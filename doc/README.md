# FortressBank: Microservices Banking System

FortressBank is a modern microservices-based banking application designed for scalability, security, and high performance.

## 🚀 Getting Started

Follow these steps to build and run the entire system using Docker.

### 1. Prerequisites

- **Java 21**
- **Maven 3.8+**
- **Docker & Docker Compose**

### 2. Build the Services

First, package the Java microservices using Maven:

```bash
mvn clean package -DskipTests
mvn clean install -DskipTests
```

### 3. Run the System

Launch all components (Microservices, Databases, Redis, Keycloak, and Kong Gateway) using Docker Compose:

```bash
docker-compose up -d --build
```

### 4. Monitor Status

Check the status of your containers:

```bash
docker-compose ps
```

Monitor system logs:

```bash
docker-compose logs -f
```

## 🛠 System Architecture

The system consists of the following core components:

- **API Gateway (Kong):** Single entry point for all requests.
- **Identity Provider (Keycloak):** Identity and access management (OIDC).
- **Service Discovery (Eureka):** Dynamic service registration and discovery.
- **Config Server:** Centralized configuration management.
- **Core Services:** Account Service, User Service, Transaction Service, Risk Engine, Notification Service, Audit Service.

## 🔗 Important URLs

- **Kong Gateway:** `http://localhost:8000`
- **Eureka Server:** `http://localhost:8761`
- **Keycloak Admin:** `http://localhost:8888`
- **Config Server:** `http://localhost:8889`
