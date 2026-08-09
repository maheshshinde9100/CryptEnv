FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Use shell form so environment variables are expanded at runtime
ENTRYPOINT ["sh", "-c", "exec java \
  -Dspring.datasource.url=\"$DB_URL\" \
  -Dspring.datasource.username=\"$DB_USER\" \
  -Dspring.datasource.password=\"$DB_PASS\" \
  -Djwt.secret=\"$JWT_SECRET\" \
  -Djwt.expiration=\"${JWT_EXPIRATION:-86400000}\" \
  -Dencryption.master.key=\"$MASTER_ENCRYPTION_KEY\" \
  -Dcryptenv.master-key=\"$MASTER_ENCRYPTION_KEY\" \
  -Dcryptenv.cors.allowed-origins=\"${CORS_ALLOWED_ORIGINS:-http://localhost:3000}\" \
  -Dserver.port=\"${PORT:-8080}\" \
  -jar app.jar"]
