# 배포 앱과 컨테이너 이미지

`api`, `batch`, `worker`, `migrator`가 각각 하나의 배포 이미지다. 컨텍스트와 플랫폼 모듈은 해당 앱의 실행 JAR에 포함한다.

| 앱 | 실행 방식 | 추가 구성 |
|---|---|---|
| api | HTTP 서버, 8080 포트, `/api` context path | PostgreSQL, Kafka, R2, 회원 설정 |
| worker | 큐를 polling하는 상주 프로세스 | PostgreSQL, R2, Cloudflare Queues, 이미지 안의 ffmpeg·ffprobe |
| batch | 잡 이름을 받아 실행 후 종료 | PostgreSQL, R2. 잡별로 CronJob/Job 생성 |
| migrator | 컨텍스트별 Flyway 실행 후 종료 | PostgreSQL과 컨텍스트별 마이그레이션 계정 |

## 빌드

이미지는 Gradle이 미리 만든 실행 JAR를 담기만 한다. 먼저 `backend/`에서 네 앱의 JAR를 한 번에 만들고, 저장소 루트에서 이미지를 굽는다. BuildKit과 실행 중인 Docker가 필요하다.

```bash
(cd backend && ./gradlew :apps:api:bootJar :apps:batch:bootJar :apps:worker:bootJar :apps:migrator:bootJar)
for app in api batch worker migrator; do
  docker build -f "backend/apps/$app/Dockerfile" -t "contentria/$app:local" backend/
done
```

앱의 `bootJar`는 버전 없이 `apps/<앱>/build/libs/application.jar`로 나온다. 빌드 컨텍스트는 **backend/**이지만 `.dockerignore`가 이 JAR만 남기고 나머지를 모두 뺀다. JAR를 만들지 않고 `docker build`를 실행하면 `COPY` 단계에서 실패한다. 이전 빌드의 JAR가 남아 있으면 그 JAR로 구워지므로, 소스를 바꾼 뒤에는 `bootJar`를 다시 실행한다.

Dockerfile의 첫 단계는 JAR에서 Spring Boot 레이어를 추출한다. 실행 이미지는 Java 25 JRE와 추출 결과만 포함한다. 추출 결과는 아키텍처와 무관하므로 첫 단계는 빌드 머신의 플랫폼에서 돈다. 다른 아키텍처로 구울 때도 에뮬레이션은 실행 이미지의 `RUN`(사용자 생성, worker의 패키지 설치)에만 걸린다.

### CI에서 게시

[`.github/workflows/backend-images.yml`](../../.github/workflows/backend-images.yml)이 main에 `backend/` 변경이 들어올 때마다 돈다. 수동 실행(workflow_dispatch)도 된다.

1. `./gradlew build`로 테스트를 돌리고 네 앱의 JAR를 만든다. 테스트가 실패하면 이미지를 굽지 않는다.
2. 앱마다 같은 JAR로 linux/amd64와 linux/arm64 이미지를 굽는다. 레이어 캐시는 GitHub Actions 캐시에 앱별로 둔다.
3. `ghcr.io/seung7642/contentria-<앱>`에 `sha-<커밋 앞 12자>`와 `latest` 태그로 올린다. 배포에는 바뀌지 않는 `sha-` 태그를 쓴다.

기반 이미지는 Eclipse Temurin의 Ubuntu Noble 계열이다. worker에만 Ubuntu 패키지의 ffmpeg와 tini를 추가한다. ffmpeg는 H.264(libx264), AAC와 HLS/fMP4 기능을 사용한다. tini는 Java와 자식 ffmpeg 프로세스에 종료 신호를 전달한다.

기본 빌드는 Docker daemon의 아키텍처를 따른다. 홈 서버나 AWS가 x86_64이고 빌드 머신이 ARM64라면 `docker build --platform linux/amd64 ...`로 대상에 맞춰 빌드한다. 이 경우 에뮬레이션 또는 해당 아키텍처의 builder가 필요하다. 두 아키텍처를 한 태그로 게시하는 작업은 별도 레지스트리 배포 단계에서 구성한다.

JDK/JRE 태그는 보안 업데이트를 받을 수 있는 가변 태그다. 새 베이스를 반영할 때 `--pull`로 재빌드하고 검증한다. 재현성을 위해 기반 이미지 digest를 고정하려면 `JDK_IMAGE`, `JRE_IMAGE` build arg에 digest를 포함한 참조를 전달한다. 빌드 및 실행 Java 메이저 버전은 프로젝트의 25와 맞춘다.

## 실행 계약

모든 이미지는 UID/GID `10001:10001`로 실행한다. Kubernetes에서 `runAsNonRoot`, `readOnlyRootFilesystem`을 사용할 수 있도록 `/tmp`에 쓰기 가능한 `emptyDir`을 마운트한다. worker는 원본과 변환 결과를 임시 저장하므로 영상 크기에 맞춰 ephemeral storage와 `/tmp` 용량도 확보한다.

환경 변수는 앱 폴더마다 있는 `.env.sample`을 따른다([backend README](../README.md)의 "설정"). `.env`, 개인키, 실제 배포 설정은 빌드에 포함하지 않는다. Kubernetes의 Secret/ConfigMap 또는 로컬 `--env-file`로 실행 시 전달한다. `.dockerignore`는 빌드 결과, 테스트 소스와 로컬 비밀 설정을 제외한다.

앱에 전달하는 인자는 Java 실행 뒤에 이어진다. 배치는 같은 이미지에 다른 잡 이름을 전달한다.

```bash
docker run --rm --read-only --tmpfs /tmp:rw,mode=1777 \
  --env-file /안전한경로/batch.env contentria/batch:local \
  --spring.batch.job.name=analyticsDailyStatistics
```

가능한 잡 이름은 `mediaImageCleanup`, `mediaVideoCleanup`, `analyticsDailyStatistics`다. migrator는 `MIGRATION_DATABASE_URL`과 각 컨텍스트의 `*_MIGRATOR_USERNAME`, `*_MIGRATOR_PASSWORD`를 받는다. API·worker를 띄우기 전에 migrator를 성공시킨다.

컨테이너 내부의 `localhost`는 컨테이너 자신이므로 DB·Kafka·저장소 주소에 다른 컨테이너의 DNS 이름이나 실제 서비스 주소를 사용한다. 현재 API에는 Kubernetes용 Actuator health endpoint가 없으므로 기존 레거시 probe를 그대로 적용하면 안 된다. probe와 GitOps 매니페스트 전환은 별도 작업이다.

## 검증 및 게시 이력

Linux ARM64(Colima)와 AMD64에서 네 이미지의 빌드와 실행을 검증했다. migrator의 6개 스키마 마이그레이션과 재실행, API의 공개·보호 경로 응답, 배치 잡 3개의 실행, worker의 영상 변환과 HLS·poster 업로드를 확인했다. 실제 클라우드 서비스 연동과 Kubernetes 배포는 아직 확인하지 않았다.

기존에 게시한 AMD64 이미지 버전은 `ghcr.io/seung7642/contentria-<app>:sha-c064d0568f16`이다. [게시 당시 실행 결과](https://github.com/seung7642/contentria/actions/runs/39951898832)에서 검증 내역을 확인할 수 있다. ARM64 이미지는 로컬 실행만 검증했다.

이후 게시는 위의 CI가 맡는다. CI는 이미지를 실제 DB나 큐에 붙여 띄워 보는 실행 검증을 하지 않는다. GitHub에서 게시한 이미지는 사용자 프로필의 [Packages 탭](https://github.com/seung7642?tab=packages)에서 확인한다.
