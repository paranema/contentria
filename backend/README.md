# Contentria Backend

바운디드 컨텍스트를 최상위 축으로 구성한 모듈러 모놀리스 백엔드입니다. 추후 필요에 따라 특정 컨텍스트를 독립된 서비스로 분리할 수 있도록 설계했습니다.
설계 문서의 전체 목차와 추천 읽기 순서는 [docs/README.md](docs/README.md)에 정리되어 있습니다.

## 컨텍스트 구성

| 컨텍스트                                                      | 아키텍처 패턴 | 담당 도메인                                                                   |
| ------------------------------------------------------------- | ------------- | ----------------------------------------------------------------------------- |
| [member](docs/contexts/member/README.md) (회원)               | 헥사고날      | 회원가입, 로그인(이메일 인증 코드, 비밀번호, Google OAuth), 세션 관리, 프로필 |
| [publishing](docs/contexts/publishing/README.md) (출판)       | 헥사고날      | 블로그 개설, 카테고리 관리, 게시글 작성·발행 및 공개 조회                     |
| [interaction](docs/contexts/interaction/README.md) (상호작용) | 레이어드      | 블로그 구독, 게시글 좋아요 및 댓글                                            |
| [notification](docs/contexts/notification/README.md) (알림)   | 레이어드      | 새 글·새 댓글 알림 생성 및 알림함 조회                                        |
| [media](docs/contexts/media/README.md) (미디어)               | 헥사고날      | 이미지·영상 업로드, 파일 검증, HLS 변환, 게시글 연결 및 미사용 파일 정리      |
| [analytics](docs/contexts/analytics/README.md) (분석)         | 레이어드      | 방문 기록 수집, 일별 통계 집계, 조회수 관리                                   |

컨텍스트 경계를 나눈 기준은 [docs/strategic/03-bounded-contexts.md](docs/strategic/03-bounded-contexts.md)에서, 컨텍스트별 내부 아키텍처 선정 이유는 [docs/architecture/02-internal-architecture.md](docs/architecture/02-internal-architecture.md)에서 확인할 수 있습니다.

## 디렉터리 구조

```text
backend/
├── build-logic/                 Gradle 컨벤션 플러그인
├── gradle/                      의존성 버전 카탈로그(libs.versions.toml), Gradle Wrapper
├── shared-kernel/               모든 컨텍스트가 공유하는 공통 기반 모듈 (식별자, 예외, 영속성 기반)
├── platform/
│   ├── messaging/               Transactional Outbox, Relay, Inbox (Kafka 연동)
│   └── architecture-rules/      아키텍처 규칙 검증 모듈 (ArchUnit 테스트 전용)
├── contexts/
│   └── <컨텍스트>/
│       ├── <컨텍스트>-api/      외부 컨텍스트에 노출하는 공개 인터페이스 모듈
│       └── <컨텍스트>-core/     컨텍스트 내부 구현 모듈 (도메인, 유스케이스, 어댑터, 마이그레이션)
├── apps/                        컨텍스트를 조립하여 프로세스로 실행하는 애플리케이션 모듈
│   ├── api/                     HTTP API 서버
│   ├── batch/                   배치 애플리케이션
│   ├── worker/                  영상 변환 워커
│   └── migrator/                컨텍스트별 Flyway DB 마이그레이션 실행기
└── docs/                        아키텍처 및 도메인 설계 문서
```

각 모듈의 상세 구조는 [docs/architecture/01-module-structure.md](docs/architecture/01-module-structure.md)에 정리되어 있습니다.

## 모듈 의존 관계

