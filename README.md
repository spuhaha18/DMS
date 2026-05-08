# DMS — Pharmaceutical R&D EDMS

GxP-compliant electronic document management system. See **[CONTEXT.md](./CONTEXT.md)** for project entry point, tech stack, repository map, and getting-started guide.

## Quick start (development)

```bash
# 1. Start infra (PostgreSQL + MinIO)
cd infra && docker compose up -d

# 2. Run backend
cd backend && ./gradlew bootRun

# 3. Run frontend
cd frontend && npm install && npm run dev
```

## Documentation

- `CONTEXT.md` — project overview and document map
- `validation/` — 31 GxP validation documents (URS, FS, DS, SOPs, etc.)
- `docs/superpowers/plans/` — implementation plans
