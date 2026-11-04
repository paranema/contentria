# 컨테이너 이미지 운영

백엔드 컨테이너 이미지의 구성과 빌드·게시 절차, 실행 조건을 설명합니다.

## 개요

### 배포 대상

`api`, `worker`, `batch`, `migrator`는 각각 별도 이미지로 배포합니다. 각 앱이 사용하는 컨텍스트와 플랫폼 모듈은 실행 JAR에 포함됩니다.

| 앱         | 역할과 실행 방식                                                 | 주요 외부 의존성                       |
| ---------- | ---------------------------------------------------------------- | -------------------------------------- |
| `api`      | HTTP 요청을 처리하는 상주 서버. 포트 `8080`, context path `/api` | PostgreSQL, Kafka, R2                  |
| `worker`   | 영상 변환 메시지를 조회하고 처리하는 상주 프로세스               | PostgreSQL, R2, Cloudflare Queues      |
| `batch`    | 지정한 잡을 실행한 뒤 종료. 잡별로 Job 또는 CronJob 구성         | PostgreSQL, R2                         |
| `migrator` | 컨텍스트별 Flyway 마이그레이션을 실행한 뒤 종료                  | PostgreSQL, 컨텍스트별 마이그레이션 롤 |

### 이미지 구성

Dockerfile은 `backend/apps/<앱>/Dockerfile`에 있으며, 빌드 컨텍스트는 `backend/`입니다. **이미지 안에서는 Gradle을 실행하지 않으므로 실행 JAR를 먼저 만들어야 합니다.**

| 항목           | 구성                                                                                              |
| -------------- | ------------------------------------------------------------------------------------------------- |
| 입력 파일      | `apps/<앱>/build/libs/application.jar`. `.dockerignore`에서 이 파일만 빌드 컨텍스트에 포함합니다. |
| 기반 이미지    | `eclipse-temurin:25-jre-noble`. `JRE_IMAGE` 빌드 인자로 변경할 수 있습니다.                       |
| 빌드 단계      | 빌드 머신의 플랫폼에서 Spring Boot JAR의 레이어를 추출합니다.                                     |
| 실행 단계      | JRE와 추출한 레이어를 포함하고 UID/GID `10001:10001`로 실행합니다.                                |
| 워커 추가 구성 | ffmpeg와 ffprobe, 종료 신호를 Java와 자식 프로세스에 전달하는 tini를 포함합니다.                  |

## 이미지 준비

### 로컬 빌드

JDK 25, BuildKit을 지원하는 Docker 및 실행 중인 Docker daemon이 필요합니다. 아래 명령은 모두 **저장소 루트**에서 실행합니다.

먼저 네 앱의 실행 JAR를 생성합니다.

```bash
(cd backend && ./gradlew :apps:api:bootJar :apps:batch:bootJar :apps:worker:bootJar :apps:migrator:bootJar)
```

JAR 생성이 성공하면 앱별 이미지를 빌드합니다.

```bash
for app in api batch worker migrator; do
  docker build -f "backend/apps/$app/Dockerfile" -t "contentria/$app:local" backend/
done
```

JAR가 없으면 Dockerfile의 `COPY` 단계에서 실패합니다. 이전 JAR가 남아 있으면 해당 파일로 이미지가 만들어지므로, 소스를 변경한 뒤에는 JAR 생성부터 다시 실행합니다.

기본적으로 Docker daemon의 아키텍처에 맞춰 빌드합니다. 다른 아키텍처는 `--platform linux/amd64`와 같이 지정하며, 이 경우 실행 이미지의 `RUN` 명령을 처리할 에뮬레이션 환경이 필요합니다.

기반 이미지 태그는 갱신될 수 있습니다. 최신 기반 이미지를 반영하려면 `--pull`을 사용하고, 특정 기반 이미지를 고정하려면 `--build-arg JRE_IMAGE=<이미지>@sha256:<digest>`로 지정합니다.

### CI 게시

[Backend images 워크플로](../../../.github/workflows/backend-images.yml)는 `main`에 `backend/` 또는 워크플로 파일의 변경이 반영되면 실행됩니다. `workflow_dispatch`로 브랜치를 선택해 수동 실행할 수도 있습니다.

| 순서 | 처리 내용                                                                           | 실행 조건                         |
| ---- | ----------------------------------------------------------------------------------- | --------------------------------- |
| 1    | `./gradlew build --continue`로 테스트를 실행하고 네 앱의 JAR를 생성합니다.          | 자동 실행 또는 수동 실행한 브랜치 |
| 2    | 생성한 JAR로 앱별 `linux/amd64`, `linux/arm64` 이미지를 빌드하고 GHCR에 게시합니다. | 1단계가 성공한 `main` 브랜치      |

다른 브랜치에서 수동 실행하면 테스트와 JAR 생성까지만 진행합니다. `--continue`는 한 모듈에서 실패해도 나머지 테스트를 계속 실행하기 위한 옵션이며, 빌드가 실패하면 이미지 게시 단계는 실행되지 않습니다.

