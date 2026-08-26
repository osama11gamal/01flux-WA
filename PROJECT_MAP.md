# PROJECT_MAP.md — 01flux WA Codebase Map

> Open Source WhatsApp API Gateway — Free, Self-Hosted HTTP API for WhatsApp (v0.23.3, MIT).
> Single NestJS process serving: REST API + Socket.IO events + bundled Dashboard SPA + MCP server.
>
> Generated: 2026-08-26 · Scope: full-tree read of `src/`, `dashboard/`, `sdk/`, `docs/`, `scripts/`, `charts/`, `test/`, deployment files.

---

## [TECH_STACK]

### Backend (root `package.json`)
| Concern | Technology |
|---|---|
| Runtime | Node.js ≥ 22.13 (`.nvmrc`), TypeScript ~6.0 |
| Framework | NestJS 11 (`@nestjs/*`), Express adapter |
| ORM | TypeORM 1.x, **dual connections**: `'main'` (always SQLite) + `'data'` (SQLite or Postgres 16) |
| Realtime | Socket.IO 4 (+ `@socket.io/redis-adapter` for multi-replica) |
| Queues | BullMQ 6 (opt-in `QUEUE_ENABLED=true`) + Bull Board UI at `/api/admin/queues` |
| Cache / rate-limit store | Redis via ioredis 6 (opt-in; in-memory fallback) |
| Validation | class-validator + class-transformer (hand-rolled env validation — **no joi**) |
| API docs | @nestjs/swagger → committed `openapi.json` (CI-enforced parity) |
| Engines | `whatsapp-web.js` 1.34.7 (Puppeteer 24 / Chrome for Testing 146) **and** `@whiskeysockets/baileys` 7.0.0-rc14 |
| Media | sharp (images), ffmpeg (audio/video conversion, opt-in), audio-decode |
| Storage | local filesystem (default) or S3/MinIO (`@aws-sdk/client-s3`) |
| Security | helmet, @nestjs/throttler (+ custom Redis storage), custom SSRF guard (undici), API-key auth w/ optional HMAC pepper |
| AI agents | `@modelcontextprotocol/sdk` (MCP server at `/mcp`, opt-in) |
| Native dep note | `better-sqlite3`: **v13 ships no prebuilt Windows binaries** — on machines without MSVC use v12.11.1 prebuilt (see ORPHANS & PENDING #1) |

### Frontend (`dashboard/`)
React 19 + TypeScript + Vite 8 · React Router 7 · TanStack Query 5 + Table · socket.io-client · i18next (13 locales incl. RTL ar/he) · recharts · lucide-react.
Dev server port **2886**, proxies `/api` + `/socket.io` → `http://localhost:2785`. Build output `dashboard/dist/` is served by the same Nest process (ServeStaticModule) when present.

### SDKs (`sdk/` — hand-written, not generated)
`javascript/TS` (@rmyndharis/01flux-wa, ESM+CJS) · `python` (httpx) · `php` (Guzzle 7) · `java` (java.net.http+Gson) · `go` (stdlib only, only SDK with opt-in retry). Wire types isolated per-language for future OpenAPI codegen.

### Testing
Jest unit lane (`npm test`, coverage thresholds per directory) · Jest e2e lane (`test/`, ~24 scenario suites, jest moduleNameMapper mocks) · `node --test` lane for scripts + dashboard · doc/contract "meta-gate" specs run via `test:docs`.

### Deployment
Docker two-stage digest-pinned `node:22-slim`, non-root via gosu entrypoint, arch-split Chromium · `docker-compose.yml` profile-based: always `docker-proxy` (socket-proxy) + `01flux-wa-api`; optional profiles `postgres` / `redis` / `minio` (or orchestrated at runtime through Docker API from the dashboard) · Helm chart `charts/01flux-wa/` (StatefulSet, single-instance by design).

---

## [SYSTEM_FLOW]

### Boot sequence (`src/main.ts` → `dist/main.js`)
1. `import './config/load-env'` FIRST — env precedence: **process env > `.env` > `data/.env.generated`** (dashboard-saved); blank host vars are cleared so lower layers win (~150 composed keys); secret files tightened to 0600.
2. Sync bootstrap guards: LOG_LEVEL, unhandled-rejection monitor, NODE_ENV advisory, production secret guard (`assertNoDefaultSecretsInProduction`), storage-root writability probe.
3. `NestFactory.create(AppModule)` — full DI graph init: ConfigModule → TypeORM `main` conn → TypeORM `data` conn (+ Postgres advisory-lock migrations when Postgres) → ThrottlerModule → Hooks → Plugins → conditional Queue/Search/MCP/ServeStatic modules → feature modules. During init: `AuthService.onModuleInit` seeds admin key if table empty (writes `data/.api-key`); `PluginLoaderService` restores enabled plugins.
4. Post-create wiring: Redis WS adapter (if enabled), body caps + helmet/CSP-nonce/CORS, shutdown hooks (SIGTERM/SIGINT drain via ShutdownService), global ValidationPipe under `/api`, Swagger (non-prod default), Bull-Board auth middleware, HTTP timeouts, `app.listen(PORT=2785)`.
5. Any bootstrap failure → `runBootstrapOrExit` (src/config/bootstrap-fatal.ts): log → bounded 5s `app.close()` → `exit(1)` (prevents zombie holding live sessions after EADDRINUSE).

### Inbound request flow
```
client → http.Server (requestTimeout caps)
       → inflight-body-budget middleware (503+Retry-After admission control)
       → request-context middleware (X-Request-ID, AsyncLocalStorage)
       → helmet / CSP nonce / CORS
       → ThrottlerGuard (proxy-aware, Redis-backed when enabled)
       → ApiKeyGuard (hash lookup in 'main' DB; roles, IP/CIDR, session scope narrowing;
                      @Public routes skip: health, metrics(bearer), ingress, auth/validate…)
       → controller (DTO whitelist+forbidNonWhitelisted+transform)
       → service → SessionService/EngineRegistry → engine adapter
       ← response; audit written to 'main'; Prometheus metrics recorded
```

### Message send flow
`POST /api/sessions/:id/messages/send-*` → MessageSendService → SendPacingService (anti-ban pacing/warmup/circuit breaker) → HookManager emit `message:sending` (**plugins may veto**) → `IWhatsAppEngine.send*()` → adapter normalizes JIDs (neutral dialect `<phone>@c.us` / `@g.us` / `@lid`) → result persisted to `messages` ('data' DB) → acks tracked (`message:ack`) → webhook dispatch.

### Inbound event flow (engine → consumers)
Adapter event → `EngineRegistry.isLive()` identity guard (superseded engines can't mutate) → SessionService event wiring → MessageProjector persists → fan-out:
1. **Socket.IO** EventsGateway (WS rate-limited; Redis adapter fans out across replicas),
2. **Webhooks**: filter evaluation → **transactional outbox** (`webhook_outbox_events`) → direct dispatcher or BullMQ queue → signed delivery (HMAC) → retries → delivery-failure records,
3. **Hooks bus** (`core/hooks`, 20 typed events) → plugin subscribers (sandboxed worker threads),
4. **Automation rules** evaluated on inbound dispatch (may autoreply).

### Outbound provider-webhook flow (Integration Fabric)
External provider (e.g. Chatwoot) → `POST /ingress/:pluginId/:instanceId/*` (@Public, signature-verified, fast-ACK) → `ingress_events` row + ordering lock → BullMQ/inline processor → ConversationMapping resolve → conversation-send-facade → engine send. Redrive endpoint replays failures; retention sweeps old events.

### Session lifecycle
create → start → EngineFactory.create (plugin-resolved adapter; path-traversal-guarded name; owner-only cred dirs) → QR / pairing-code → `ready` (readiness marker + hasSynced check) → live (watchdog monitors liveness; heartbeat lease ownership for multi-node; TakeoverService adopts lapsed peers) → stop/logout/delete (delete purges BOTH engines' credential dirs).

### Config mutation flow (dashboard ↔ runtime)
Dashboard PUT `/api/infra/config` → guarded by "OS-provided/pinned keys are immutable" snapshots (env-precedence) → validated → written to `data/.env.generated` (0600) → takes effect on next restart (or managed-service restart via InfraController).

---

## [ARCHITECTURE]

### Layered layout of `src/`
```
src/
├── main.ts               entrypoint (boot order above)
├── app.module.ts         root module; dual TypeORM conns; conditional Queue/Search/MCP/ServeStatic
├── configure-app.ts      shared HTTP surface config (prod + e2e use the SAME stack)
├── config/               configuration.ts (all knobs), load-env, env.validation (fail-fast),
│                         bootstrap-security/-fatal, swagger.config, feature-flags,
│                         inflight-body-budget, http-timeouts, storage-root, dashboard-csp …
├── common/               cross-cutting: security/ (ssrf-guard, api-key helpers, throttler),
│                         services/ (logger, shutdown, request-context), cache/, storage/
│                         (local+S3, orphan sweep), utils/ (secret-file, path-safety, paginate…),
│                         errors/ (typed domain errors→4xx/5xx), media/, metrics/,
│                         middleware/, interceptors/, throttler/, transformers/, openapi/
├── core/                 extension runtime:
│   ├── hooks/            central event bus — 20 exhaustively-typed events, priority order,
│   │                     AsyncLocalStorage re-entry short-circuit, veto-capable sending gate
│   ├── plugins/          sandboxed plugin system — worker_threads (256MB heap, 32-inflight,
│   │                     30s timeouts), manifest validation, SSRF net.fetch allowlist,
│   │                     PluginTypes: ENGINE/STORAGE/QUEUE/AUTH/EXTENSION
│   └── agent-tools/      protocol-neutral AI tool registry (session/message/contact/group/
│                         label/webhook/automation tools) — sole consumer: MCP server
├── database/             migrations ('data': 31 files + drift fixture; 'main': 1),
│                         pg-boot-migrations (advisory lock), CLI data-sources, sqlite perms boot
├── engine/               anti-corruption layer over WhatsApp libs:
│   ├── interfaces/whatsapp-engine.interface.ts   IWhatsAppEngine contract + neutral JID dialect
│   ├── adapters/         whatsapp-web-js.adapter.ts (19 partials) + baileys.adapter.ts
│   │                     (12 partials) — parity-gate specs force identical method inventories
│   ├── builtin/          WhatsAppWebJsPlugin + BaileysPlugin (manifest.json each)
│   ├── engine.factory.ts plugin-mediated creation; purgeSessionData on delete only
│   ├── engine-registry.service.ts  live-engine Map + isLive/deleteIfLive identity guards
│   └── identity/         wa-id dialect helpers, lid_mappings entity + LID↔phone cache/store
└── modules/              31 feature modules (table below)
```

### Module inventory (31)
| Domain | Modules |
|---|---|
| Access | auth (API keys/roles/scopes), audit, health, settings (controller-only) |
| Sessions | session (lifecycle, leases, watchdog, chat ops), takeover, status-store, chat-media |
| Messaging | message (send/bulk/pacing/reaper), template, automation, catalog, media (ffmpeg) |
| Chats | contact, group, label, channel, profile, call, search (FTS provider registry) |
| Integration | webhook (outbox pattern), integration (provider ingress fabric), plugins API, queue (BullMQ processors), mcp |
| Ops | infra (config/data/storage/status console), docker (managed postgres/redis/minio via socket-proxy), stats, metrics, events (Socket.IO gateway) |

### Data topology
- **`'main'` connection — always SQLite** `./data/main.sqlite`: `api_keys`, `audit_logs`. Never holds business data.
- **`'data'` connection — SQLite default / Postgres optional**: 15 entities — sessions, messages, message_batches, webhooks(+outbox+delivery_failures), templates, automation_rules, status_updates, plugin_instances, ingress_events, integration_delivery_failures, conversation_mappings, baileys_stored_messages, lid_mappings.
- Files under `./data/`: both DBs, `.env.generated`, `.api-key`, `sessions/<engine>/`, `media/`, `plugins/`.
- Multi-node: NODE_ID + ownership leases in `sessions` table; Redis required for shared throttle/queue/cache/WS fan-out.

### Cross-cutting contracts worth knowing
- Route fences: everything meaningful under global prefix `/api`; `@Public()` set mirrored into swagger config by spec-gates.
- "Meta-gate" spec culture: many specs assert repo shape (CHANGELOG format, CI privileges, docs coverage, migration drift fixture, engine parity, SDK route coverage vs `openapi.json`). Breaking conventions fails tests, not just behavior.
- Upstream patchers: 8 scripts patch whatsapp-web.js/baileys post-install (newsletter previews, status repair, ready-sync, participant arity, block/unblock, appstate bound, newsletter-create parse) — applied by postinstall hook locally and explicitly in Dockerfile.

---

## [ORPHANS & PENDING]

Tracking for gaps, dead weight, and local-machine caveats discovered during mapping. Verify before acting; some items are deliberate design (noted as such).

### Orphans / vestigial
1. **`data/main.sqlite.corrupt`** — quarantined corrupt DB from a local incident (2026-08-26). Safe to delete once confirmed no longer needed.
2. **Local `.env`** — copied from `.env.minimal` during setup (SQLite dev mode). Not in git; keep out of commits.
3. **`dashboard/package.json` version `0.10.2` is vestigial** — UI version comes from root package.json via vite define (`__APP_VERSION__`). Cosmetic drift only.
4. **`src/database/migrations/.gitkeep`** — leftover; dir now holds 31 migrations.
5. **`src/engine/engine-capability-matrix.ts`** — imported only by parity/docs specs; ships in `src/` though it's effectively a test fixture. Intentional (drift detection) but misplaced-looking.
6. **Settings module notifications block** (`settings.controller.ts`: emailEnabled/notificationEmail/webhookAlerts) — hardcoded defaults, no backing service; reads as stub surface awaiting implementation or removal.
7. **Compiled `dist/` present at repo root** — build artifact from local runs; not part of source.

### Pending / known gaps (documented in-repo, not yet closed)
8. **SDK route gate blind spot**: `scripts/check-sdk-routes.mjs` cannot parse Go & Java SDKs (paths built by concatenation, no literals) — documented gap; existence-only check even where it does scan; `check-contract-shapes.mjs` partially compensates.
9. **Phantom-stub retirement in progress (whatsapp-web.js adapter)**: historical null/empty returns for unsupported capabilities being replaced with honest 501 `EngineNotSupportedError`; capability matrix exists to find stragglers — a few Status-API spots still marked "limited support" (`whatsapp-web-js.adapter.ts` ~line 832).
10. **MCP route absent from openapi.json by construction** (`POST /mcp` mounted on raw Express outside Nest routing) — hand-documented in `docs/06`; sole exemption from the docs-contract gate.
11. **Lowest test coverage: `src/common/cache/`** (34% branches / 42% lines vs 61–92% elsewhere) — soft spot.
12. **Retry policy only in Go SDK** — other four SDKs intentionally never retry; fine, but worth remembering when clients expect backoff.

### Local-machine caveats (this workstation)
13. **better-sqlite3 native build**: installed `v12.11.1` with `--no-save` + official Node-v127 win32-x64 prebuilt, because v13.0.3 has **no prebuilds** and no MSVC Build Tools exist here (`vswhere` finds only SSMS). ⚠️ Re-running `npm install` will restore v13 and fail without VS Build Tools ("Desktop development with C++") — either install the toolchain or pin `"better-sqlite3": "12.11.1"` in package.json/overrides.
14. **Puppeteer Chrome** downloaded to `%USERPROFILE%\.cache\puppeteer\chrome\win64-146.0.7680.31` (needed by whatsapp-web.js sessions; lazy-required at session start, not boot).
15. **Dev-server stdout lag under `nest start --watch`**: on this machine the watch-launched API sometimes shows minutes-long silence between `[Bootstrap] Loading .env` and binding :2785 (native-blocked, single Socket handle per diagnostic report), while direct `node dist/main.js` boots in <1s. Suspect antivirus real-time scanning of freshly compiled `dist/` + node_modules natives. Workaround: poll `netstat -ano | findstr :2785` instead of trusting log tail; or run `npm run start:dev` alone / plain `node dist/main.js`.
16. **Windows file locks**: npm lifecycle steps that rewrite `node_modules` (e.g. `npm ci`) can hit `EPERM unlink` on `.node` binaries while any node process is alive — kill stray node processes first (this bit the postinstall hook during setup).

### Rebrand surgery (2026-08-26): OpenWA -> 01flux WA

Full-tree rename applied (~3,700 occurrences / ~640 files) with context-aware token mapping (brand starts with a digit; several grammars forbid that):

| Context | Old | New | Constraint |
|---|---|---|---|
| Prose/display | OpenWA | 01flux WA | identity |
| JS/TS/Java/PHP identifiers | OpenWAClient, ... | FluxWaClient, ... | identifiers cannot start with a digit |
| Env vars | OPENWA_DATA_DIR, ... | FLUX_WA_* | Dockerfile/POSIX shell forbid digit-leading names |
| Webhook headers | X-OpenWA-Signature/-Event/-Idempotency-Key/-Delivery-Id/-Retry-Count | X-FluxWa-* (wire forms x-fluxwa-*) | header tokens cannot contain spaces |
| Prometheus series | openwa_up, openwa_messages_total | flux_wa_* | metric names cannot start with a digit |
| npm/docker/helm/volumes/network/S3 bucket/MCP serverInfo/pg db name | openwa(-x) | 01flux-wa(-x) | allow leading digits |
| Redis/storage keys, symbols | openwa_*, openwa:throttle:* | flux_wa_* | code-safe snake token |
| API-key prefix | owa_k1_ | flx_k1_ | brand prefix; keys are opaque hashed strings, existing keys stay valid |
| Linux container user | openwa (gosu) | fluxwa | useradd forbids digit-leading usernames |
| Java pkg / Python mod / Go pkg | ...openwa | ...fluxwa (+ dirs renamed) | language identifier rules |
| Charts dir | charts/openwa | charts/01flux-wa | helm chart name |

**Deliberate exception**: runtime DB filenames stay ./data/openwa.sqlite - renaming would orphan existing local data.

**Verification**: nest build clean; full Jest unit lane = 333 suites passing with ONLY the 10 pre-existing Windows-platform failures (37 tests) byte-identical to a baseline run of the pristine upstream tree; scripts lane 115/115; dashboard node --test 332/332; dashboard prod build OK; gates green (sdk-routes 337 literals, sdk-docs, versions, contract-shapes exit 0, dockerignore).

**Pending follow-ups from the rebrand:**

22. **External coordinates point at not-yet-existing remotes**: github.com/rmyndharis/01flux-wa, docker.io/GHCR image paths, npm/Packagist package names, and the plugin catalog URL in src/config/configuration.ts (`rmyndharis/01flux-wa-plugins`). Create these repos/org packages before running publish/release workflows.
23. **go.sum hashes**: Go module path renamed textually in go.mod/go.sum; refresh via go mod tidy on a machine with the Go toolchain before building sdk/go.
24. **Webhook contract migration**: receivers validating X-OpenWA-* headers must move to X-FluxWa-*; no compatibility shim added (surgical scope).
25. **Upgrading existing deployments**: pinned compose volumes renamed to `flux_wa_{postgres,redis,minio}-data` (needs volume migration or services start empty) and Prometheus dashboards scraping openwa_* series need updating. Existing local SQLite data is untouched (deliberate filename exception).
26. **Railway deployment prep (2026-08-26)**: added ailway.json (Dockerfile builder, healthcheck /api/health/ready, ON_FAILURE restart) and RAILWAY.md (volume at /app/data is REQUIRED for persistence; recommended vars NODE_ENV/AUTO_START_SESSIONS/API_MASTER_KEY/ENGINE_TYPE). Repo pushed to github.com/osama11gamal/01flux-WA. Single-replica only - SQLite + in-process sessions are single-node by design.
