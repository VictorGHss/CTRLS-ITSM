# Estágio 1: Dependências e Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests && rm -f target/*-plain.jar

# Estágio 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=America/Sao_Paulo
RUN apk add --no-cache tzdata postgresql-client && \
    mkdir -p /app/logs /app/uploads /app/backups && \
    chown -R 1000:1000 /app
COPY --from=builder --chown=1000:1000 /app/target/*.jar app.jar
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-XX:+UseG1GC", "-XX:+UseStringDeduplication", "-Duser.timezone=America/Sao_Paulo", "-jar", "app.jar"]