### 이미지 태그

게시 경로는 `ghcr.io/<저장소 소유자 소문자>/contentria-<앱>`입니다. 예를 들어 API 이미지는 `ghcr.io/seung7642/contentria-api`로 게시됩니다.

| 태그                 | 용도                                                                            |
| -------------------- | ------------------------------------------------------------------------------- |
| `sha-<커밋 앞 12자>` | 배포할 소스 커밋을 지정할 때 사용합니다.                                        |
| `latest`             | 가장 최근에 게시된 이미지를 확인할 때 사용합니다. 이후 게시 시 대상이 바뀝니다. |

배포 설정에는 `sha-` 태그를 사용해 대상 커밋을 명시합니다. 게시된 이미지 경로와 태그는 워크플로 실행 결과의 요약에서 확인합니다.

## 실행

### 환경 설정

각 앱의 `.env.sample`을 기준으로 설정을 준비합니다. 설정 항목의 공통 안내는 [백엔드 README](../../README.md#설정)를 참고합니다.

`.env`, 개인키, 배포 환경의 설정값은 이미지에 포함하지 않습니다. 로컬 실행에서는 `--env-file`, Kubernetes에서는 Secret과 ConfigMap으로 주입합니다. `--env-file`에 전달하는 값은 따옴표 없이 작성합니다.

컨테이너 내부의 `localhost`는 해당 컨테이너를 가리킵니다. PostgreSQL, Kafka, R2 등 외부 서비스에는 컨테이너에서 접근할 수 있는 DNS 이름이나 실제 서비스 주소를 지정합니다. 워커의 R2·큐 연결은 [영상 변환 파이프라인 설정](cloudflare-video-pipeline.md)을 따릅니다.

### 실행 권한

모든 이미지는 비루트 사용자 `10001:10001`로 실행됩니다. Kubernetes에서도 `runAsNonRoot`를 활성화해 비루트 실행을 보장합니다.

### 파일 시스템

Kubernetes에서는 `readOnlyRootFilesystem`을 활성화하고, `/tmp`에 쓰기 가능한 `emptyDir`을 마운트합니다.

워커는 원본 영상과 변환 결과를 임시 디렉터리에 저장합니다. Kubernetes의 ephemeral storage 용량은 원본과 변환 결과를 함께 저장할 수 있도록 지정합니다.

### 앱 실행 순서

배포할 이미지와 환경 변수를 준비한 뒤 다음 순서로 실행합니다.

1. `migrator`를 실행해 컨텍스트별 스키마 마이그레이션을 적용합니다.
2. migrator가 종료 코드 `0`으로 완료되었는지 확인합니다. 실패하면 이후 앱 실행을 중단하고 원인을 확인합니다.
3. `api`와 `worker`를 실행합니다.
4. 필요한 배치 잡을 Job 또는 CronJob으로 실행합니다.

### 배치 잡 실행

`batch` 이미지는 실행 인자로 잡 이름을 지정합니다. Docker 이미지 이름 뒤에 전달한 인자는 `java -jar application.jar`의 인자로 이어집니다.

아래 예시는 저장소 루트에서 실행하며, `backend/apps/batch/.env`의 연결 정보는 컨테이너에서 접근 가능한 주소로 준비되어 있어야 합니다.

```bash
docker run --rm --read-only --tmpfs /tmp:rw,mode=1777 \
  --env-file backend/apps/batch/.env contentria/batch:local \
  --spring.batch.job.name=analyticsDailyStatistics
```

| 잡 이름                    | 수행 작업           |
| -------------------------- | ------------------- |
| `mediaImageCleanup`        | 미디어 이미지 정리  |
| `mediaVideoCleanup`        | 미디어 영상 정리    |
| `analyticsDailyStatistics` | 일별 방문 통계 집계 |

## 검증

### 앱별 동작 확인

CI는 테스트와 이미지 빌드·게시까지 수행합니다. 완성된 이미지가 배포 환경에서 동작하는지는 다음 항목을 직접 확인합니다.

| 대상       | 확인할 내용                                                                                                      |
| ---------- | ---------------------------------------------------------------------------------------------------------------- |
| `migrator` | 마이그레이션 성공, 종료 코드 `0`, 재실행 시 정상 종료                                                            |
| `api`      | 공개 API 응답과 인증이 필요한 API의 접근 제어                                                                    |
| `batch`    | 지정한 잡의 완료 여부와 데이터 처리 결과                                                                         |
| `worker`   | 큐 메시지 처리, HLS·poster 저장, 영상 재생 ([파이프라인 동작 검증](cloudflare-video-pipeline.md#동작-검증) 참고) |

### 헬스 체크

API 앱에는 Actuator 기반 헬스 체크 엔드포인트가 없습니다. Kubernetes probe를 구성하려면 먼저 헬스 체크를 구현하고 사용할 경로를 정해야 합니다.
