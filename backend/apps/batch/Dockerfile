# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 backend/다. 실행 JAR는 이미지 밖에서 먼저 만든다: ./gradlew :apps:batch:bootJar
ARG JRE_IMAGE=eclipse-temurin:25-jre-noble

# 추출한 레이어는 아키텍처와 무관하므로 빌드 머신의 플랫폼에서 추출해 에뮬레이션을 피한다.
FROM --platform=$BUILDPLATFORM ${JRE_IMAGE} AS extract
WORKDIR /workspace
COPY apps/batch/build/libs/application.jar ./
RUN java -Djarmode=tools -jar application.jar extract --layers --destination /layers

FROM ${JRE_IMAGE} AS runtime
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=extract --chown=10001:10001 /layers/dependencies/ ./
COPY --from=extract --chown=10001:10001 /layers/spring-boot-loader/ ./
COPY --from=extract --chown=10001:10001 /layers/snapshot-dependencies/ ./
COPY --from=extract --chown=10001:10001 /layers/application/ ./
USER 10001:10001
ENTRYPOINT ["java", "-jar", "application.jar"]
