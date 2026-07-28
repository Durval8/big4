---
name: run
description: Launch big4 (backend, investments-service, frontend, or the full stack) — picks the right one of three run modes for the task instead of always spinning up full Docker Compose.
---

# Running big4

There are three legitimate ways to run this app. Picking the wrong one either wastes time
(rebuilding Docker images for a frontend tweak) or gives an incomplete picture (frontend-only dev
server when the change is cross-service). Pick based on what you're actually checking.

> **This host also runs production**, on the default ports (`8080`/`8081`/`8090`/`5432`/`27017`/
> `5672`) via `make up` (Compose project `big4`, reading `.env`). Every mode below must assume prod
> could be up concurrently — check with `make ps` / `docker ps` first if unsure — and must not bind
> those same host ports or point at those same containers by accident. The safe pattern, already
> established by this repo's own `-test` environment, is: **always run dev/bare-metal work through
> `.env.test`'s offset ports**, never the bare defaults, regardless of whether prod happens to be
> running at this exact moment.

## Mode 1 — Frontend-only iteration

Use when the change is purely in `frontend/**` and you already have *some* backend/investments
combo running (or don't need live data).

```bash
cd frontend && npm run dev   # :5173
```

Vite's `server.proxy` **defaults** to `/api/investments` → `:8081`, `/api` → `:8080`
(`vite.config.ts`) — those are production's exact host ports. **Never run `npm run dev` with the
bare defaults on this host**: if prod is up, you'll silently proxy your dev frontend straight at
the live production backend/investments-service with no indication anything is different. Always
override both to match whatever ports your backend/investments-service actually run on:

```bash
# Against Mode 2 (bare JVMs on the .env.test-offset ports, see below)
VITE_API_PROXY_TARGET=http://localhost:9080 \
VITE_INVESTMENTS_PROXY_TARGET=http://localhost:9081 \
  npm run dev
```

## Mode 2 — One or both Java services against bare infra

Use when iterating on backend or investments-service code and you want fast reload without
rebuilding Docker images.

Both `backend/src/main/resources/application.yml` and `investments-service/.../application.yml`
hardcode `server.port` to `8080`/`8081` — the same host ports production's containers already
bind. Spring Boot's environment property source still lets `SERVER_PORT` override that (env vars
outrank `application.yml`), so use it. Likewise, run the bare infra containers under the **test**
Compose project/env, not a bare `docker compose up` — with no `-p` flag, Compose defaults the
project name to the directory name (`big4`, i.e. **the same project prod uses**) and auto-loads
`.env` (prod's ports/volumes) from the cwd. Load `.env.test`'s already-established offsets and reuse
them for everything:

```bash
set -a; source .env.test; set +a   # POSTGRES_PORT=6432, MONGO_PORT=28017, RABBITMQ_PORT=6672, BACKEND_PORT=9080, INVESTMENTS_PORT=9081, FINNHUB_API_KEY, ...

docker compose -p big4-test --env-file .env.test up postgres mongodb rabbitmq

DB_HOST=localhost DB_PORT=$POSTGRES_PORT \
RABBITMQ_HOST=localhost RABBITMQ_PORT=$RABBITMQ_PORT \
SERVER_PORT=$BACKEND_PORT \
  bash -c 'cd backend && mvn spring-boot:run'      # bare backend on :9080, against the test infra

MONGO_URI=mongodb://localhost:$MONGO_PORT/investments \
RABBITMQ_HOST=localhost RABBITMQ_PORT=$RABBITMQ_PORT \
SERVER_PORT=$INVESTMENTS_PORT \
  bash -c 'cd investments-service && mvn spring-boot:run'   # bare investments-service on :9081 — needs FINNHUB_API_KEY for live prices
```

This keeps both the containers *and* the bare JVMs entirely off prod's ports, project namespace,
and volumes, whether or not prod happens to be running right now. Add `cd frontend && npm run dev`
(with the proxy overrides from Mode 1, pointed at `:9080`/`:9081`) on top if you also need the UI.
This is the fastest inner loop for backend/investments-service work — no image rebuilds.

## Mode 3 — Full stack via Docker Compose (gateway-fronted, single origin)

Use when verifying something that depends on the **gateway routing**, the **nginx single-origin
behavior**, or you want an environment that matches production topology.

```bash
# Isolated test stack (recommended for anything exploratory — separate ports/volumes from prod)
make build-test && make up-test     # app at http://localhost:9090
make down-test                      # stop (make clean-test also wipes volumes — destructive)
```

For debugging instead of a live tail, use the `logs` skill (`bash .claude/scripts/fetch-service-logs.sh
--test`) — bounded and pre-filtered to ERROR/WARN/Exception rather than `make logs-test`'s unbounded
raw `-f` stream.

Never use `make up`/`make build` (the **production** targets, backing the live
https://big4finance.online Cloudflare tunnel) for routine verification — always use the `-test`
targets unless you are deliberately deploying (see the `deploy` skill for that).

**Gotcha:** nginx resolves upstream hostnames at container start. If you rebuild
`backend`/`investments-service` while the gateway is already running, restart the gateway too:
```bash
docker compose -p big4-test restart gateway   # or: make restart-test
```
Otherwise the gateway may keep routing to a stale container IP.

## Choosing between modes

- Quick frontend styling/layout change → Mode 1.
- Backend logic, a new endpoint, a message-contract change → Mode 2 (fast reload), then Mode 3 once
  to confirm it survives the gateway + Docker networking if the change is routing/infra-sensitive.
- Anything involving the news/pricing scheduled jobs, RabbitMQ DLQ behavior, or cross-service
  message flow end-to-end → Mode 3 (test stack) — these need the real broker and both services up
  together, not just one service against bare infra.
