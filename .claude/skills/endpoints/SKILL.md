---
name: endpoints
description: Fast, ground-truth lookup of every REST endpoint across big4's backend and investments-service — use before adding/renaming an endpoint, or whenever reasoning about whether something already exists.
---

# Checking what endpoints already exist

One grep across both Spring controllers packages, straight from source — not `docs/API.md`, so it
can never be stale even if a doc edit was missed:

```bash
grep -rn "@RequestMapping\|@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" \
  backend/src/main/java investments-service/src/main/java
```

Read it as: the class-level `@RequestMapping("/api/...")` is the base path; each method-level
annotation below it in the same file appends its own path (or none, meaning the base path itself).
Current shape (5 controllers, 26 mappings) as of this check:

| Base (`@RequestMapping`) | Controller | Method mappings |
|---|---|---|
| `/api/balances` | `BalanceController` | `GET` (root) |
| `/api/transactions` | `TransactionController` | `GET` (root), `GET /{id}`, `POST` (root), `PUT /{id}`, `DELETE /{id}` |
| `/api/budgets` | `BudgetController` | `GET` (root), `GET /progress`, `GET /{id}`, `POST` (root), `PUT /{id}`, `DELETE /{id}` |
| `/api/investments` | `InvestmentController` | `GET` (root), `GET /summary`, `GET /{id}`, `POST` (root), `PUT /{id}`, `POST /{id}/cash-out`, `POST /{id}/price`, `DELETE /{id}` |
| `/api/investments/news` | `NewsController` | `GET` (root) |

This table is a snapshot for orientation — **re-run the grep, don't trust this table**, since it'll
drift the moment a controller changes (that's the whole point of grepping source instead of relying
on memory or docs).

## What this skill is (and isn't) for

- **Existence/shape check** — "is there already an endpoint for X" before adding a new one, or
  "what's the full route" before wiring a frontend call. Answered in one command, no LLM judgment
  needed.
- **Not** a replacement for `docs/API.md` — once you know an endpoint exists, go there for the
  actual request/response JSON shapes, validation rules, and status codes. This skill only answers
  "does it exist and what's the path," not "what does it accept/return."
- **Not** a replacement for the `docs-sync` hook — that hook keeps `docs/API.md`'s prose accurate
  after a change; this skill is for a quick lookup at decision time, independent of whether docs
  happen to be current.
