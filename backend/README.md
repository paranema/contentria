# Contentria Backend

바운디드 컨텍스트를 최상위 축으로 둔 모듈러 모놀리스다. 컨텍스트 하나를 떼어 서비스로 만들 수 있게 구성한다.
설계 문서의 색인과 읽는 순서는 [docs/README.md](docs/README.md)에 있다.

레거시 모듈(`blog-*`)은 이 구조와 호환하지 않으며 저장소에서 지웠다. 실행 중인 레거시 컨테이너는 그대로 두고, 소스는 `legacy-final` 태그에 남겼다.
레거시를 고쳐야 하면 그 태그에서 브랜치를 딴다. 빌드와 배포 방법은 그 태그의 이 문서에 있다.

## 컨텍스트

| 컨텍스트 | 내부 구조 | 다루는 것 |
|---|---|---|
| [member](docs/contexts/member/README.md) (회원) | 헥사고날 | 가입, 로그인(이메일 코드, 비밀번호, Google), 세션, 프로필 |
| [publishing](docs/contexts/publishing/README.md) (출판) | 헥사고날 | 블로그, 카테고리, 글의 작성과 발행, 공개 조회 |
| [interaction](docs/contexts/interaction/README.md) (상호작용) | 레이어드 | 구독, 좋아요, 댓글 |
| [notification](docs/contexts/notification/README.md) (알림) | 레이어드 | 새 글과 댓글 알림, 알림함 |
| [media](docs/contexts/media/README.md) (미디어) | 헥사고날 | 이미지와 영상의 업로드, 검증, 변환, 글과의 연결, 정리 |
| [analytics](docs/contexts/analytics/README.md) (분석) | 레이어드 | 방문 기록, 일별 집계, 조회수 |

경계를 나눈 이유는 [docs/strategic/03-bounded-contexts.md](docs/strategic/03-bounded-contexts.md)에, 내부 구조를 고른 이유는 [docs/architecture/02-internal-architecture.md](docs/architecture/02-internal-architecture.md)에 있다.

## 디렉터리 구조

```
backend/
├── build-logic/                 Gradle 컨벤션 플러그인
├── gradle/                      의존성 버전 카탈로그(libs.versions.toml), Gradle wrapper
├── shared-kernel/               모든 컨텍스트가 쓰는 공통 기반 (식별자, 예외, 영속성 기반)
├── platform/
│   ├── messaging/               아웃박스, 릴레이, 인박스 (Kafka)
│   └── architecture-rules/      아키텍처 규칙 (테스트 전용)
├── contexts/
│   └── <컨텍스트>/
│       ├── <컨텍스트>-api/      다른 컨텍스트에 공개하는 계약
│       └── <컨텍스트>-core/     그 컨텍스트의 전부 (도메인, 유스케이스, 어댑터, 마이그레이션)
├── apps/                        컨텍스트를 묶어 프로세스로 띄우는 조립 모듈
│   ├── api/                     HTTP API 서버
│   ├── batch/                   배치 잡
│   ├── worker/                  영상 변환 worker
│   └── migrator/                컨텍스트별 Flyway 마이그레이션 실행
└── docs/                        설계 문서
```

각 모듈의 현재 상태는 [docs/architecture/01-module-structure.md](docs/architecture/01-module-structure.md)에 있다.

## 모듈 의존 관계

| 모듈 | 종류 | 의존 모듈 |
|---|---|---|
| `shared-kernel` | 라이브러리 | 없음 |
| `platform:messaging` | 라이브러리 | `shared-kernel` |
| `platform:architecture-rules` | 테스트 전용 라이브러리 | 없음 (ArchUnit) |
| `contexts:*:*-api` | 라이브러리 | `shared-kernel` |
| `contexts:member:member-core` | 라이브러리 | `member-api` |
| `contexts:publishing:publishing-core` | 라이브러리 | `publishing-api`, `member-api`, `media-api`, `messaging` |
| `contexts:interaction:interaction-core` | 라이브러리 | `interaction-api`, `member-api`, `publishing-api`, `messaging` |
| `contexts:notification:notification-core` | 라이브러리 | `notification-api`, `interaction-api`, `publishing-api`, `messaging` |
| `contexts:media:media-core` | 라이브러리 | `media-api` |
| `contexts:analytics:analytics-core` | 라이브러리 | `analytics-api`, `publishing-api` |
| `apps:api` | 실행 (API 서버) | 6개 컨텍스트의 `-core`, `messaging` |
| `apps:batch` | 실행 (배치) | `media-core`, `analytics-core` |
| `apps:worker` | 실행 (상주 worker) | `media-core` |
| `apps:migrator` | 실행 (마이그레이션 Job) | 6개 컨텍스트의 `-core` (마이그레이션 파일만, runtimeOnly) |

`-core`는 모두 `shared-kernel`에도 의존한다.
`-core` 모듈끼리는 서로 의존하지 않는다. 다른 컨텍스트에는 `-api`로만 의존한다. ArchUnit 테스트가 모듈마다 이 규칙을 확인한다.

