#!/bin/sh
# apps/migrator의 Migrator와 같은 규칙: 컨텍스트마다 스키마, 기록 테이블, 마이그레이션 롤이 따로다.
# 스키마는 postgres-init.sh가 이미 만들었으므로 만들지 않는다.
#
# 끝나도 컨테이너를 남겨 둔다. Boot는 docker compose up --wait로 띄우는데, --wait는 끝난 컨테이너를
# 실패로 본다. 대신 다 돌면 /tmp/done을 만들어 헬스체크를 통과시키므로, 앱은 마이그레이션 뒤에 뜬다.
set -eu

# stop한 컨테이너를 다시 start하면 지난 실행의 /tmp/done이 남아 있다. 지우지 않으면 마이그레이션 전에 헬스체크가 통과한다.
rm -f /tmp/done

for dir in /migrations/*/; do
    context=$(basename "$dir")
    flyway \
        -url=jdbc:postgresql://postgres:5432/contentria_v2 \
        -user="${context}_migrator" \
        -password=local \
        -schemas="$context" \
        -defaultSchema="$context" \
        -createSchemas=false \
        -locations="filesystem:$dir" \
        -failOnMissingLocations=true \
        migrate
done

touch /tmp/done
trap 'exit 0' TERM
sleep infinity &
wait
