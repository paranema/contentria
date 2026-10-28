#!/bin/sh
# 로컬 compose의 PostgreSQL이 처음 뜰 때 한 번 돈다(데이터가 비어 있을 때만).
# 운영과 같은 스크립트로 롤과 스키마를 만들고, 비밀번호는 모두 local로 둔다.
set -eu

password=local

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v member_migrator_password="$password" \
    -v publishing_migrator_password="$password" \
    -v interaction_migrator_password="$password" \
    -v notification_migrator_password="$password" \
    -v media_migrator_password="$password" \
    -v analytics_migrator_password="$password" \
    -v api_app_password="$password" \
    -v batch_app_password="$password" \
    -v worker_app_password="$password" \
    -f /contentria-v2/01-database-and-roles.sql

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname contentria_v2 \
    -f /contentria-v2/02-schemas-and-grants.sql
