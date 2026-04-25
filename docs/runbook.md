# DMS Phase 1 — Operations Runbook

## Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│  Docker Compose                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐   │
│  │ Next.js  │  │ FastAPI  │  │   Postgres 16    │   │
│  │ :3000    │→ │ :8000    │→ │   :5432          │   │
│  └──────────┘  └────┬─────┘  └──────────────────┘   │
│                     │                                │
│                     ↓                                │
│               ┌──────────┐                           │
│               │  MinIO   │                           │
│               │  :9000   │                           │
│               └──────────┘                           │
└─────────────────────────────────────────────────────┘
```

Buckets: `dms-templates`, `dms-sources`, `dms-final`

---

## Daily Backup

### Postgres

```bash
# Daily at 02:00 KST — cron: 0 17 * * * (UTC)
docker compose exec postgres pg_dump -U dms dms | gzip > /backups/dms-$(date +%Y%m%d).sql.gz

# Retain 30 days
find /backups -name "dms-*.sql.gz" -mtime +30 -delete
```

### MinIO

```bash
# Mirror all buckets to backup location daily at 03:00 KST
mc mirror minio/dms-templates /backups/minio/dms-templates/
mc mirror minio/dms-sources   /backups/minio/dms-sources/
mc mirror minio/dms-final     /backups/minio/dms-final/
```

---

## Health Checks

```bash
# Backend
curl -f http://localhost:8000/health || alert "Backend down"

# Postgres
docker compose exec postgres pg_isready -U dms || alert "Postgres down"

# MinIO
curl -f http://localhost:9000/minio/health/live || alert "MinIO down"
```

---

## AD/LDAP Sync Monitoring

The LDAP sync runs at login time (lazy sync). No scheduled job in Phase 1.

Monitor authentication failures:
```sql
SELECT actor, count(*) as fails, max(ts) as last_fail
FROM audit_logs
WHERE action = 'LOGIN_FAIL'
  AND ts > now() - interval '24 hours'
GROUP BY actor
ORDER BY fails DESC
LIMIT 20;
```

---

## Numbering Conflict Check

```sql
-- Should always return 0 in healthy operation
SELECT count(*)
FROM audit_logs
WHERE action = 'NUMBERING_CONFLICT';
```

---

## Audit Log Integrity

Verify audit_logs is append-only (no UPDATE/DELETE):
```sql
SELECT count(*) FROM audit_logs;  -- record count
-- Run same query next day; count must be >= previous count
```

---

## Incident Response

### Backend Unresponsive

1. `docker compose logs backend --tail 100`
2. Check Postgres connection: `docker compose exec postgres pg_isready -U dms`
3. Restart: `docker compose restart backend`
4. Check logs again after restart

### Database Corruption / Recovery

1. Stop all services: `docker compose stop backend frontend`
2. Restore from backup:
   ```bash
   gunzip -c /backups/dms-YYYYMMDD.sql.gz | docker compose exec -T postgres psql -U dms dms
   ```
3. Restart services: `docker compose start backend frontend`
4. Verify document count matches expected

### MinIO Unreachable

1. Check MinIO logs: `docker compose logs minio --tail 50`
2. Restart: `docker compose restart minio`
3. Re-verify buckets: `mc ls minio/`

---

## Key Environment Variables

| Variable | Purpose | Example |
|---|---|---|
| `DATABASE_URL` | Postgres connection | `postgresql://dms:pass@postgres/dms` |
| `MINIO_ENDPOINT` | MinIO host:port | `minio:9000` |
| `MINIO_ACCESS_KEY` | MinIO access key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret | `minioadmin` |
| `LDAP_URL` | AD server | `ldap://ad.example.com` |
| `LDAP_BASE_DN` | Search base | `DC=example,DC=com` |
| `SECRET_KEY` | JWT signing key | 32+ random chars |
| `SMTP_HOST` | Mail relay | `smtp.example.com` |

---

## Deployment (update)

```bash
git pull origin main
docker compose build backend frontend
docker compose exec backend alembic upgrade head
docker compose up -d backend frontend
docker compose logs -f backend --tail 30
```

---

## Useful Queries

```sql
-- Document status summary
SELECT effective_status, count(*) FROM documents GROUP BY 1 ORDER BY 2 DESC;

-- Approval pipeline bottlenecks (avg lead time in hours)
SELECT
  date_trunc('day', a.ts) AS day,
  avg(extract(epoch from (b.ts - a.ts))/3600)::int AS avg_hours
FROM audit_logs a
JOIN audit_logs b ON b.target = a.target AND b.action = 'DOC_FINALIZED'
WHERE a.action = 'DOC_SUBMIT'
GROUP BY 1 ORDER BY 1;

-- Recent print audit
SELECT actor, target, payload_json->>'reason' AS reason, ts
FROM audit_logs
WHERE action = 'PRINT'
ORDER BY ts DESC
LIMIT 20;
```
