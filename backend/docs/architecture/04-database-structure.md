# 데이터베이스 구조

바운디드 컨텍스트 경계를 데이터베이스 수준에서 격리하기 위한 스키마 구성, PostgreSQL 계정(Role) 권한 분리, 스키마 마이그레이션 및 이벤트 메시징 테이블 구조를 정의합니다.

## 개요

### 컨텍스트별 스키마

신규 아키텍처는 기존 레거시(`contentria` 데이터베이스)와 분리된 **`contentria_v2` 데이터베이스**를 사용하며, 그 안에 바운디드 컨텍스트마다 독립된 스키마를 하나씩 둡니다([배경 참고](#단일-데이터베이스-내-스키마-분리)). 스키마 이름은 각 컨텍스트의 영문 식별자와 동일합니다.

| 스키마 이름    | 소유 도메인 및 용도                                        |
| -------------- | ---------------------------------------------------------- |
| `member`       | 회원 컨텍스트                                              |
| `publishing`   | 출판 컨텍스트                                              |
| `interaction`  | 상호작용 컨텍스트                                          |
| `media`        | 미디어 컨텍스트                                            |
| `analytics`    | 분석 컨텍스트                                              |
| `notification` | 알림 컨텍스트                                              |
| `batch`        | Spring Batch 메타데이터 전용 스키마 (도메인 컨텍스트 아님) |

데이터베이스 초기 구성 스크립트는 `infra/database/contentria-v2/`에 위치하며, 최초 1회 슈퍼유저 권한으로 `01-database-and-roles.sql`(데이터베이스 및 Role 생성)과 `02-schemas-and-grants.sql`(스키마 생성 및 권한 부여)을 순서대로 실행합니다.

### 스키마 격리 원칙

타 컨텍스트의 데이터는 반드시 해당 컨텍스트가 공개한 `-api` 포트를 통해서만 조회하며, 데이터베이스 수준에서 컨텍스트 경계를 침범하는 다음 행위를 금지합니다.

- 다른 컨텍스트의 스키마에 속한 테이블을 SQL(`JOIN`, 서브쿼리, DML)로 직접 참조하지 않습니다.
- 애그리거트 경계를 넘는 물리적 외래키(FK)를 생성하지 않습니다(동일 컨텍스트 내의 다른 애그리거트 간에도 동일하게 적용, [03-context-integration.md](03-context-integration.md)의 `외래키` 절 참고).
- JPA 엔티티의 `@Table(schema = ...)`에 자기 컨텍스트가 아닌 다른 스키마 이름을 지정하지 않습니다.
- 다른 컨텍스트의 테이블을 참조하는 데이터베이스 뷰(`VIEW`)를 생성하지 않습니다.

## 데이터베이스 계정 (`Role`)

### 계정 권한 분리

데이터베이스 계정은 스키마 변경(DDL) 권한을 가진 **컨텍스트별 마이그레이션 전용 계정(`*_migrator`)**과 데이터 조회·수정(DML) 권한만 가진 **프로세스별 애플리케이션 실행 계정(`*_app`)**으로 엄격히 분리합니다.

| PostgreSQL 계정 (Role)         | 사용 주체                            | 부여된 권한 범위                                                                              |
| ------------------------------ | ------------------------------------ | --------------------------------------------------------------------------------------------- |
| `<컨텍스트>_migrator` (총 6개) | 해당 컨텍스트의 Flyway 마이그레이션  | 자기 컨텍스트 스키마의 소유자(`OWNER`). 다른 컨텍스트 스키마에 대한 권한 없음                 |
| `batch_migrator`               | Spring Batch 메타데이터 마이그레이션 | `batch` 스키마의 소유자                                                                       |
| `api_app`                      | `apps/api` 서버 프로세스             | 6개 컨텍스트 스키마에 대한 `USAGE` 및 테이블 DML(`SELECT`, `INSERT`, `UPDATE`, `DELETE`) 권한 |
| `batch_app`                    | `apps/batch` 배치 프로세스           | 배치가 접근하는 스키마(`member`, `media`, `analytics`, `batch`)의 `USAGE` 및 테이블 DML 권한  |
| `worker_app`                   | `apps/worker` 워커 프로세스          | `media` 스키마의 `USAGE` 및 테이블 DML 권한                                                   |

### 스키마 접근 제어

애플리케이션 런타임 계정(`*_app`)에는 테이블을 생성하거나 변경하는 DDL 권한을 부여하지 않으며, 각 프로세스는 자신이 포함하는 컨텍스트 스키마에만 접근할 수 있습니다. 마이그레이션 계정(`*_migrator`)이 새로 생성하는 테이블에도 런타임 계정의 DML 권한이 자동 부여되도록 `ALTER DEFAULT PRIVILEGES`를 설정합니다.

```sql
CREATE ROLE publishing_migrator LOGIN;
CREATE SCHEMA publishing AUTHORIZATION publishing_migrator;

GRANT USAGE ON SCHEMA publishing TO api_app;
ALTER DEFAULT PRIVILEGES FOR ROLE publishing_migrator IN SCHEMA publishing
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO api_app;
```

## 스키마 마이그레이션

### 마이그레이션 규칙

데이터베이스 스키마 형상 관리는 Flyway를 사용하며, 애플리케이션 서버 기동 시점이 아닌 **독립된 마이그레이션 전용 프로세스(`apps/migrator`)**를 통해 실행합니다([배경 참고](#마이그레이션-전용-프로세스-분리)).

| 항목                    | 규칙                                                                                                    |
| ----------------------- | ------------------------------------------------------------------------------------------------------- |
| 마이그레이션 파일 위치  | 각 `<컨텍스트>-core` 모듈의 `src/main/resources/db/migration/<컨텍스트>/`                               |
| 변경 이력 테이블        | 각 컨텍스트 스키마 내부의 `flyway_schema_history`                                                       |
| 실행 DB 계정            | 컨텍스트별 `<컨텍스트>_migrator` 계정                                                                   |
| 스크립트 명명 규칙      | `V<버전번호>__<설명>.sql` (버전 번호는 각 컨텍스트 내에서 독립적으로 증가)                              |
| 실행 시점               | 배포 시 애플리케이션 기동 전에 마이그레이션 전용 Job(`apps/migrator`)으로 선행 실행 (ArgoCD PreSync 훅) |
| 컨텍스트 간 실행 순서   | 컨텍스트 간 외래키가 없으므로 컨텍스트 사이의 마이그레이션 순서 의존성이 없습니다.                      |
| Hibernate `ddl-auto`    | 모든 실행 환경에서 `validate`로 고정하여 엔티티 매핑과 실제 스키마의 정합성만 검증                      |
| Spring Batch 메타데이터 | `spring-batch.jdbc.initialize-schema=never`로 두고, 필요한 경우 `batch` 스키마 마이그레이션으로 관리    |

### 전용 실행 모듈 (`apps/migrator`)

`apps/migrator`는 6개 `<컨텍스트>-core` 모듈을 `runtimeOnly`로 참조하여 마이그레이션 SQL 리소스만 클래스패스에 포함한 뒤, 컨텍스트마다 해당 `<컨텍스트>_migrator` 계정으로 Flyway를 순차 실행합니다. 불필요한 JPA 자동 설정이나 애플리케이션 빈이 함께 기동되지 않도록 Spring Boot 컨텍스트를 띄우지 않고 순수 Kotlin `main` 함수로만 동작합니다.

| 설정 항목            | 설명                                                                                                                                |
| -------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| 필수 환경 변수       | `MIGRATION_DATABASE_URL`, `<컨텍스트>_MIGRATOR_USERNAME`, `<컨텍스트>_MIGRATOR_PASSWORD`                                            |
| `createSchemas` 옵션 | `false`로 비활성화 (스키마 자체는 초기 준비 SQL이 소유자와 함께 생성하며, 마이그레이션 계정에 DB 전체 스키마 생성 권한을 주지 않음) |
| 실행 대상 컨텍스트   | `Migrator.CONTEXTS` 상수에 정의된 컨텍스트 목록                                                                                     |

### 스키마 격리 검증

스키마 경계와 계정 권한, 엔티티 매핑의 정합성은 컴파일 타임부터 통합 테스트까지 네 단계에 걸쳐 검증합니다.

| 검증 대상                                            | 차단 및 검증 장치                                                                                     | 검증 시점       |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | --------------- |
| 타 컨텍스트 엔티티에 대한 JPA 매핑 및 JPQL `JOIN`    | Gradle 모듈 경계 (타 컨텍스트의 `-core` 엔티티 클래스가 클래스패스에 존재하지 않음)                   | 컴파일 타임     |
| 스키마(컨텍스트)를 넘는 외래키(FK) 생성              | 컨텍스트별 `<컨텍스트>_migrator` 계정 권한 분리 (타 스키마 `REFERENCES` 권한이 없어 DDL 실행 거부)    | 마이그레이션 시 |
| 교차 스키마 외래키 부재 및 DB 계정 권한 검증         | `apps/migrator`의 `MigratorIntegrationTest` (Testcontainers PostgreSQL 17에서 전체 마이그레이션 검증) | CI 테스트 타임  |
| Flyway DDL과 JPA 엔티티 매핑의 일치 여부             | 각 컨텍스트의 `MigrationMatchesMappingTest` (Hibernate `validate` 검증)                               | CI 테스트 타임  |
| 프로세스가 담당하지 않는 컨텍스트 스키마에 대한 접근 | 프로세스별 런타임 DB 계정(`*_app`) 권한 분리                                                          | 런타임          |

`MigratorIntegrationTest`는 전체 컨텍스트 마이그레이션을 적용한 뒤 ① 재실행 시 멱등성, ② `api_app`의 DDL 실행 차단, ③ 타 스키마 테이블 접근 차단과 함께 아래 카탈로그 조회 쿼리로 스키마 경계를 넘는 외래키가 0건인지 검증합니다.

```sql
-- 스키마 경계를 넘는 외래키 조회 쿼리 (결과가 1건이라도 존재하면 통합 테스트 실패)
SELECT con.conname, src_ns.nspname AS from_schema, dst_ns.nspname AS to_schema
FROM pg_constraint con
JOIN pg_class src        ON src.oid = con.conrelid
JOIN pg_namespace src_ns ON src_ns.oid = src.relnamespace
JOIN pg_class dst        ON dst.oid = con.confrelid
JOIN pg_namespace dst_ns ON dst_ns.oid = dst.relnamespace
WHERE con.contype = 'f'
  AND src_ns.nspname <> dst_ns.nspname;
```

## 이벤트 메시징 테이블

### 컨텍스트별 테이블 배치

비동기 도메인 이벤트를 발행하는 컨텍스트는 자신의 스키마에 `outbox_event` 테이블을 두고, 이벤트를 구독하는 컨텍스트는 자신의 스키마에 `inbox_event` 테이블을 둡니다([03-context-integration.md](03-context-integration.md)의 `비동기 도메인 이벤트` 절 참고). 공통 테이블 대신 컨텍스트별 스키마에 각각 생성해 두어야 향후 특정 컨텍스트를 독립 마이크로서비스로 분리할 때 해당 스키마만 그대로 덤프하여 이전할 수 있습니다.

| 컨텍스트                       | `outbox_event` 보유 여부                 | `inbox_event` 보유 여부                  |
| ------------------------------ | ---------------------------------------- | ---------------------------------------- |
| `publishing`                   | 보유                                     | 없음                                     |
| `interaction`                  | 보유                                     | 보유                                     |
| `notification`                 | 없음                                     | 보유                                     |
| `member`, `media`, `analytics` | 컨텍스트 간 발행 이벤트가 추가될 때 생성 | 컨텍스트 간 구독 이벤트가 추가될 때 생성 |

### 테이블 스키마

`outbox_event`는 미발행 레코드(`published_at IS NULL`)만 빠르게 폴링할 수 있도록 부분 인덱스를 두며, `inbox_event`는 동일한 컨텍스트 내의 여러 컨슈머가 같은 이벤트를 각각 독립적으로 처리할 수 있도록 `(event_id, consumer)` 복합 기본키를 사용합니다.

```sql
CREATE TABLE publishing.outbox_event (
    id             uuid         PRIMARY KEY,         -- 이벤트 고유 식별자 (eventId)
    aggregate_type varchar(50)  NOT NULL,            -- 대상 토픽 결정에 사용
    aggregate_id   uuid         NOT NULL,            -- Kafka 파티션 메시지 키
    event_type     varchar(100) NOT NULL,
    event_version  int          NOT NULL,
    payload        jsonb        NOT NULL,
    occurred_at    timestamptz  NOT NULL,
    published_at   timestamptz                       -- 릴레이가 Kafka에 발행 완료한 시각 (NULL이면 미발행)
);
CREATE INDEX idx_outbox_event_unpublished ON publishing.outbox_event (occurred_at) WHERE published_at IS NULL;

CREATE TABLE interaction.inbox_event (
    event_id     uuid         NOT NULL,
    consumer     varchar(100) NOT NULL,              -- 컨슈머 그룹 이름
    processed_at timestamptz  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
```

## 설계 배경

### 단일 데이터베이스 내 스키마 분리

마이크로서비스의 'Database per Service' 원칙이 추구하는 본질은 데이터의 명확한 소유권과 결합 차단이며, 물리적 DB 인스턴스 분리는 이를 달성하는 수단입니다. 모듈러 모놀리스 단계에서는 단일 PostgreSQL 데이터베이스(`contentria_v2`) 안에서 컨텍스트별로 스키마만 분리해도 소유권 격리와 향후 스키마 단위 덤프 이전을 충분히 달성할 수 있습니다.

| 격리 수준                  | 타 컨텍스트 데이터 무단 접근 통제      | 모듈러 모놀리스 운영 비용                                              |
| -------------------------- | -------------------------------------- | ---------------------------------------------------------------------- |
| 단일 스키마 공유           | 통제 불가 (경계 없음)                  | 추가 비용 없음                                                         |
| **컨텍스트별 스키마 분리** | **DB 계정 권한 및 테스트로 통제 가능** | **단일 `DataSource`와 커넥션 풀을 유지하면서 논리적 소유권 확립 가능** |
| 물리적 데이터베이스 분리   | 원천 차단                              | 컨텍스트 수만큼 `DataSource`, `EntityManager`, 트랜잭션 매니저 필요    |

다만 현재 1단계에서는 `apps/api`가 단일 `api_app` 런타임 계정과 단일 `DataSource`를 공유하므로, 네이티브 SQL로 다른 컨텍스트 스키마를 직접 조회하는 코드는 DB 권한만으로 막히지 않아 코드 리뷰로 통제합니다. 추후 ① 컨텍스트 경계를 넘는 네이티브 SQL이 유입되는 문제가 발생하거나 ② 특정 컨텍스트를 마이크로서비스로 분리하기로 결정하면, 해당 컨텍스트부터 별도의 런타임 DB 계정과 독립된 `DataSource`·트랜잭션 매니저로 분리(2단계)하여 타 스키마 접근을 DB 수준(`permission denied`)에서 차단합니다.

### 마이그레이션 전용 프로세스 분리

만약 `apps/api` 등 서버 기동 시점에 Flyway가 자동 실행되도록 구성하면, 애플리케이션 런타임 계정(`api_app`)에 스키마와 테이블을 변경할 수 있는 DDL 권한까지 부여해야 합니다. 그렇게 되면 마이그레이션 계정(`*_migrator`)과 런타임 계정(`*_app`)의 권한을 분리한 보안 및 스키마 격리 원칙이 무너지므로, 마이그레이션은 반드시 배포 전 전용 프로세스(`apps/migrator`)로만 실행합니다.
