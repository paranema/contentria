# 컨테이너 이미지: 빌드, 게시, 실행

`api`, `batch`, `worker`, `migrator`는 각각 이미지 하나로 배포한다. 컨텍스트와 플랫폼 모듈은 그 앱의 실행 JAR 안에 들어간다.

| 앱 | 실행 방식 | 붙는 곳 |
|---|---|---|
| api | HTTP 서버. 8080 포트, context path `/api` | PostgreSQL, Kafka, R2 |
| worker | 큐를 가져와(pull) 처리하는 상주 프로세스 | PostgreSQL, R2, Cloudflare Queues. 이미지 안의 ffmpeg·ffprobe |
| batch | 잡 이름을 받아 실행하고 끝난다. 잡마다 CronJob이나 Job을 만든다 | PostgreSQL, R2 |
| migrator | 컨텍스트마다 Flyway를 실행하고 끝난다 | PostgreSQL. 컨텍스트별 마이그레이션 롤 |

## 이미지 구성

이미지는 Gradle이 미리 만든 실행 JAR를 담기만 한다. 이미지 안에서 Gradle을 돌리지 않는다.

| 항목 | 내용 |
|---|---|
| Dockerfile | `apps/<앱>/Dockerfile` |
| 빌드 컨텍스트 | `backend/`. `.dockerignore`가 `apps/*/build/libs/application.jar`만 남기고 모두 뺀다 |
| 기반 이미지 | `eclipse-temurin:25-jre-noble` (build arg `JRE_IMAGE`) |
| 첫 단계 | JAR에서 Spring Boot 레이어를 추출한다. 결과가 아키텍처와 무관하므로 빌드 머신의 플랫폼에서 돈다 |
| 실행 단계 | JRE와 추출한 레이어만 담는다. UID/GID `10001:10001`로 실행한다 |
| worker만 다른 점 | Ubuntu 패키지의 ffmpeg(libx264, AAC, HLS/fMP4)와 tini를 더한다. tini는 Java와 자식 ffmpeg 프로세스에 종료 신호를 전달한다 |

다른 아키텍처로 구울 때 에뮬레이션은 실행 단계의 `RUN`(사용자 생성, worker의 패키지 설치)에만 걸린다.

`25-jre-noble`은 보안 업데이트가 반영되는 가변 태그다. 새 기반 이미지를 받으려면 `--pull`로 다시 굽는다. 재현성이 필요하면 `JRE_IMAGE`에 digest를 포함한 참조를 넘긴다.

## 로컬에서 빌드

BuildKit과 실행 중인 Docker가 필요하다. `backend/`에서 네 앱의 JAR를 만든 뒤, 저장소 루트에서 이미지를 굽는다.

```bash
(cd backend && ./gradlew :apps:api:bootJar :apps:batch:bootJar :apps:worker:bootJar :apps:migrator:bootJar)
for app in api batch worker migrator; do
  docker build -f "backend/apps/$app/Dockerfile" -t "contentria/$app:local" backend/
done
```

- JAR는 버전 없이 `apps/<앱>/build/libs/application.jar`로 나온다.
- JAR 없이 `docker build`를 실행하면 `COPY` 단계에서 실패한다.
- 이전 빌드의 JAR가 남아 있으면 그 JAR로 구워진다. 소스를 바꿨다면 `bootJar`부터 다시 실행한다.
- 기본으로는 Docker daemon의 아키텍처로 굽는다. 다른 아키텍처가 필요하면 `--platform linux/amd64`처럼 지정한다.

## CI에서 게시

[`.github/workflows/backend-images.yml`](../../../.github/workflows/backend-images.yml)이 main에 `backend/` 변경이 들어올 때 돈다. 수동 실행(workflow_dispatch)도 된다.