| 모듈                                      | 유형                         | 의존 대상 모듈                                                       |
| ----------------------------------------- | ---------------------------- | -------------------------------------------------------------------- |
| `shared-kernel`                           | 라이브러리                   | 없음                                                                 |
| `platform:messaging`                      | 라이브러리                   | `shared-kernel`                                                      |
| `platform:architecture-rules`             | 테스트 전용 라이브러리       | 없음 (ArchUnit)                                                      |
| `contexts:*:*-api`                        | 라이브러리                   | `shared-kernel`                                                      |
| `contexts:member:member-core`             | 라이브러리                   | `member-api`                                                         |
| `contexts:publishing:publishing-core`     | 라이브러리                   | `publishing-api`, `member-api`, `media-api`, `messaging`             |
| `contexts:interaction:interaction-core`   | 라이브러리                   | `interaction-api`, `member-api`, `publishing-api`, `messaging`       |
| `contexts:notification:notification-core` | 라이브러리                   | `notification-api`, `interaction-api`, `publishing-api`, `messaging` |
| `contexts:media:media-core`               | 라이브러리                   | `media-api`                                                          |
| `contexts:analytics:analytics-core`       | 라이브러리                   | `analytics-api`, `publishing-api`                                    |
| `apps:api`                                | 실행 모듈 (API 서버)         | 6개 컨텍스트의 `-core`, `messaging`                                  |
| `apps:batch`                              | 실행 모듈 (배치)             | `media-core`, `analytics-core`                                       |
| `apps:worker`                             | 실행 모듈 (상주 워커)        | `media-core`                                                         |
| `apps:migrator`                           | 실행 모듈 (마이그레이션 Job) | 6개 컨텍스트의 `-core` (마이그레이션 SQL만 참조, `runtimeOnly`)      |

- 모든 `-core` 모듈은 `shared-kernel`을 기본으로 의존합니다.
- `-core` 모듈끼리는 직접 의존할 수 없으며, 다른 컨텍스트를 참조할 때는 반드시 상대 컨텍스트의 `-api` 모듈만 의존해야 합니다. 이 규칙은 빌드 시 ArchUnit 테스트로 검증합니다.

## 컨텍스트 간 통신

| 통신 방식                 | 주요 사용처                                               | 설계 규칙                                                                                                                                                                   |
| ------------------------- | --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 비동기 이벤트             | 게시글 발행·삭제, 댓글 작성                               | 발행 측은 비즈니스 로직과 동일한 트랜잭션에서 Outbox 테이블에 기록하고, Relay가 Kafka로 발행합니다. 수신 측은 Inbox 테이블을 통해 중복 처리를 방지합니다.                   |
| 동기 조회 및 명령         | 게시글 공개 여부 확인, 회원 정보 조회, 게시글 미디어 연결 | 상대 컨텍스트의 `-api` 포트를 호출합니다. 단일 JVM 내에서 동작하더라도 원격 호출을 전제로 설계하며, 외부 컨텍스트 호출은 각 컨텍스트의 번역 계층(ACL) 한 곳으로 격리합니다. |
| 데이터 조합 (Composition) | 블로그 대시보드, 게시글 영상 정보 조합                    | 단일 화면 응답을 위해 여러 컨텍스트의 데이터를 조합해야 하는 경우 `apps/api`의 `composition` 계층에서 처리합니다.                                                           |

상세한 통신 규칙은 [docs/architecture/03-context-integration.md](docs/architecture/03-context-integration.md)를 참고해 주세요.

## 애플리케이션 프로세스

| 프로세스        | 역할                                                                                         | PostgreSQL 계정 (Role) | 외부 의존성                                                  |
| --------------- | -------------------------------------------------------------------------------------------- | ---------------------- | ------------------------------------------------------------ |
| `apps/api`      | HTTP API 제공, Outbox 이벤트 릴레이, 이벤트 소비                                             | `api_app`              | PostgreSQL, Kafka, Cloudflare R2, SMTP, Google OAuth         |
| `apps/batch`    | 단발성 배치 작업 실행 (`mediaImageCleanup`, `mediaVideoCleanup`, `analyticsDailyStatistics`) | `batch_app`            | PostgreSQL, Cloudflare R2                                    |
| `apps/worker`   | Cloudflare Queues에서 R2 업로드 이벤트 알림을 가져와(Pull) 영상을 HLS로 변환                 | `worker_app`           | PostgreSQL, Cloudflare R2, Cloudflare Queues, FFmpeg·FFprobe |
| `apps/migrator` | 컨텍스트별 마이그레이션 전용 계정으로 Flyway 스키마 마이그레이션 실행                        | `<컨텍스트>_migrator`  | PostgreSQL                                                   |

`apps/batch`는 여러 배치 Job을 포함하고 있으므로, 실행할 Job 이름을 인자로 지정해야 기동됩니다.

```bash
./gradlew :apps:batch:bootRun --args='--spring.batch.job.name=mediaImageCleanup'
```

