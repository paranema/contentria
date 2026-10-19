-- 이메일 코드 로그인 시도. 가입 시도와 같은 형태지만 테이블을 나눠, 한 목적으로 시작한 시도를 다른 목적으로 완료할 수 없게 한다.
-- 계정 ID 대신 주소를 담고, 코드가 돌아오면 주소로 계정을 다시 찾는다. 계정을 참조하는 외래키는 없다.

CREATE TABLE login_attempts (
    id              uuid                        NOT NULL,
    email           varchar(255)                NOT NULL,
    code_hash       varchar(64)                 NOT NULL,
    failed_attempts integer                     NOT NULL,
    expires_at      timestamp(6) with time zone NOT NULL,
    created_at      timestamp(6) with time zone NOT NULL,
    updated_at      timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_login_attempts PRIMARY KEY (id)
);

-- 재발송할 때 같은 주소의 이전 시도를 지운다
CREATE INDEX ix_login_attempts_email ON login_attempts (email);
