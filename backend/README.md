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

### 설정

기본값이 없는 값은 빠지면 프로세스가 뜨지 않는다. 비밀 값은 저장소에 두지 않는다.

| 프로세스 | 기본값 없는 환경 변수 |
|---|---|
| `apps/api` | `DATABASE_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `MEMBER_ACCESS_TOKEN_SECRET`(Base64, 256비트 이상), `ANALYTICS_VISITOR_KEY_SECRET`, `MEDIA_PUBLIC_BASE_URL`, `R2_ENDPOINT`, `R2_BUCKET_NAME`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` |
| `apps/batch` | `DATABASE_PASSWORD`, `MEDIA_PUBLIC_BASE_URL`, `R2_*` 네 개 |
| `apps/worker` | `DATABASE_PASSWORD`, `MEDIA_PUBLIC_BASE_URL`, `R2_*` 네 개, `CLOUDFLARE_ACCOUNT_ID`, `CF_QUEUE_ID`, `CF_API_TOKEN` |
| `apps/migrator` | `MIGRATION_DATABASE_URL`, 컨텍스트마다 `<컨텍스트>_MIGRATOR_USERNAME`, `<컨텍스트>_MIGRATOR_PASSWORD` |

`DATABASE_URL`은 `jdbc:postgresql://localhost:5432/contentria_v2`, `KAFKA_BOOTSTRAP_SERVERS`는 `localhost:9092`가 기본값이다.
로컬에서는 `MEMBER_MAIL_MODE=log`(메일 대신 로그에 코드를 쓴다)와 `MEMBER_COOKIE_SECURE=false`를 준다. 나머지 값과 기본값은 각 app의 `application.yml`에 있다.

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
| 컨테이너 이미지와 배포 | 아직 없다. `infra/gitops`가 배포하는 것은 레거시 이미지(`blog-api`, `blog-batch`)다 |
| 프론트엔드 | 아직 레거시 API(`/api/blogs/create`, `/api/videos/presigned` 등)에 맞춰져 있다. 새 API로 옮기는 작업은 남아 있다 |
| 레거시 데이터 이전 | 아직 없다 |
