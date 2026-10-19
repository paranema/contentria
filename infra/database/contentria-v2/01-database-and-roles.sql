-- 새 구조(backend/contexts, backend/apps)의 데이터베이스와 롤.
-- 레거시가 쓰는 contentria 데이터베이스와 따로 둔다 (backend/docs/architecture/04-persistence-conventions.md).
--
-- 슈퍼유저로 postgres 데이터베이스에 접속해 한 번 실행한다. 비밀번호는 psql 변수로 넘긴다.
--   psql -d postgres \
--     -v member_migrator_password=... \
--     -v api_app_password=... \
--     -f 01-database-and-roles.sql

CREATE DATABASE contentria_v2;

-- 마이그레이션 롤: 자기 컨텍스트 스키마의 소유자. 다른 스키마에는 권한이 없다.
CREATE ROLE member_migrator LOGIN PASSWORD :'member_migrator_password';

-- 런타임 롤: 프로세스마다 하나. DML만 가진다.
CREATE ROLE api_app LOGIN PASSWORD :'api_app_password';
