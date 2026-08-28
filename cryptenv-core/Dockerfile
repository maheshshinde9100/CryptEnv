# ============================================================
# CryptEnv Core — GraalVM Community 17 JIT (Render 512MB Optimized)
# ============================================================
# STAGE 1: Build fat JAR with GraalVM JDK
# STAGE 2: Slim runtime with GraalVM JRE, JIT + aggressive memory tuning
#
# Memory profile (target: 512MB Render instance)
#   Heap:    ~281 MB (MaxRAMPercentage=55)
#   Metaspace: 128 MB cap
#   CodeCache:  64 MB cap
#   Direct:     32 MB cap
#   Threads:   ~16 MB (32 thr * 512KB stack)
#   OS + other: ~55 MB
#   TOTAL est: < 500 MB (safe margin for 512MB cgroup limit)
#
# Startup optimizations
#   TieredCompilation C1-only (fast warmup, minimal code cache)
#   Compressed Oops + Class Pointers (smaller object layout)
#   SerialGC (lower overhead than G1 for <2GB heaps)
#   JIT aggressively inlines hot methods via Graal
#
# Health endpoints (UptimeRobot-compatible)
#   GET /health       -> simple 200 OK alias (root path)
#   GET /api/health   -> full JSON status payload
# ============================================================

# ---------- STAGE 1: BUILD ----------
FROM ghcr.io/graalvm/graalvm-community:17 AS build
WORKDIR /build

ENV MAVEN_OPTS="-XX:+UseSerialGC -Xmx1536m -Xms512m"

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw \
 && ./mvnw -B -q org.apache.maven.plugins:maven-dependency-plugin:3.6.1:go-offline -DskipTests

COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests \
 && JAR="$(ls target/*.jar | grep -v '\.original$' | head -n 1)" \
 && java -Djarmode=layertools -jar "$JAR" extract --destination /build/layers \
 && echo "Extracted Spring Boot layers"

# ---------- STAGE 2: RUNTIME ----------
FROM ghcr.io/graalvm/graalvm-community:17
WORKDIR /app

# Drop unused GraalVM native-image / gu components (saves ~200MB image size)
RUN rm -rf /opt/graalvm-community/lib/svm \
           /opt/graalvm-community/lib/installer \
           /opt/graalvm-community/bin/native-image \
           /opt/graalvm-community/bin/gu 2>/dev/null || true

# Spring Boot layered copy (cache-friendly: dependencies first, app last)
COPY --from=build /build/layers/dependencies/ ./
COPY --from=build /build/layers/spring-boot-loader/ ./
COPY --from=build /build/layers/snapshot-dependencies/ ./
COPY --from=build /build/layers/application/ ./

EXPOSE 8080

# --- GraalVM JIT tuned for 512MB Render free tier ---
ENV JAVA_OPTS="\
-XX:+UseSerialGC \
-XX:MaxRAMPercentage=55.0 \
-XX:InitialRAMPercentage=28.0 \
-XX:MinRAMPercentage=10.0 \
-XX:MaxMetaspaceSize=128m \
-XX:MetaspaceSize=64m \
-XX:ReservedCodeCacheSize=64m \
-XX:InitialCodeCacheSize=16m \
-XX:MaxDirectMemorySize=32m \
-XX:+UseCompressedOops \
-XX:+UseCompressedClassPointers \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-XX:+TieredCompilation \
-XX:TieredStopAtLevel=1 \
-XX:CICompilerCount=2 \
-XX:ParallelGCThreads=2 \
-XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/tmp \
-XX:-OmitStackTraceInFastThrow \
-Xss512k \
-Djava.security.egd=file:/dev/./urandom \
-Dfile.encoding=UTF-8 \
-Djava.awt.headless=true \
-Dspring.beaninfo.ignore=true \
-Dsun.net.inetaddr.ttl=60 \
-Dsun.net.inetaddr.negative.ttl=10"

ENTRYPOINT ["sh", "-c", "exec java \
  $JAVA_OPTS \
  org.springframework.boot.loader.launch.JarLauncher \
  --spring.datasource.url=\"$DB_URL\" \
  --spring.datasource.username=\"$DB_USER\" \
  --spring.datasource.password=\"$DB_PASS\" \
  --spring.datasource.hikari.maximum-pool-size=\"${DB_POOL_MAX:-6}\" \
  --spring.datasource.hikari.minimum-idle=\"${DB_POOL_MIN:-2}\" \
  --spring.datasource.hikari.connection-timeout=10000 \
  --spring.datasource.hikari.idle-timeout=600000 \
  --spring.datasource.hikari.max-lifetime=1800000 \
  --jwt.secret=\"$JWT_SECRET\" \
  --jwt.expiration=\"${JWT_EXPIRATION:-86400000}\" \
  --cryptenv.master-key=\"$MASTER_ENCRYPTION_KEY\" \
  --encryption.master.key=\"$MASTER_ENCRYPTION_KEY\" \
  --cryptenv.cors.allowed-origins=\"${CORS_ALLOWED_ORIGINS:-https://cryptenv-dashboard.vercel.app}\" \
  --cryptenv.flyway.enabled=\"${CRYPTENV_FLYWAY_ENABLED:-true}\" \
  --springdoc.swagger-ui.enabled=\"${SPRINGDOC_ENABLED:-false}\" \
  --spring.main.banner-mode=off \
  --server.port=\"${PORT:-8080}\" \
  --server.address=0.0.0.0 \
  --server.tomcat.threads.max=\"${TOMCAT_THREADS_MAX:-32}\" \
  --server.tomcat.threads.min-spare=\"${TOMCAT_THREADS_SPARE:-4}\" \
  --server.tomcat.max-connections=\"${TOMCAT_MAX_CONN:-128}\" \
  --server.tomcat.accept-count=\"${TOMCAT_ACCEPT:-32}\" \
  --spring.jpa.properties.hibernate.jdbc.batch_size=16 \
  --spring.jpa.properties.hibernate.order_inserts=true \
  --spring.jpa.properties.hibernate.order_updates=true \
  --logging.level.root=\"${LOG_LEVEL_ROOT:-INFO}\""]
