# FortressBank Dev Mode

> **One command to rule them all.** Start infrastructure + all services with smart logging.

## Quick Start

```powershell
cd infrastructure
.\dev.bat
```

That's it. All databases, Keycloak, Redis, RabbitMQ, Kong, and all Java services start with proper sequencing.

## Commands

### Backend Commands

| Command | What It Does |
|---------|--------------|
| `dev.bat` | Start backend services (infra + Java services) |
| `dev.bat -watch` | Start backend + centralized error watcher |
| `dev.bat -status` | Show status of all services (BE + FE) |
| `dev.bat -logs` | Open logs folder |
| `dev.bat -clearlogs` | Clear logs only (no restart) |
| `dev.bat -kill` | Kill all Java processes |
| `dev.bat -clean` | **Clean all Maven target directories (use after switching branches!)** |
| `dev.bat -infra` | Start infrastructure only (Docker) |
| `dev.bat -infradown` | Stop infrastructure (Docker) |

### Frontend Commands

| Command | What It Does |
|---------|--------------|
| `dev.bat -fe` | Start frontend only (Expo dev server) |
| `dev.bat -feinstall` | Install frontend dependencies (npm install) |
| `dev.bat -fekill` | Kill frontend processes (Node/Expo) |

### Full Stack Commands

| Command | What It Does |
|---------|--------------|
| `dev.bat -full` | Start **everything**: infra + backend + frontend |
| `dev.bat -fullkill` | Kill **everything**: Java + Node processes |

## ⚠️ Branch Switching (IMPORTANT!)

**When switching git branches, always clean first to avoid stale class files:**

```powershell
cd infrastructure
.\dev.bat -kill       # Stop running services
.\dev.bat -clean      # Clean all Maven target directories
git checkout <branch> # Switch branch
.\dev.bat             # Start fresh
```

Stale `.class` files from a previous branch can cause mysterious errors that look like infrastructure problems (e.g., "Redis connection refused" when Redis is actually running).

## Smart Logging

The dev mode uses **cunning logging**:
- Console shows ONLY: startup confirmation + ERROR level logs
- Everything else goes to log files in `infrastructure/logs/`
- Each service gets its own timestamped log file
- Window titles show `[service] OK` or `[service] ERR!`

## Error Watcher

Use `-watch` to open a centralized error window that shows:
- All ERROR level logs from all services
- Stack traces with "Caused by" chains
- Build failures and compilation errors
- Color-coded by service

## Folder Structure

```
infrastructure/
├── dev.bat                     # Main entry point
├── compose-infra-only.yaml     # Docker: just dependencies
├── README.md                   # This file
├── logs/                       # Auto-created log files
│   ├── user-service-2026-01-01_12-00-00.log
│   ├── account-service-2026-01-01_12-00-00.log
│   └── ...
└── scripts/
    ├── dev-mode.ps1            # The brain
    ├── stop_all.bat            # Kill by port
    ├── restart.bat             # Restart single service
    └── clean.bat               # Nuclear reset
```

## Infrastructure Ports

| Service | Port |
|---------|------|
| userdb | 5433 |
| accountdb | 5434 |
| transactiondb | 5435 |
| auditdb | 5436 |
| notificationdb | 5437 |
| Keycloak | 8888 |
| Redis | 6379 |
| RabbitMQ | 5672 (AMQP), 15672 (UI) |
| Kong | 8000 (Proxy), 8001 (Admin) |

## Service Ports

| Service | Port |
|---------|------|
| config-server | 8889 |
| discovery (Eureka) | 8761 |
| user-service | 4000 |
| account-service | 4001 |
| notification-service | 4002 |
| transaction-service | 4004 |
| audit-service | 4005 |
| risk-engine | 4006 |

## Frontend (Mobile App)

The frontend is a React Native + Expo mobile app located at `../fortressbank_fe`.

### Quick Start

```powershell
cd infrastructure
.\dev.bat -fe        # Start Expo dev server in new window
```

Or start the full stack:
```powershell
.\dev.bat -full      # Infra + Backend + Frontend all at once
```

### Frontend Network Configuration

The mobile app connects to Kong Gateway. You must configure `API_CONFIG.BASE_URL` in `fortressbank_fe/src/constants/index.ts` based on your test device:

| Device Type | BASE_URL |
|-------------|----------|
| Android Emulator | `http://10.0.2.2:8000` |
| iOS Simulator | `http://localhost:8000` |
| Physical Device | `http://<your-machine-ip>:8000` |

### Frontend Ports

| Service | Port |
|---------|------|
| Metro Bundler | 8081 |
| Expo DevTools | 19002 |

## Key URLs

- **Kong Gateway**: http://localhost:8000
- **Keycloak Admin**: http://localhost:8888 (admin/admin)
- **Eureka Dashboard**: http://localhost:8761
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

## Deployment Scenarios

FortressBank supports multiple deployment scenarios. All `application.yml` files use `${ENV_VAR:localhost-default}` pattern for maximum flexibility.

| Scenario | Infrastructure | Services | Commands |
|----------|----------------|----------|----------|
| **Full Docker** | Docker | Docker | `docker compose up -d` |
| **Hybrid (Recommended)** | Docker | Maven/IDE | `dev.bat -infra` then `dev.bat` |
| **Raw Maven** | Docker | `mvn spring-boot:run` | Start infra, then run each service |
| **IDE Run** | Docker | IDE Run Config | Same as Raw Maven, use IDE debugger |

### Full Docker Mode
All services run in containers. Uses `docker-compose.yml` and `.env` file.
```powershell
cd fortressbank
docker compose up -d
```

### Hybrid Mode (Recommended for Development)
Infrastructure in Docker, services run locally via Maven for faster reload and debugging.
```powershell
cd infrastructure
.\dev.bat -infra     # Start databases, Keycloak, Redis, RabbitMQ, Kong
.\dev.bat            # Start all Java services
```

### Raw Maven Mode
For debugging a single service without the full dev.bat machinery.
```powershell
cd infrastructure
.\dev.bat -infra     # Start infrastructure
cd ../account-service
.\mvnw spring-boot:run
```

### IDE Run Mode
Same as Raw Maven but use your IDE's run configuration. Services use localhost defaults from `application.yml`.

## Troubleshooting

### Service won't start
1. Check the service window for errors
2. Check the log file in `infrastructure/logs/`
3. Run `dev.bat -status` to see which services are up

### Database connection errors
1. Run `dev.bat -infra` to ensure Docker is running
2. Check `docker ps` to verify containers are healthy
3. Try `docker compose -f compose-infra-only.yaml logs <service>`

### Keycloak takes forever
First boot imports the realm and can take 2-3 minutes. Subsequent boots are faster.

### Out of memory
If running all services locally, ensure you have at least 16GB RAM. Consider running only the services you're working on.

### Port already in use
Run `dev.bat -kill` to stop all Java processes, or use `scripts/stop_all.bat --docker` to also stop Docker.

## Per copilot-instructions.md

This tooling follows:
- §7 TERMINAL DISCIPLINE for build/run commands
- §9 ARCHITECTURE for port assignments
- §ENVIRONMENT_SETUP for credentials and URLs
