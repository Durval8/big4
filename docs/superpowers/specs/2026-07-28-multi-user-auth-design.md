# Multi-user auth (password sign-in) — design

**Status: SHELVED — brainstormed and mostly agreed, but not yet approved or implemented.**
Do not start implementation from this doc without re-confirming with the user first; pick the
conversation back up, do a quick sanity re-check that the codebase hasn't drifted since
2026-07-28, then resume at the "open confirmation" question in the last section.

## Why

The app is moving off "single-user MVP" scope to support password-based sign-in for a small,
trusted set of users (e.g. a household), with credentials stored securely. No email verification,
2FA, or MFA. This marks a deliberate departure from the original MVP framing in CLAUDE.md, which
will need to be rewritten alongside this work (see "Docs" below).

## Decisions locked in during brainstorming

- **Auth mechanism**: stateless JWT in an httpOnly cookie (not server-side sessions, not a header-based bearer token). Fits the existing single-origin-gateway setup.
- **Token lifetime**: short-lived **access token** (JWT, ~15 min) + longer-lived **refresh token** (opaque, ~30 days, DB-backed for revocation) — not a single long-lived token.
- **Signup model**: invite-only, no public registration. `POST /api/auth/register` requires an authenticated caller — no separate admin/member role, any logged-in user can create another account (small trusted group; flagged as an assumption, easy to override).
- **Bootstrap**: on startup, if `users` is empty, seed one user from `INITIAL_USER_EMAIL`/`INITIAL_USER_PASSWORD` env vars (same pattern as existing `.env` secrets). The register endpoint itself never needs an unauthenticated path.
- **Data isolation**: fully per-user — transactions, budgets, and investment holdings are private to the owning user, not a shared household pool.
- **Existing data**: fresh start — no production data to migrate/backfill; new `user_id` columns can be `NOT NULL` from the first migration.
- **Register UI**: yes — a simple authenticated "add user" screen in the frontend (not just a bare endpoint).
- **JWT filter duplication**: each service (backend, investments-service) independently validates the access token — no shared library between them. This matches existing precedent in this repo (the two services already mirror the RabbitMQ message contract rather than share code), not a new violation of DRY.

## Approach (recommended, agreed in principle)

One integrated plan, four ordered milestones, merged together (not shipped piecemeal, so nothing
is ever half-protected on the publicly reachable production host):

1. **Auth core** (backend/Postgres) — `User` entity, BCrypt hashing, JWT access token + DB-backed
   refresh token, login/refresh/logout/register/me endpoints, backend data scoped by `user_id`
   (transactions, budgets, the two investment projections).
2. **Investments-service scoping** — `Holding`/`NewsFeed` gain `userId`; the service validates the
   same JWT independently (no sync HTTP to backend); `CashLegCommand`/`ValueSnapshot` messages gain
   a `userId` field so backend projections attribute correctly.
3. **Frontend** — login page, auth context (`GET /api/auth/me` on load), protected routes, a fetch
   wrapper that retries once through `/api/auth/refresh` on a 401, and the "add user" screen.
4. **Docs** — CLAUDE.md + the relevant `docs/*.md` files updated to drop "single-user MVP" framing.

Rejected alternatives:
- *Ship backend auth now, extend investments-service later* — would leave `/api/investments/**`
  completely open in the interim on a publicly reachable host.
- *Gateway-level auth (nginx `auth_request`)* — can gate "is someone logged in" but can't hand
  *which* user to each service without still teaching both services about identity, so it doesn't
  remove the per-service work.

## Data model

**Postgres (backend), new Flyway migrations:**
- `users`: `id BIGSERIAL`, `email` (unique), `password_hash` (BCrypt), `created_at`/`updated_at`.
- `refresh_tokens`: `id`, `user_id` FK, `token_hash` (SHA-256 of the opaque token, never stored
  raw), `expires_at`, `revoked_at` (nullable), `created_at`. Rotation: every `/refresh` call revokes
  the presented row and inserts a new one; presenting an already-revoked token revokes *all* of that
  user's tokens (cheap replay/theft mitigation).
- `transactions`, `budgets`, `investment_cash_flow` gain `user_id` (`NOT NULL`, no backfill needed).
- `investment_valuation` stops being a true singleton — one row per `user_id` (unique constraint on
  `user_id` instead of a fixed single-row PK).

