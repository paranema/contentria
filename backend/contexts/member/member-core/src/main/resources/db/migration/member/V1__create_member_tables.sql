-- member 컨텍스트의 첫 스키마. 애그리거트 두 개(Account, Profile)의 테이블이다.
-- Flyway가 search_path를 member 스키마로 잡으므로 테이블 이름에 스키마를 붙이지 않는다.
--
-- 외래키는 같은 애그리거트 안에만 둔다 (credentials, account_roles → accounts).
-- profiles.id는 계정 ID와 같은 값이지만 외래키를 두지 않는다. 애그리거트 사이이기 때문이다.
-- (docs/architecture/03-context-integration.md "외래키")

-- ------------------------------------------------------------------ Account 애그리거트

CREATE TABLE accounts (
    id         uuid                        NOT NULL,
    email      varchar(255)                NOT NULL,
    status     varchar(20)                 NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_email UNIQUE (email),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE TABLE account_roles (
    account_id uuid        NOT NULL,
    role       varchar(30) NOT NULL,
    CONSTRAINT pk_account_roles PRIMARY KEY (account_id, role),
    CONSTRAINT fk_account_roles_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_account_roles_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE credentials (
    id            uuid                        NOT NULL,
    account_id    uuid                        NOT NULL,
    provider      varchar(20)                 NOT NULL,
    password_hash varchar(255),
    provider_id   varchar(255),
    created_at    timestamp(6) with time zone NOT NULL,
    updated_at    timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_credentials PRIMARY KEY (id),
    CONSTRAINT fk_credentials_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    -- 계정당 공급자별 자격증명 하나
    CONSTRAINT uq_credentials_account_provider UNIQUE (account_id, provider),
    -- 소셜 신원 하나는 계정 하나에만. EMAIL 자격증명의 provider_id는 NULL이라 서로 충돌하지 않는다
    CONSTRAINT uq_credentials_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT ck_credentials_provider CHECK (provider IN ('EMAIL', 'GOOGLE'))
);

-- ------------------------------------------------------------------ Profile 애그리거트

CREATE TABLE profiles (
    id           uuid                        NOT NULL,
    display_name varchar(50)                 NOT NULL,
    picture_url  varchar(2048),
    created_at   timestamp(6) with time zone NOT NULL,
    updated_at   timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_profiles PRIMARY KEY (id),
    CONSTRAINT uq_profiles_display_name UNIQUE (display_name)
);