## 컨텍스트 사이의 통신

| 방식 | 쓰는 곳 | 규칙 |
|---|---|---|
| 이벤트 | 글 발행과 삭제, 댓글 | 보내는 쪽이 같은 트랜잭션에서 아웃박스에 쓰고, 릴레이가 Kafka로 보내고, 받는 쪽이 인박스로 중복을 거른다 |
| 조회와 요청 | 글이 공개인지, 회원 정보, 글의 이미지와 영상 연결 | 상대의 `-api` 포트를 부른다. 같은 JVM에 있어도 원격 호출로 여긴다. 부르는 곳은 컨텍스트마다 번역 계층 한 곳이다 |
| 조합 | 블로그 대시보드, 글의 영상 | 화면 하나가 여러 컨텍스트를 모아야 하면 `apps/api`의 `composition`이 모은다 |

자세한 규칙은 [docs/architecture/03-context-integration.md](docs/architecture/03-context-integration.md)에 있다.

## 프로세스

| 프로세스 | 하는 일 | DB 롤 | 외부 의존 |
|---|---|---|---|
| `apps/api` | HTTP API, 아웃박스 릴레이, 이벤트 소비 | `api_app` | PostgreSQL, Kafka, R2, SMTP, Google OAuth |
| `apps/batch` | 한 번 돌고 끝나는 잡 (`mediaImageCleanup`, `mediaVideoCleanup`, `analyticsDailyStatistics`) | `batch_app` | PostgreSQL, R2 |
| `apps/worker` | Cloudflare Queue에서 업로드 알림을 받아 영상을 HLS로 변환 | `worker_app` | PostgreSQL, R2, Cloudflare Queues, ffmpeg와 ffprobe |
| `apps/migrator` | 컨텍스트마다 그 컨텍스트의 마이그레이션 롤로 Flyway 실행 | `<컨텍스트>_migrator` | PostgreSQL |

배치는 잡 이름을 넘겨야 뜬다. 잡이 여럿이라 이름 없이는 어느 잡을 돌릴지 정하지 못한다.

```bash
./gradlew :apps:batch:bootRun --args='--spring.batch.job.name=mediaImageCleanup'
```

### 로컬에서 api 띄우기

`apps/api`는 `spring-boot-docker-compose`로 의존을 직접 띄운다. `bootRun`을 실행하면 [apps/api/compose.yaml](apps/api/compose.yaml)로 `docker compose up --wait`를 하고, 앱이 끝나면 컨테이너를 멈춘다. 이 의존성은 `developmentOnly`라서 실행 JAR와 테스트에는 들어가지 않는다. Docker가 필요하다.

| 서비스 | 호스트 포트 | 하는 일 |
|---|---|---|
| `postgres` | 15432 | 처음 뜰 때 `infra/database/contentria-v2`로 운영과 같은 롤과 스키마를 만든다. 비밀번호 없이(trust) 받는다. 롤 권한은 운영과 같다 |
| `migrate` | | 띄울 때마다 작업 중인 소스의 마이그레이션을 컨텍스트별 롤로 적용한다. 끝나야 앱이 뜬다 |
| `kafka` | 19092 | 단일 노드 |
| `kafka-ui` | 18080 | 토픽, 메시지, 컨슈머 그룹을 브라우저로 본다. 로그인 없이 연다 |

호스트 포트는 기본 포트에 10000을 더했다. 다른 로컬 스택의 PostgreSQL, Kafka와 겹치지 않게 하려는 것이다. 포트는 127.0.0.1에만 열어 이 머신 밖에서는 붙을 수 없다.
Boot는 컨테이너를 띄우고 멈추기만 한다. 앱은 평소처럼 환경 변수로 접속 정보를 받고, [apps/api/.env.sample](apps/api/.env.sample)에 이 포트와 계정을 적어 두었다. PostgreSQL은 Boot의 자동 연결에서 뺐다. 자동 연결은 슈퍼유저로 붙기 때문이다. Kafka는 Boot에 compose용 자동 연결이 없다.

미디어 저장소는 compose로 띄우지 않고 실제 R2를 쓴다. 버킷은 환경마다 따로 둔다. 로컬은 개발용 버킷(예: `contentria-media-dev`)을 쓰고, 운영 버킷에는 손대지 않는다.

| 개발용 버킷에 필요한 것 | 이유 |
|---|---|
| 그 버킷에만 Object Read & Write 권한이 있는 R2 API 토큰 | 로컬 `.env`의 키가 새도 운영 버킷에는 닿지 않는다 |
| 공개 접근 (r2.dev 주소 또는 연결한 도메인) | `MEDIA_PUBLIC_BASE_URL`로 이미지와 영상을 내준다 |
| CORS: 출처 `http://localhost:3000`, 메서드 `PUT`, 헤더 `Content-Type` | 브라우저가 미리 서명된 주소로 직접 올린다 |
| 영상을 다룬다면 개발용 큐와 이벤트 알림 | 버킷의 이벤트는 그 버킷에 연결한 큐로만 간다. 운영 큐에 연결하면 로컬 업로드를 운영 worker가 받는다([operations/cloudflare-video-pipeline.md](docs/operations/cloudflare-video-pipeline.md)) |