| 단계 | 하는 일 | 도는 곳 |
|---|---|---|
| 1 | `./gradlew build --continue`로 테스트를 돌리고 네 앱의 JAR를 만든다. 한 모듈이 실패해도 나머지 테스트는 마저 돈다 | 모든 브랜치 |
| 2 | 앱마다 같은 JAR로 linux/amd64와 linux/arm64 이미지를 구워 한 태그로 올린다. 레이어 캐시는 GitHub Actions 캐시에 앱별로 둔다 | main만 |

- 1단계가 실패하면 이미지를 굽지 않는다.
- 다른 브랜치에서 수동으로 돌리면 1단계까지만 돈다. 머지 전에 Linux에서 테스트를 확인할 때 쓴다.
- 이미지 이름은 `ghcr.io/seung7642/contentria-<앱>`이다. 태그는 `sha-<커밋 앞 12자>`와 `latest` 두 개를 붙인다.
- 배포에는 바뀌지 않는 `sha-` 태그를 쓴다.
- 게시한 이미지는 GitHub 프로필의 [Packages 탭](https://github.com/seung7642?tab=packages)에서 본다.

## 실행 계약

### 파일 시스템

이미지는 비루트(`10001:10001`)로 돈다. Kubernetes에서 `runAsNonRoot`, `readOnlyRootFilesystem`을 켜고, `/tmp`에 쓰기 가능한 `emptyDir`을 마운트한다. worker는 원본과 변환 결과를 `/tmp`에 임시로 두므로 영상 크기에 맞춰 ephemeral storage를 잡는다.

### 설정

환경 변수는 앱 폴더의 `.env.sample`을 따른다([backend README](../../README.md)의 "설정"). `.env`, 개인키, 실제 배포 설정은 이미지에 넣지 않는다. 실행할 때 Kubernetes의 Secret·ConfigMap이나 로컬 `--env-file`로 넘긴다.

컨테이너 안의 `localhost`는 그 컨테이너 자신이다. DB, Kafka, 저장소 주소에는 다른 컨테이너의 DNS 이름이나 실제 서비스 주소를 쓴다.

### 인자와 순서

`java -jar application.jar` 뒤에 인자가 이어진다. batch는 같은 이미지에 잡 이름만 바꿔 넘긴다.

```bash
docker run --rm --read-only --tmpfs /tmp:rw,mode=1777 \
  --env-file /안전한경로/batch.env contentria/batch:local \
  --spring.batch.job.name=analyticsDailyStatistics
```

| batch 잡 이름 | 정의 |
|---|---|
| `mediaImageCleanup` | `contexts/media/media-core/.../ImageCleanupJob.kt` |
| `mediaVideoCleanup` | `contexts/media/media-core/.../VideoCleanupJob.kt` |
| `analyticsDailyStatistics` | `contexts/analytics/analytics-core/.../DailyStatisticsJob.kt` |

배포마다 migrator를 먼저 성공시킨 뒤 api, worker를 띄운다. migrator가 실패하면 0이 아닌 코드로 끝난다.

### 헬스 체크

api에는 Actuator가 없어서 Kubernetes용 health endpoint가 없다. 레거시의 probe 설정을 그대로 가져다 쓰면 안 된다.

## 확인한 것과 남은 것

CI가 생기기 전에 Linux ARM64(Colima)와 AMD64에서 네 이미지를 직접 굽고 띄워 봤다. 확인한 범위는 다음과 같다.
- migrator: 6개 스키마 마이그레이션과 재실행
- api: 공개 경로와 보호 경로의 응답
- batch: 잡 3개 실행
- worker: 영상 변환, HLS와 poster 업로드

CI는 이미지를 굽고 올리기만 한다. 이미지를 실제 DB나 큐에 붙여 띄워 보지는 않는다.

| 남은 것 | 상태 |
|---|---|
| 실제 클라우드 서비스(R2, Cloudflare Queues)와 이미지 연동 | 확인하지 않았다 |
| Kubernetes 배포와 GitOps 매니페스트 전환 | 별도 작업. 현재 클러스터는 레거시 이미지로 돈다 |
| api의 health endpoint와 probe | 없다. 위 "헬스 체크" 참고 |