### 로컬에서 API 서버 실행하기

`apps/api`는 `spring-boot-docker-compose`(`developmentOnly`)를 사용하여 로컬 인프라 컨테이너를 자동으로 관리합니다. `bootRun`을 실행하면 [apps/api/compose.yaml](apps/api/compose.yaml)을 기반으로 `docker compose up --wait`가 수행되고, 애플리케이션 종료 시 컨테이너도 함께 중지됩니다. 이 의존성은 실행 JAR나 테스트 클래스패스에는 포함되지 않으며, 로컬 실행 시 Docker가 필요합니다.

| 서비스     | 호스트 포트 | 역할                                                                                                                                                                                     |
| ---------- | ----------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `postgres` | `15432`     | 최초 기동 시 `infra/database/contentria-v2` 스크립트로 운영 환경과 동일한 DB 계정(Role)과 스키마를 생성합니다. 로컬에서는 비밀번호 없이(`trust`) 접속하며 권한 구조는 운영과 동일합니다. |
| `migrate`  | -           | 기동할 때마다 현재 소스 코드의 마이그레이션을 컨텍스트별 계정으로 적용합니다. 마이그레이션이 완료되어야 API 서버가 기동됩니다.                                                           |
| `kafka`    | `19092`     | 단일 노드 브로커로 동작합니다.                                                                                                                                                           |
| `kafka-ui` | `18080`     | 브라우저에서 토픽, 메시지, 컨슈머 그룹을 조회합니다 (별도 로그인 없음).                                                                                                                  |

- **포트 및 네트워크 격리**: 로컬 PC의 다른 PostgreSQL·Kafka와 충돌하지 않도록 기본 포트에 `10000`을 더한 포트를 사용하며, `127.0.0.1`에만 바인딩하여 외부 접근을 차단합니다.
- **접속 설정**: Spring Boot는 컨테이너 기동과 종료만 담당합니다. PostgreSQL 자동 연결 기능을 켜면 슈퍼유저 계정으로 접속하여 `api_app` 계정의 권한 제약을 우회하게 되므로 자동 연결을 비활성화했습니다. 애플리케이션은 [apps/api/.env.sample](apps/api/.env.sample)에 정의된 환경 변수로 접속 정보를 주입받습니다.
- **미디어 스토리지(Cloudflare R2)**: R2 이벤트 알림이 Cloudflare Queues로 전달되는 파이프라인은 로컬 컨테이너(MinIO 등)로 재현할 수 없으므로, 로컬 개발 환경에서도 개발 전용 R2 버킷(예: `contentria-media-dev`)을 분리해 사용합니다.

| 개발용 R2 버킷 설정 항목                                                        | 목적                                                                                                                                                       |
| ------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 해당 버킷에만 Object Read & Write 권한이 부여된 R2 API 토큰                     | 로컬 `.env` 토큰이 유출되더라도 운영 버킷에 영향을 주지 않도록 격리합니다.                                                                                 |
| 공개 접근 허용 (`r2.dev` 도메인 또는 커스텀 도메인)                             | `MEDIA_PUBLIC_BASE_URL`을 통해 이미지와 변환된 영상을 조회합니다.                                                                                          |
| CORS 설정 (Origin `http://localhost:3000`, Method `PUT`, Header `Content-Type`) | 브라우저에서 Presigned URL로 파일을 직접 업로드할 수 있도록 허용합니다.                                                                                    |
| 개발용 Cloudflare Queue 및 R2 이벤트 알림 연결                                  | 로컬 업로드 이벤트를 운영 워커가 가져가지 않도록 개발 전용 큐로 분리합니다 ([cloudflare-video-pipeline.md](docs/operations/cloudflare-video-pipeline.md)). |

단, 자동화된 통합 테스트는 외부 네트워크나 Cloudflare 계정 없이 독립적으로 실행되어야 하므로 Testcontainers 기반의 MinIO 컨테이너를 사용합니다.

```bash
cp apps/api/.env.sample apps/api/.env   # 비어 있는 시크릿 값을 채웁니다
set -a; . apps/api/.env; set +a
./gradlew :apps:api:bootRun
```

