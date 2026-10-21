# 영속성 규칙

컨텍스트별 스키마, DB 롤, 마이그레이션 규칙이다.
엔티티 작성 규칙, 식별자 전략, 공통 필드는 아직 작성하지 않았다.

## 데이터베이스

새 구조는 레거시와 같은 PostgreSQL 인스턴스에 **새 데이터베이스** `contentria_v2`를 만들어 쓴다. 이름은 확정 전이다.
레거시는 같은 인스턴스의 `contentria` 데이터베이스와 그 안의 `contentria` 스키마, `contentria` 롤을 쓴다(`infra/gitops/02-platform/postgres/cluster/base/cluster.yaml`의 initdb). 새 구조는 이것들을 쓰지 않는다.
레거시 데이터를 옮길지는 정하지 않았다. 옮기더라도 새 구조로 넘어가는 시점에 한 번 실행하는 작업이며, 지금의 스키마 설계에는 영향을 주지 않는다.
레거시를 정리할 때는 레거시 쪽 데이터베이스 객체만 지우면 된다.

준비 SQL은 `infra/database/contentria-v2/`에 있다. 슈퍼유저가 `01-database-and-roles.sql`(데이터베이스와 롤), `02-schemas-and-grants.sql`(스키마와 권한) 순서로 한 번 실행한다. 클러스터에 배포할 때 롤을 CNPG의 managed roles로 옮길지 정한다.

## 스키마

새 데이터베이스 안에 컨텍스트마다 스키마를 하나씩 둔다.
스키마 이름은 컨텍스트 영문 이름과 같다.

| 스키마 | 소유 |
|---|---|
| `member` | 회원 |
| `publishing` | 출판 |
| `interaction` | 상호작용 |
| `media` | 미디어 |
| `analytics` | 분석 |
| `notification` | 알림 |
| `batch` | Spring Batch 메타데이터. 컨텍스트가 아니다 |

### 금지

- 다른 컨텍스트의 스키마를 SQL로 참조한다 (JOIN, 서브쿼리, 쓰기 모두).
- 애그리거트를 넘는 외래키를 만든다. 같은 컨텍스트 안이어도 마찬가지다([03-context-integration.md](03-context-integration.md)의 "외래키").
- 엔티티의 `@Table(schema = ...)`에 자기 컨텍스트가 아닌 스키마를 쓴다.
- 다른 컨텍스트의 테이블을 감싸는 뷰를 만든다.

다른 컨텍스트의 데이터는 그 컨텍스트의 provided 포트로만 얻는다([03-context-integration.md](03-context-integration.md)).

## 강제 장치

### 1단계 (현재)

| 참조 경로 | 장치 | 실패하는 시점 |
|---|---|---|
| 다른 컨텍스트 엔티티의 JPA 매핑, JPQL JOIN | Gradle 모듈 경계. 다른 컨텍스트의 엔티티 클래스가 컴파일 경로에 없다 | 컴파일 |
| 컨텍스트를 넘는 외래키 | 컨텍스트별 마이그레이션 롤. 다른 스키마에 대한 권한이 없어 `REFERENCES`가 거부된다 | 마이그레이션 |
| 위 두 장치가 빠졌을 때 | 통합 테스트. 모든 마이그레이션을 적용한 뒤 아래 쿼리 결과가 0건이어야 한다 | CI |
| 프로세스가 맡지 않은 컨텍스트의 스키마 | 프로세스별 런타임 롤 (아래 "DB 롤") | 실행 |

```sql
-- 스키마를 넘는 외래키 목록. 결과가 있으면 테스트 실패
SELECT con.conname, src_ns.nspname AS from_schema, dst_ns.nspname AS to_schema
FROM pg_constraint con
JOIN pg_class src        ON src.oid = con.conrelid
JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
JOIN pg_class dst        ON dst.oid = con.confrelid
JOIN pg_namespace dst_ns ON dst_ns.oid = dst.relnamespace
WHERE con.contype = 'f'
  AND src_ns.nspname <> dst_ns.nspname;
```

1단계에서 남는 구멍은 하나다. 같은 런타임 롤로 접근할 수 있는 스키마끼리는 네이티브 SQL로 서로를 읽을 수 있다. 코드 리뷰로 막는다.

### 2단계 (조건부)

다음 중 하나가 생기면 컨텍스트마다 런타임 롤과 DataSource를 따로 둔다.

- 컨텍스트를 넘는 네이티브 SQL이 리뷰에서 걸러지지 않고 들어간 사례가 생긴다.
- 컨텍스트 하나를 서비스로 분리하기로 한다. 이 경우 그 컨텍스트만 먼저 분리한다.

2단계에서는 다른 스키마에 대한 SQL이 `permission denied`로 실패한다.
비용은 프로세스 하나에 커넥션 풀이 컨텍스트 수만큼 생기는 것이다. 또 `@Transactional`마다 트랜잭션 매니저를 지정해야 한다.

