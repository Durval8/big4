PROD_PROJECT  := big4
TEST_PROJECT  := big4-test
COMPOSE       := docker compose
PROD_COMPOSE  := $(COMPOSE) -p $(PROD_PROJECT)
TEST_COMPOSE  := $(COMPOSE) -p $(TEST_PROJECT) --env-file .env.test

.PHONY: build up down restart logs ps clean up-data \
        build-test up-test down-test restart-test logs-test ps-test clean-test up-test-data

# ── Production (Cloudflare tunnel — ports from .env) ────────────────────────

build:
	$(PROD_COMPOSE) build

up:
	$(PROD_COMPOSE) up -d

down:
	$(PROD_COMPOSE) down

restart:
	$(PROD_COMPOSE) restart

logs:
	$(PROD_COMPOSE) logs -f

ps:
	$(PROD_COMPOSE) ps

## Wipe prod data (destructive)
clean:
	$(PROD_COMPOSE) down -v

## Bring prod up, then POST dummy transactions via the running API (see scripts/seed-dummy-transactions.sh).
## Not stored in any volume/migration -- just curl calls against the real endpoint after boot.
up-data: up
	bash scripts/seed-dummy-transactions.sh prod

# ── Test environment (browser: http://localhost:9090) ───────────────────────

build-test:
	$(TEST_COMPOSE) build

up-test:
	$(TEST_COMPOSE) up -d

down-test:
	$(TEST_COMPOSE) down

restart-test:
	$(TEST_COMPOSE) restart

logs-test:
	$(TEST_COMPOSE) logs -f

ps-test:
	$(TEST_COMPOSE) ps

## Wipe test data (destructive)
clean-test:
	$(TEST_COMPOSE) down -v

## Bring the test stack up, then POST dummy transactions via the running API (see scripts/seed-dummy-transactions.sh).
## Not stored in any volume/migration -- just curl calls against the real endpoint after boot.
up-test-data: up-test
	bash scripts/seed-dummy-transactions.sh test
