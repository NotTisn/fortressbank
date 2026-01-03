# FortressBank: Next-Gen Microservices Banking System

FortressBank is a comprehensive, enterprise-grade banking simulation built on a microservices architecture. It demonstrates advanced concepts in fintech security, AI-powered identity verification, and distributed transaction processing.

## 🌟 Standout Features

### 🧠 AI-Powered eKYC (Electronic Know Your Customer)
The **FBANK-AI** service leverages Computer Vision and Deep Learning to ensure secure user onboarding and transaction verification.
*   **Face Recognition:** Utilizes **InsightFace** and **ONNX Runtime** for high-accuracy face matching.
*   **Liveness Detection:** Anti-spoofing technology to prevent attacks using photos or videos.
*   **Vector Database:** Integration with **Qdrant** for high-speed similarity search of biometric data.
*   **Consistency Check:** Ensures multiple frames captured during a transaction belong to the same person.

### 🛡️ Advanced Security & IAM
*   **Custom Keycloak Extensions:** 
    *   **Single Device Policy:** Enforces one active device per user session (custom Authenticator).
    *   **Device Binding:** Maps unique Device IDs directly into JWT tokens (`DeviceIdProtocolMapper`).
*   **Dynamic Risk Engine:** Real-time transaction scoring based on amount, device fingerprint, and behavioral patterns. Triggers step-up authentication (FaceID/OTP) for high-risk actions.
*   **OAuth2 / OIDC:** Full implementation of OpenID Connect standards using Keycloak and Kong Gateway.

### 💸 Comprehensive Banking Operations
*   **Diverse Transaction Support:**
    *   Internal & Interbank Transfers.
    *   **Bill Payments** & Scheduled Transactions.
    *   **Stripe Integration** for seamless payment processing.
    *   Deposits & Withdrawals.

---

## 🛠 Technology Stack

| Category | Technologies |
| :--- | :--- |
| **Backend (Core)** | Java 21, Spring Boot 3, Spring Cloud (Eureka, Config, Gateway, OpenFeign) |
| **Backend (AI)** | Python 3.10, FastAPI, PyTorch, OpenCV, InsightFace, Qdrant |
| **Database** | PostgreSQL (Data), Redis (Cache/Session), Qdrant (Vector DB) |
| **Security** | Keycloak (IAM), Kong Gateway (API Management), Resilience4j |
| **Messaging** | RabbitMQ (Event-driven Architecture) |
| **Infrastructure** | Docker, Docker Compose |

---

## 🧩 Microservices Landscape

| Service | Responsibility |
| :--- | :--- |
| **fbank-ai** | The "Brain" of the system. Handles biometric data processing, face embeddings, and liveness checks. |
| **transaction-service** | Orchestrates money movement. Handles Internal/External transfers, Bill Payments, and Saga distributed transactions. |
| **account-service** | Manages Account Ledgers and Balances. |
| **user-service** | User profiles and identity data management. |
| **risk-engine** | Evaluates transaction context (Device, Amount, Velocity) to assign risk scores. |
| **notification-service** | Omnichannel alerts (SMS, Email) using 3rd party providers. |
| **keycloak-extensions** | Custom Java providers ensuring banking-grade security policies within Keycloak. |

---

## 🚀 Getting Started

### 1. Prerequisites
*   **Java 21** & **Maven 3.8+**
*   **Python 3.10+** (Optional, for local AI dev)
*   **Docker Desktop** (Required)

### 2. Build Core Services
Package the Java microservices:
```bash
mvn clean package -DskipTests
```

### 3. Launch System
Start the entire stack (including the Python AI service and Databases):
```bash
docker-compose up -d --build
```

### 4. Monitor & Verify
*   **Kong Gateway:** `http://localhost:8000`
*   **Keycloak Admin:** `http://localhost:8888`
*   **Eureka Registry:** `http://localhost:8761`
*   **AI Service Health:** `http://localhost:8000/ai/health` (via Gateway)

## 🔗 Documentation
*   [Database Schema (ERD)](docs/ERD_Diagram_Database.png)
*   [API Specifications](API/)