통합 테스트는 지금처럼 Testcontainers의 MinIO를 쓴다. 네트워크와 Cloudflare 계정 없이 돌아야 하기 때문이다.

```bash
cp apps/api/.env.sample apps/api/.env   # 비어 있는 비밀 값을 채운다
set -a; . apps/api/.env; set +a
./gradlew :apps:api:bootRun
```

스택이 이미 떠 있으면 Boot는 띄우지 않고, 자기가 띄우지 않은 스택은 앱이 끝나도 멈추지 않는다. 직접 `docker compose up`으로 띄워 둔 경우가 그렇다.
데이터는 컨테이너를 멈춰도 볼륨에 남는다. 처음부터 다시 만들려면 `docker compose -f apps/api/compose.yaml down -v`를 실행한다.

### 설정

프로세스마다 읽는 환경 변수는 그 app 폴더의 `.env.sample`에 있다. 필수인 값, 비밀인 값, 기본값과 로컬용 값을 함께 적었다.

| 프로세스 | 샘플 |
|---|---|
| `apps/api` | [apps/api/.env.sample](apps/api/.env.sample) |
| `apps/batch` | [apps/batch/.env.sample](apps/batch/.env.sample) |
| `apps/worker` | [apps/worker/.env.sample](apps/worker/.env.sample) |
| `apps/migrator` | [apps/migrator/.env.sample](apps/migrator/.env.sample) |

로컬에서는 샘플을 같은 폴더의 `.env`로 복사해 채운다. `.env`는 git이 무시하고 이미지에도 들어가지 않는다. IntelliJ는 EnvFile 플러그인으로, Docker는 `--env-file`로 넘긴다.
운영에서는 `[비밀]` 표시가 있는 값을 Kubernetes Secret에, 나머지를 ConfigMap에 둔다. 비밀 값은 저장소에 두지 않는다.

각 app의 `EnvSampleTest`가 샘플의 목록이 `application.yml`(migrator는 `main`)이 읽는 환경 변수와 같은지 확인한다. 설정에 환경 변수를 더하면 샘플에도 더해야 빌드가 통과한다.

## 빌드와 테스트

JDK 버전은 `mise.toml`을 따른다.

```bash
./gradlew build
```

통합 테스트는 Testcontainers로 실제 의존을 띄우므로 Docker가 필요하다.

| 이미지 | 쓰는 테스트 |
|---|---|
| `postgres:17` | 마이그레이션, 저장소, app 통합 테스트 |
| `apache/kafka:4.1.0` | `apps/api`의 이벤트 흐름 |
| `cgr.dev/chainguard/minio:latest` | 저장소 어댑터, 업로드, 정리 잡, 변환 (S3 API) |
| `jrottenberg/ffmpeg:7.1-ubuntu2404` | `apps/worker`의 실제 변환 |

MinIO는 Docker Hub에 이미지를 더 올리지 않아 Chainguard의 빌드를 쓴다. 무료로는 `latest`만 받을 수 있다.
colima를 쓴다면 Testcontainers가 소켓을 찾도록 환경 변수를 준다.

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 데이터베이스

새 구조는 `contentria_v2` 데이터베이스를 쓴다. 레거시의 `contentria` 데이터베이스와 따로 둔다.
컨텍스트마다 스키마 하나를 두고, 그 컨텍스트의 마이그레이션 롤이 소유한다. 런타임 롤은 DML만 가진다. 컨텍스트를 넘는 외래키는 없다.

| 순서 | 할 일 | 위치 |
|---|---|---|
| 1 | 데이터베이스와 롤 만들기 (슈퍼유저, 한 번) | `infra/database/contentria-v2/01-database-and-roles.sql` |
| 2 | 스키마와 권한 (슈퍼유저, 한 번) | `infra/database/contentria-v2/02-schemas-and-grants.sql` |
| 3 | 마이그레이션 (배포마다) | `apps/migrator` |

스키마 규칙은 [docs/architecture/04-persistence-conventions.md](docs/architecture/04-persistence-conventions.md)에 있다.

## 현재 상태

| 항목 | 상태 |
|---|---|
| 6개 컨텍스트와 4개 app | 코드와 테스트가 있다 |
| 컨테이너 이미지와 배포 | 앱별 Dockerfile과 [빌드·실행 안내](docs/operations/container-images.md)가 있다. main에 들어오면 CI가 이미지를 GHCR에 올린다. 새 GitOps 연결은 남아 있다. 현재 클러스터 배포는 레거시 이미지다 |
| 프론트엔드 | 새 API로 옮겼다. 옮긴 단계는 [frontend/docs/architecture.md](../frontend/docs/architecture.md)의 "옮긴 과정"에 있다 |
| 레거시 데이터 이전 | 아직 없다 |
