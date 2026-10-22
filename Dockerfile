# syntax=docker/dockerfile:1
# One image definition for every service. Build the jars first, then for example:
#   ./mvnw -DskipTests package
#   docker build --build-arg SERVICE=request-service -t request-portal/request-service .
ARG JAVA_IMAGE=eclipse-temurin:25.0.4.1_1-jre-noble

FROM ${JAVA_IMAGE} AS extract
ARG SERVICE
WORKDIR /build
COPY services/${SERVICE}/target/${SERVICE}-*.jar app.jar
# Split the jar into layers that change at different rates (libraries rarely, our classes often), so
# rebuilding after a code change only replaces the small top layer.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM ${JAVA_IMAGE}
ARG SERVICE
LABEL org.opencontainers.image.source="https://github.com/Kazt2407/request-portal" \
      org.opencontainers.image.title="request-portal ${SERVICE}"
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER 10001
# Size the heap from the container's memory limit and restart cleanly instead of limping on after an OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