**MongoDB (investments-service):** `Holding` and `NewsFeed` gain `userId` (Long, matching the
Postgres user id carried in the JWT `sub` claim). The service has **no `Users` collection of its
own** — it trusts and echoes the id from a validated JWT into its own documents and outbound
messages. `OutboxMessage` needs no schema change (it stores the serialized message, which already
carries `userId`).

## Auth flow

- **Login** `POST /api/auth/login` (backend only — it owns `users`): verify BCrypt hash, issue an
  access token cookie (`Path=/`) and a refresh token cookie (`Path=/api/auth/refresh`, to limit
  where the browser ever sends it).
- **Refresh** `POST /api/auth/refresh`: validates + rotates the refresh token, mints a new access
  token.
- **Logout** `POST /api/auth/logout`: revokes the refresh token row, clears both cookies.
- **Register** `POST /api/auth/register`: requires an authenticated caller.
- **`GET /api/auth/me`**: returns the current user for frontend hydration.
- New shared env vars: `JWT_SECRET` (both services need it to independently verify access tokens),
  `COOKIE_SECURE` (true in prod behind the Cloudflare/HTTPS tunnel, false for local Docker/test
  stacks on plain HTTP). Add to `.env`, `.env.example`, `.env.test`.

## Backend changes

- New `domain/User`, `domain/RefreshToken`; `repository/UserRepository`, `RefreshTokenRepository`.
- New `service/AuthService` (login/refresh/logout/register/bootstrap) — needs `@Transactional` for
  the atomic revoke+insert rotation, a deliberate, called-out exception to the repo's no-
  `@Transactional` convention (document this in CLAUDE.md alongside the existing note).
- New `security/JwtService` (sign/verify) + a `OncePerRequestFilter` populating a request-scoped
  current-user-id, wired via a new `config/SecurityConfig`.
- `TransactionService`, `BudgetService`, `BalanceService`, and both RabbitMQ consumers
  (`InvestmentCashLegConsumer`, `InvestmentValuationConsumer`) all take/scope by the current user id.
- New exceptions (`InvalidCredentialsException` → 401, `EmailAlreadyInUseException` → 409) added to
  the existing `GlobalExceptionHandler`.

## Investments-service changes

- Its own copy of the JWT-verification filter (no shared code, per the mirror precedent above).
- `HoldingService`, `HoldingRepository`, `InvestmentController` scoped by user id.
- `NewsFeed` becomes per-user: `NewsSelector`'s value-weighted draw runs over each user's own
  holdings, not the whole system's.
- `PriceRefreshScheduler` stays **system-wide by symbol** — a stock's price isn't user-specific, so
  no change needed there; only the holdings referencing it are scoped.
- `CashLegCommand`/`ValueSnapshot` contract records gain `userId`; `InvestmentMessageContractTest`
  fixtures updated on both sides.

## Frontend changes

- `LoginPage`, `AuthContext` (hydrated via `/api/auth/me`), a route guard redirecting
  unauthenticated users to `/login`.
- `api/client.ts` wrapper: on a 401, attempt `/api/auth/refresh` once, retry the original request,
  else redirect to login.
- A small authenticated "Add user" screen wrapping `/api/auth/register`.

## Testing

Every existing backend/investments-service test touching `Transaction`, `Budget`, `Holding`,
`BalanceService`, or the message contract needs a `user_id`/`userId` added — the single largest
mechanical cost of this change. New tests: `AuthServiceTest`, JWT filter tests (valid/expired/
missing/tampered token) in both services, and a login → refresh → logout integration test.

## Docs

- CLAUDE.md's opening framing ("Single-user personal finance dashboard (MVP)") rewritten to drop
  MVP/single-user language.
- `SYSTEM_DESIGN.md`'s Status table flips Auth to **Built**.
- `DATA_MODEL.md`'s "Single user, no auth" scope-decision bullet replaced.
- `ARCHITECTURE.md` gets the new `security`/`domain` additions documented.
- `API.md` gets the five new `/api/auth/*` endpoints.

## Future work (out of scope for this spec)

- **Mobile-responsive UI adaptation** — added during this brainstorm as a side note: the current UI
  gets clunky on mobile and needs a real responsive pass. Independent of auth; track separately.

## Open item when resuming

Design was presented in full and looked sound in discussion, but the user shelved it before giving
final approval ("shelf this spec, do not proceed for now"). When picking this back up: re-confirm
the design still holds (nothing above has silently gone stale), get explicit approval, then invoke
the `writing-plans` skill to turn this into an implementation plan — per the brainstorming skill,
that's the only next step, not jumping straight to code.
