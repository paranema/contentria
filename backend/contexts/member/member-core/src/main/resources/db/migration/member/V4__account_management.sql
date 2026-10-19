-- 계정 관리(흐름 4). 새 테이블은 모두 계정과 다른 애그리거트라서 accounts를 참조하는 외래키를 두지 않는다.

-- 탈퇴한 회원의 프로필은 지우지 않고 탈퇴 시각만 남긴다
ALTER TABLE profiles ADD COLUMN withdrawn_at timestamp(6) with time zone;

-- 비밀번호 설정, 재설정 링크의 토큰. 해시만 저장하고, 쓰면 지운다
CREATE TABLE password_tokens (
    id         uuid                        NOT NULL,
    account_id uuid                        NOT NULL,
    token_hash varchar(64)                 NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_password_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_tokens_account_id ON password_tokens (account_id);

-- 이메일 변경 시도. email은 새 주소, code_hash는 현재 주소 코드와 새 주소 코드를 이은 값의 해시다
CREATE TABLE email_change_attempts (
    id              uuid                        NOT NULL,
    account_id      uuid                        NOT NULL,
    email           varchar(255)                NOT NULL,
    code_hash       varchar(64)                 NOT NULL,
    failed_attempts integer                     NOT NULL,
    expires_at      timestamp(6) with time zone NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_email_change_attempts PRIMARY KEY (id)
);

CREATE INDEX ix_email_change_attempts_account_id ON email_change_attempts (account_id);

-- 탈퇴 시도. 계정 주소로 보낸 코드를 기다린다
CREATE TABLE withdrawal_attempts (
    id              uuid                        NOT NULL,
    account_id      uuid                        NOT NULL,
    email           varchar(255)                NOT NULL,
    code_hash       varchar(64)                 NOT NULL,
    failed_attempts integer                     NOT NULL,
    expires_at      timestamp(6) with time zone NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_withdrawal_attempts PRIMARY KEY (id)
);

CREATE INDEX ix_withdrawal_attempts_account_id ON withdrawal_attempts (account_id);