## DB 롤

지금 만든 롤은 `member_migrator`, `publishing_migrator`, `interaction_migrator`, `notification_migrator`, `media_migrator`, `analytics_migrator`, `api_app`, `batch_app`이다. 나머지는 그 롤을 쓰는 컨텍스트나 프로세스가 생길 때 `01-database-and-roles.sql`에 추가한다.

| 롤 | 쓰는 곳 | 권한 |
|---|---|---|
| `<컨텍스트>_migrator` (6개) | 그 컨텍스트의 마이그레이션 | 자기 스키마의 소유자. 다른 스키마에는 권한 없음 |
| `batch_migrator` | Spring Batch 메타데이터 마이그레이션 | `batch` 스키마의 소유자. 지금의 잡은 메타데이터를 남기지 않아 아직 없다 |
| `api_app` | api 프로세스 | 6개 컨텍스트 스키마의 `USAGE`와 테이블 DML |
| `batch_app` | batch 프로세스 | `member`(토큰 정리), `media`(이미지와 영상 정리), `analytics`(일별 집계), `batch`의 `USAGE`와 테이블 DML. 지금은 `media`, `analytics`가 있다. 잡이 생기는 스키마마다 더한다 |
| `worker_app` | worker 프로세스 | `media`의 `USAGE`와 테이블 DML |

런타임 롤(`*_app`)에는 DDL 권한을 주지 않는다. 테이블을 만들거나 바꾸는 것은 마이그레이션 롤만 한다.

마이그레이션 롤이 새로 만드는 테이블에도 런타임 권한이 자동으로 붙도록 기본 권한을 설정한다.

```sql
CREATE ROLE publishing_migrator LOGIN;
CREATE SCHEMA publishing AUTHORIZATION publishing_migrator;

GRANT USAGE ON SCHEMA publishing TO api_app;
ALTER DEFAULT PRIVILEGES FOR ROLE publishing_migrator IN SCHEMA publishing
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO api_app;
```

## 마이그레이션

Flyway를 쓴다.

| 항목 | 규칙 |
|---|---|
| 파일 위치 | 각 `<컨텍스트>-core` 모듈의 `src/main/resources/db/migration/<컨텍스트>/` |
| 이력 테이블 | 각 스키마 안의 `flyway_schema_history` |
| 실행 계정 | `<컨텍스트>_migrator` |
| 파일 이름 | `V<번호>__<설명>.sql`. 번호는 컨텍스트 안에서만 증가한다 |
| 실행 시점 | 애플리케이션 기동 시 실행하지 않는다. 배포 전에 마이그레이션 전용 Job으로 실행한다 (ArgoCD PreSync 훅) |
| 실행 순서 | 컨텍스트 사이에 순서 의존이 없다. 컨텍스트를 넘는 외래키가 없기 때문이다 |
| Hibernate `ddl-auto` | 테스트를 뺀 모든 환경에서 `validate` |
| Spring Batch 메타데이터 | `spring-batch-core`가 제공하는 PostgreSQL DDL을 `batch` 스키마의 마이그레이션으로 넣는다. `spring.batch.jdbc.initialize-schema`는 `never`로 둔다 |

마이그레이션은 `apps/migrator`가 실행한다([01-module-structure.md](01-module-structure.md)).
`apps/migrator`는 모든 `<컨텍스트>-core`의 마이그레이션 파일을 가져와, 컨텍스트마다 그 컨텍스트의 마이그레이션 롤로 Flyway를 실행한다.

`apps/migrator`는 Spring을 띄우지 않는 일반 `main`이다. 마이그레이션 파일을 가져오려고 각 컨텍스트의 `-core`를 `runtimeOnly`로 두는데, Spring을 띄우면 그 모듈의 JPA 자동 설정까지 따라오기 때문이다.

| 항목 | 값 |
|---|---|
| 환경 변수 | `MIGRATION_DATABASE_URL`, `<컨텍스트>_MIGRATOR_USERNAME`, `<컨텍스트>_MIGRATOR_PASSWORD` |
| `createSchemas` | 끈다. 스키마는 준비 SQL이 소유자와 함께 만든다. 스키마를 만들 수 있는 롤은 다른 컨텍스트의 스키마도 만들 수 있다 |
| 컨텍스트 목록 | `Migrator.CONTEXTS`. 컨텍스트에 첫 마이그레이션이 생기면 추가한다 |

`flyway_schema_history`도 마이그레이션 롤이 만들므로, 기본 권한에 따라 런타임 롤이 이 테이블에도 DML을 가지게 된다. 런타임 롤이 이력을 고칠 수 있다는 뜻이지만 지금은 받아들인다. 기본 권한의 동작에서 추론한 것이며 테스트로 확인하지는 않았다.

### 검증

