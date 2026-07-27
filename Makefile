PROD_PROJECT  := big4
TEST_PROJECT  := big4-test
COMPOSE       := docker compose
PROD_COMPOSE  := $(COMPOSE) -p $(PROD_PROJECT)
TEST_COMPOSE  := $(COMPOSE) -p $(TEST_PROJECT) --env-file .env.test

.PHONY: build up down restart logs ps clean \
        build-test up-test down-test restart-test logs-test ps-test clean-test

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
