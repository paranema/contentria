-- 컨텍스트별 스키마와 권한. 01을 실행한 뒤, 슈퍼유저로 contentria_v2 데이터베이스에 접속해 실행한다.
--   psql -d contentria_v2 -f 02-schemas-and-grants.sql
--
-- 컨텍스트를 추가하면 그 컨텍스트의 블록을 이 파일에 더한다.

REVOKE ALL ON DATABASE contentria_v2 FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE contentria_v2 TO member_migrator, publishing_migrator, api_app;

-- ------------------------------------------------------------------ member
-- member_migrator가 스키마를 소유하므로 그 안에서만 DDL을 실행할 수 있다.
-- 다른 스키마에는 USAGE도 없어서, 다른 스키마를 참조하는 외래키를 만들 수 없다.
CREATE SCHEMA member AUTHORIZATION member_migrator;

-- api_app: member 테이블의 DML. member_migrator가 앞으로 만드는 테이블에도 자동으로 적용된다.
GRANT USAGE ON SCHEMA member TO api_app;
ALTER DEFAULT PRIVILEGES FOR ROLE member_migrator IN SCHEMA member
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO api_app;

-- ------------------------------------------------------------------ publishing
CREATE SCHEMA publishing AUTHORIZATION publishing_migrator;

GRANT USAGE ON SCHEMA publishing TO api_app;
ALTER DEFAULT PRIVILEGES FOR ROLE publishing_migrator IN SCHEMA publishing
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO api_app;
