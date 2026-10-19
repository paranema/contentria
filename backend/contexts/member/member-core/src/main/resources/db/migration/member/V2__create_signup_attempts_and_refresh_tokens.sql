-- 이메일 가입 시도와 리프레시 토큰. 둘 다 계정과 다른 애그리거트라서 accounts를 참조하는 외래키를 두지 않는다.

CREATE TABLE signup_attempts (
    id              uuid                        NOT NULL,
    email           varchar(255)                NOT NULL,
    code_hash       varchar(64)                 NOT NULL,
    failed_attempts integer                     NOT NULL,
    expires_at      timestamp(6) with time zone NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_signup_attempts PRIMARY KEY (id)
);

-- 재발송할 때 같은 주소의 이전 시도를 지운다
CREATE INDEX ix_signup_attempts_email ON signup_attempts (email);

CREATE TABLE refresh_tokens (
    id         uuid                        NOT NULL,
    account_id uuid                        NOT NULL,
    token_hash varchar(64)                 NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_account_id ON refresh_tokens (account_id);