- `docker compose up`으로 컨테이너를 직접 띄워 둔 상태라면 Spring Boot는 기존 컨테이너를 그대로 사용하며, 애플리케이션을 종료해도 컨테이너를 멈추지 않습니다.
- 컨테이너가 중지되어도 데이터는 Docker 볼륨에 유지됩니다. 데이터베이스와 볼륨을 초기화하려면 `docker compose -f apps/api/compose.yaml down -v`를 실행합니다.

### 설정

각 프로세스가 사용하는 환경 변수 목록은 해당 앱 디렉터리의 `.env.sample`에 정의되어 있습니다.

| 프로세스        | 환경 변수 샘플 파일                                    |
| --------------- | ------------------------------------------------------ |
| `apps/api`      | [apps/api/.env.sample](apps/api/.env.sample)           |
| `apps/batch`    | [apps/batch/.env.sample](apps/batch/.env.sample)       |
| `apps/worker`   | [apps/worker/.env.sample](apps/worker/.env.sample)     |
| `apps/migrator` | [apps/migrator/.env.sample](apps/migrator/.env.sample) |

- **로컬 환경**: `.env.sample`을 같은 폴더의 `.env`로 복사해 값을 채워 사용합니다. `.env` 파일은 Git 추적 및 컨테이너 이미지 빌드 대상에서 제외됩니다.
- **운영 환경**: 민감한 시크릿 값은 Kubernetes `Secret`으로, 일반 설정값은 `ConfigMap`으로 주입합니다.
- **환경 변수 정합성 검증**: 각 앱의 `EnvSampleTest`가 `application.yml`(`migrator`는 `main`)에서 참조하는 환경 변수와 `.env.sample`의 항목이 일치하는지 검증합니다. 새로운 환경 변수를 추가할 때는 `.env.sample`에도 함께 반영해야 빌드가 통과합니다.

## 빌드 및 테스트

JDK 버전은 저장소 루트의 `mise.toml` 설정을 따릅니다.

```bash
./gradlew build
```

통합 테스트는 Testcontainers를 통해 실제 인프라 컨테이너를 기동하므로 Docker가 실행 중이어야 합니다.

| 컨테이너 이미지                     | 테스트 대상                                                                  |
| ----------------------------------- | ---------------------------------------------------------------------------- |
| `postgres:17`                       | Flyway 마이그레이션, 리포지토리, 애플리케이션 통합 테스트                    |
| `apache/kafka:4.1.0`                | `apps/api`의 이벤트 발행·소비 흐름                                           |
| `cgr.dev/chainguard/minio:latest`   | 스토리지 어댑터, 파일 업로드, 미사용 파일 정리 배치, 영상 변환 (S3 호환 API) |
| `jrottenberg/ffmpeg:7.1-ubuntu2404` | `apps/worker`의 실제 영상 인코딩 및 썸네일 추출                              |

Colima 환경에서는 Testcontainers가 Docker 소켓을 인식할 수 있도록 아래 환경 변수를 설정해야 합니다.

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 데이터베이스

신규 아키텍처는 기존 레거시(`contentria`)와 분리된 `contentria_v2` 데이터베이스를 사용합니다.
컨텍스트마다 독립된 스키마를 두며, 각 스키마는 해당 컨텍스트의 마이그레이션 전용 계정(`<컨텍스트>_migrator`)이 소유합니다. 애플리케이션 실행 계정(`*_app`)에는 데이터 조회·수정(DML) 권한만 부여하고, 컨텍스트 간 물리적 외래키(FK)는 두지 않습니다.

| 순서 | 작업 내용                                                | 스크립트 및 모듈 위치                                    |
| ---- | -------------------------------------------------------- | -------------------------------------------------------- |
| 1    | 데이터베이스 및 계정(Role) 생성 (슈퍼유저, 최초 1회)     | `infra/database/contentria-v2/01-database-and-roles.sql` |
| 2    | 컨텍스트별 스키마 생성 및 권한 부여 (슈퍼유저, 최초 1회) | `infra/database/contentria-v2/02-schemas-and-grants.sql` |
| 3    | 스키마 마이그레이션 실행 (배포 시마다 실행)              | `apps/migrator`                                          |

상세한 데이터베이스 및 영속성 규칙은 [docs/architecture/04-persistence-conventions.md](docs/architecture/04-persistence-conventions.md)에 정리되어 있습니다.
