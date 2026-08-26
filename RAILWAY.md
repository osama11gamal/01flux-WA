# Deploying 01flux WA on Railway

The repository is Railway-ready: the root `Dockerfile` (two-stage, node:22-slim, bundled Chromium
for the whatsapp-web.js engine) is auto-detected via `railway.json`, which also wires the
readiness healthcheck (`/api/health/ready`) and an ON_FAILURE restart policy.

## Steps

1. **New Project → Deploy from GitHub repo** → pick `osama11gamal/01flux-WA`.
   Railway builds the Dockerfile automatically; no start command needed (image ENTRYPOINT is used).
2. **Attach a Volume mounted at `/app/data`** (Service → Volumes).
   All persistent state lives there: SQLite databases, WhatsApp sessions, media, plugins,
   `data/.env.generated` (dashboard-saved config) and `data/.api-key`. Without a volume every
   redeploy wipes sessions and settings.
3. **Variables** — nothing is strictly required for the default single-node SQLite mode
   (`PORT` is injected by Railway and honoured by the app). Recommended set:

   | Variable | Why |
   |---|---|
   | `NODE_ENV=production` | enables the production security posture |
   | `AUTO_START_SESSIONS=true` | re-link previously authenticated sessions on boot |
   | `API_MASTER_KEY=<32+ chars>` | pin a known admin key instead of reading it from the volume |
   | `ENGINE_TYPE=baileys` | optional: no-browser engine (smaller footprint, no Chromium) |

4. First boot seeds an admin API key. Retrieve it from the volume file `data/.api-key`
   (Railway volume file browser / shell) or set `API_MASTER_KEY` yourself in step 3.
5. Networking: expose the HTTP port; the app listens on `$PORT`. The dashboard is served from
   the same origin at `/`, Swagger at `/api/docs`.

## Multi-instance notes

- One replica only (`numReplicas: 1`): SQLite + in-process sessions are single-node by design.
- For Postgres/Redis/MinIO, use managed Railway plugins and set the corresponding
  `DATABASE_TYPE=postgres` / `REDIS_ENABLED=true` variables — see `.env.example`
  (the single source of truth for every knob).

## Local smoke before pushing

The image is the same one used by `docker-compose.dev.yml`; CI gates Dockerfile/patcher parity
via `npm run test:scripts`.
