# Stage 1: Build stage
FROM maven:3.9.5-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy parent POM
COPY pom.xml ./

# Copy all modules (required by Maven multi-module build)
COPY shared-kernel ./shared-kernel
COPY user-service ./user-service
COPY account-service ./account-service
COPY notification-service ./notification-service
COPY transaction-service ./transaction-service
COPY config-server ./config-server
COPY risk-engine ./risk-engine
COPY discovery ./discovery
COPY reference-service ./reference-service
COPY audit-service ./audit-service
COPY keycloak-extensions ./keycloak-extensions

# Copy backup-service source
COPY backup-service/pom.xml ./backup-service/
COPY backup-service/src ./backup-service/src

# Build the application
RUN mvn clean package -pl backup-service -am -DskipTests

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install docker CLI and postgresql-client for pg_dump/pg_restore commands
RUN apk add --no-cache docker-cli postgresql-client

WORKDIR /app

# Create backup directory
RUN mkdir -p /app/backups && chmod 777 /app/backups

# Copy the built JAR from build stage
COPY --from=build /app/backup-service/target/*.jar app.jar

# Expose port
EXPOSE 4006

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:4006/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=docker"]
