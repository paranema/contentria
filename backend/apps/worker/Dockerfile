# syntax=docker/dockerfile:1
# 빌드 컨텍스트는 backend/다. 호스트 JDK와 Gradle 설치 없이 빌드한다.
ARG JDK_IMAGE=eclipse-temurin:25-jdk-noble
ARG JRE_IMAGE=eclipse-temurin:25-jre-noble
FROM ${JDK_IMAGE} AS build
WORKDIR /workspace
COPY gradlew gradle.properties settings.gradle.kts ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
COPY shared-kernel/ shared-kernel/
COPY platform/ platform/
COPY contexts/ contexts/
COPY apps/ apps/
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    sh ./gradlew --no-daemon --build-cache --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx1g -Pkotlin.compiler.execution.strategy=in-process \
    :apps:worker:bootJar
RUN cp apps/worker/build/libs/worker-*.jar /tmp/application.jar \
    && java -Djarmode=tools -jar /tmp/application.jar extract --layers --destination /layers

FROM ${JRE_IMAGE} AS runtime
# ffmpeg는 libx264, AAC, HLS/fMP4 지원을 포함한다. tini는 자식 프로세스에도 종료 신호를 보낸다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg tini \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=10001:10001 /layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/application/ ./
USER 10001:10001
ENTRYPOINT ["/usr/bin/tini", "-g", "--", "java", "-jar", "application.jar"]
