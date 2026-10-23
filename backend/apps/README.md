# 배포 앱과 컨테이너 이미지

`api`, `batch`, `worker`, `migrator`가 각각 하나의 배포 이미지다. 컨텍스트와 플랫폼 모듈은 해당 앱의 실행 JAR에 포함한다.

| 앱 | 실행 방식 | 추가 구성 |
|---|---|---|
| api | HTTP 서버, 8080 포트, `/api` context path | PostgreSQL, Kafka, R2, 회원 설정 |
| worker | 큐를 polling하는 상주 프로세스 | PostgreSQL, R2, Cloudflare Queues, 이미지 안의 ffmpeg·ffprobe |
| batch | 잡 이름을 받아 실행 후 종료 | PostgreSQL, R2. 잡별로 CronJob/Job 생성 |
| migrator | 컨텍스트별 Flyway 실행 후 종료 | PostgreSQL과 컨텍스트별 마이그레이션 계정 |

## 빌드

저장소 루트에서 실행한다. 멀티모듈 의존성을 포함해야 하므로 빌드 컨텍스트는 앱 폴더가 아닌 **backend/**다. BuildKit과 실행 중인 Docker가 필요하다. 호스트에 JDK나 Gradle을 설치할 필요는 없다.

```bash
for app in api batch worker migrator; do
  docker build -f "backend/apps/$app/Dockerfile" -t "contentria/$app:local" backend/
done
```

빌드 단계는 Java 25 JDK로 해당 앱의 `bootJar`를 만들고 Spring Boot 레이어를 추출한다. 실행 이미지는 Java 25 JRE와 추출 결과만 포함한다. Gradle 의존성과 빌드 캐시는 BuildKit cache mount로 공유한다. 이미지 빌드는 테스트를 실행하지 않으므로 테스트와 아래 실행 검증을 별도로 수행한다.

기반 이미지는 Eclipse Temurin의 Ubuntu Noble 계열이다. worker에만 Ubuntu 패키지의 ffmpeg와 tini를 추가한다. ffmpeg는 H.264(libx264), AAC와 HLS/fMP4 기능을 사용한다. tini는 Java와 자식 ffmpeg 프로세스에 종료 신호를 전달한다.

기본 빌드는 Docker daemon의 아키텍처를 따른다. 홈 서버나 AWS가 x86_64이고 빌드 머신이 ARM64라면 `docker build --platform linux/amd64 ...`로 대상에 맞춰 빌드한다. 이 경우 에뮬레이션 또는 해당 아키텍처의 builder가 필요하다. 두 아키텍처를 한 태그로 게시하는 작업은 별도 레지스트리 배포 단계에서 구성한다.

JDK/JRE 태그는 보안 업데이트를 받을 수 있는 가변 태그다. 새 베이스를 반영할 때 `--pull`로 재빌드하고 검증한다. 재현성을 위해 기반 이미지 digest를 고정하려면 `JDK_IMAGE`, `JRE_IMAGE` build arg에 digest를 포함한 참조를 전달한다. 빌드 및 실행 Java 메이저 버전은 프로젝트의 25와 맞춘다.

## 실행 계약

모든 이미지는 UID/GID `10001:10001`로 실행한다. Kubernetes에서 `runAsNonRoot`, `readOnlyRootFilesystem`을 사용할 수 있도록 `/tmp`에 쓰기 가능한 `emptyDir`을 마운트한다. worker는 원본과 변환 결과를 임시 저장하므로 영상 크기에 맞춰 ephemeral storage와 `/tmp` 용량도 확보한다.

환경 변수는 [backend README](../README.md)의 프로세스별 목록을 따른다. `.env`, 개인키, 실제 배포 설정은 빌드에 포함하지 않는다. Kubernetes의 Secret/ConfigMap 또는 로컬 `--env-file`로 실행 시 전달한다. `.dockerignore`는 빌드 결과, 테스트 소스와 로컬 비밀 설정을 제외한다.

앱에 전달하는 인자는 Java 실행 뒤에 이어진다. 배치는 같은 이미지에 다른 잡 이름을 전달한다.

```bash
docker run --rm --read-only --tmpfs /tmp:rw,mode=1777 \
  --env-file /안전한경로/batch.env contentria/batch:local \
  --spring.batch.job.name=analyticsDailyStatistics
```

가능한 잡 이름은 `mediaImageCleanup`, `mediaVideoCleanup`, `analyticsDailyStatistics`다. migrator는 `MIGRATION_DATABASE_URL`과 각 컨텍스트의 `*_MIGRATOR_USERNAME`, `*_MIGRATOR_PASSWORD`를 받는다. API·worker를 띄우기 전에 migrator를 성공시킨다.

컨테이너 내부의 `localhost`는 컨테이너 자신이므로 DB·Kafka·저장소 주소에 다른 컨테이너의 DNS 이름이나 실제 서비스 주소를 사용한다. 현재 API에는 Kubernetes용 Actuator health endpoint가 없으므로 기존 레거시 probe를 그대로 적용하면 안 된다. probe와 GitOps 매니페스트 전환은 별도 작업이다.

## 로컬 실행 검증

Python 3.9 이상과 Docker로 실행한다. 추가 Python 패키지는 필요 없다. 아래 명령은 이미지 빌드와 실행을 모두 수행하며 레지스트리에 push하지 않는다.

```bash
python3 backend/scripts/image-smoke/run.py --build
```

이미 `contentria/<app>:smoke`를 빌드했다면 `--build`를 빼고 실행한다. 다른 태그는 `--tag local`처럼 지정한다.

검증용 Docker network에 PostgreSQL, Kafka, MinIO, 가짜 Cloudflare Queue를 띄운다. 실제 클라우드 자격 증명은 사용하지 않고 매번 임시 값을 생성한다. 저장소의 DB 초기화 SQL을 그대로 적용해 운영과 같은 역할별 권한으로 앱을 실행한다.

- migrator: 6개 스키마 마이그레이션과 재실행 성공
- api: 공개 글 목록 HTTP 200과 보호 경로 HTTP 401, 종료 신호 처리
- batch: 잡 3개의 성공 종료와 잘못된 잡 이름의 실패 종료
- worker: 자체 ffmpeg로 H.264/AAC 샘플 생성, 큐 이벤트 소비, 실제 변환, HLS·poster 업로드, 원본 삭제와 ack, 종료 신호 처리
- 공통: non-root 이미지, 읽기 전용 루트 파일시스템과 임시 `/tmp`에서 실행

Linux ARM64(Colima)에서 네 이미지의 빌드와 위 실행 검증을 통과했다. AMD64 실행과 실제 클라우드 연동은 아직 확인하지 않았다.

성공·실패 시 이 실행이 생성한 컨테이너, 익명 볼륨과 네트워크를 정리한다. 강제 종료나 Docker 장애로 정리가 중단되면 `contentria.image-smoke` label로 해당 실행의 리소스를 확인한다. 빌드한 이미지와 BuildKit 캐시는 이후 재사용을 위해 남긴다. 실제 R2·Cloudflare API, SMTP·Google 로그인, Kubernetes 배포와 장애 복구를 검증하는 테스트는 아니다.