| 테스트 | 확인하는 것 |
|---|---|
| `apps/migrator`의 `MigratorIntegrationTest` | PostgreSQL 17 컨테이너에 준비 SQL을 실행하고 마이그레이션 롤로 마이그레이션한다. 다시 실행하면 적용할 것이 없다. 스키마를 넘는 외래키가 없다. `api_app`은 DML만 되고 DDL은 안 된다. `member_migrator`는 다른 스키마를 참조하는 외래키를 만들 수 없다. `publishing_migrator`는 member 스키마에서 테이블을 만들지도 읽지도 못한다 |
| `member-core`의 `MigrationMatchesMappingTest` | 마이그레이션으로 만든 스키마가 엔티티 매핑과 일치한다 (Hibernate `validate`) |

두 테스트는 Testcontainers를 쓰므로 Docker가 필요하다.

## 아웃박스와 인박스 테이블

이벤트를 발행하는 컨텍스트는 `outbox_event`를, 소비하는 컨텍스트는 `inbox_event`를 자기 스키마에 둔다.
동작 규칙은 [03-context-integration.md](03-context-integration.md)의 "이벤트"에 있다.

| 컨텍스트 | `outbox_event` | `inbox_event` |
|---|---|---|
| publishing | 있음 | 없음 |
| interaction | 있음 | 있음 |
| notification | 없음 | 있음 |
| member, media, analytics | 발행하는 이벤트가 생길 때 만든다 | 소비하는 이벤트가 생길 때 만든다 |

```sql
CREATE TABLE publishing.outbox_event (
    id             uuid        PRIMARY KEY,         -- eventId
    aggregate_type varchar(50) NOT NULL,            -- 토픽 결정에 쓴다
    aggregate_id   uuid        NOT NULL,            -- 메시지 키
    event_type     varchar(100) NOT NULL,
    event_version  int         NOT NULL,
    payload        jsonb       NOT NULL,
    occurred_at    timestamptz NOT NULL,
    published_at   timestamptz                      -- 릴레이가 Kafka에 보낸 시각. 비어 있으면 아직 안 보냈다
);
CREATE INDEX idx_outbox_event_unpublished ON publishing.outbox_event (occurred_at) WHERE published_at IS NULL;

CREATE TABLE interaction.inbox_event (
    event_id     uuid         NOT NULL,
    consumer     varchar(100) NOT NULL,             -- 소비자 그룹 이름
    processed_at timestamptz  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
```

`inbox_event`의 기본키에 소비자를 넣는 이유: 같은 컨텍스트 안의 소비자 여럿이 같은 이벤트를 각자 처리할 수 있다.

두 테이블은 각 컨텍스트의 마이그레이션으로 만든다. 공통 테이블을 하나 두지 않는다. 컨텍스트를 서비스로 분리할 때 그 스키마만 옮기면 되도록 하기 위해서다.

## 결정과 이유

### 레거시 데이터베이스와 따로 둔다

**이전 결정:** 레거시의 `contentria` 스키마에 있는 테이블을 컨텍스트별 스키마로 옮기고, 이전 중에는 레거시와 같은 데이터베이스를 함께 쓴다.

**뒤집은 이유:** 새 구조는 레거시와 호환하지 않고 따로 배포하기로 했다. 같은 데이터베이스를 쓰면 레거시의 롤, `search_path`, 외래키와 계속 얽힌다.
같은 인스턴스를 쓰는 것은 운영 비용 때문이다. 데이터베이스가 다르면 서로의 테이블을 SQL로 참조할 수 없다.

### 데이터베이스가 아니라 스키마로 나눈다

| 분리 수준 | 다른 컨텍스트 데이터 접근 | 모듈러 모놀리스에서의 비용 |
|---|---|---|
| 같은 스키마 | 자유로움 | 없음. 경계도 없음 |
| **스키마 분리** | 권한으로 막을 수 있음 | DataSource를 하나로 유지할 수 있음 |
| 데이터베이스 분리 | 불가능 | 컨텍스트마다 DataSource, EntityManager, 트랜잭션 매니저 필요 |

서비스마다 DB를 두는 원칙의 핵심은 소유권이다. 물리적 분리는 수단이다.
모듈러 모놀리스 단계의 목적은 경계를 검증하는 것이므로 스키마로 충분하다.
컨텍스트를 서비스로 분리할 때는 그 스키마만 덤프해 새 데이터베이스로 옮긴다.

### 런타임 롤 분리를 2단계로 미룬다

레거시에서 네이티브 SQL로 컨텍스트를 넘는 곳은 인기 글 쿼리 한 곳뿐이다.
이 구멍 하나를 막기 위해 처음부터 커넥션 풀을 컨텍스트 수만큼 두는 것은 비용이 크다.

### 애플리케이션 기동 시 마이그레이션하지 않는다

기동 시 실행하려면 런타임 롤에 DDL 권한이 필요하다. 그러면 마이그레이션 롤을 나눈 의미가 없어진다.
