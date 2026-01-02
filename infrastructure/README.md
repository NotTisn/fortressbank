# FortressBank Dev Mode

> **One command to rule them all.** Start infrastructure + all services with smart logging.

## Quick Start

```powershell
cd infrastructure
.\dev.bat
```

That's it. All databases, Keycloak, Redis, RabbitMQ, Kong, and all Java services start with proper sequencing.

## Commands

| Command | What It Does |
|---------|--------------|
| `dev.bat` | Start everything (infra + services) |
| `dev.bat -watch` | Start everything + centralized error watcher |
| `dev.bat -status` | Show status of all services |
| `dev.bat -logs` | Open logs folder |
| `dev.bat -kill` | Kill all Java processes |
| `dev.bat -infra` | Start infrastructure only (Docker) |
| `dev.bat -infradown` | Stop infrastructure (Docker) |
| `dev.bat -clean` | Clear logs and restart |

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

## Key URLs

- **Kong Gateway**: http://localhost:8000
- **Keycloak Admin**: http://localhost:8888 (admin/admin)
- **Eureka Dashboard**: http://localhost:8761
- **RabbitMQ Management**: http://localhost:15672 (guest/guest)

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
