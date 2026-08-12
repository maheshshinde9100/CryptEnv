# ============================================================
# CryptEnv Core — GraalVM Community 17 JIT for Render.com
# ============================================================
# GraalVM JIT improves steady-state throughput/latency vs HotSpot
# while remaining fully compatible with Spring Boot / JPA / Flyway.
# Native-image is optional and NOT used by default on Render.
# ============================================================

FROM ghcr.io/graalvm/graalvm-community:17 AS build
WORKDIR /app

ENV MAVEN_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=70.0"

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline -DskipTests

COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests \
 && JAR="$(ls target/*.jar | grep -v '\.original$' | head -n 1)" \
 && cp "$JAR" /app/app.jar

FROM ghcr.io/graalvm/graalvm-community:17
WORKDIR /app
COPY --from=build /app/app.jar /app/app.jar
EXPOSE 8080

# GraalVM JIT + G1 GC tuned for Render container memory limits
ENV JAVA_OPTS="\
-XX:+UseG1GC \
-XX:MaxGCPauseMillis=100 \
-XX:+UseStringDeduplication \
-XX:MaxRAMPercentage=75.0 \
-XX:InitialRAMPercentage=25.0 \
-XX:+ExitOnOutOfMemoryError \
-XX:+AlwaysPreTouch \
-Djava.security.egd=file:/dev/./urandom \
-Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "exec java \
  $JAVA_OPTS \
  -Dspring.datasource.url=\"$DB_URL\" \
  -Dspring.datasource.username=\"$DB_USER\" \
  -Dspring.datasource.password=\"$DB_PASS\" \
  -Djwt.secret=\"$JWT_SECRET\" \
  -Djwt.expiration=\"${JWT_EXPIRATION:-86400000}\" \
  -Dcryptenv.master-key=\"$MASTER_ENCRYPTION_KEY\" \
  -Dencryption.master.key=\"$MASTER_ENCRYPTION_KEY\" \
  -Dcryptenv.cors.allowed-origins=\"${CORS_ALLOWED_ORIGINS:-http://localhost:3000}\" \
  -Dcryptenv.flyway.enabled=\"${CRYPTENV_FLYWAY_ENABLED:-true}\" \
  -Dspringdoc.swagger-ui.enabled=\"${SPRINGDOC_ENABLED:-false}\" \
  -Dserver.port=\"${PORT:-8080}\" \
  -Dserver.address=0.0.0.0 \
  -jar /app/app.jar"]
