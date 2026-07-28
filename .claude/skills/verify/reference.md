# Verify skill — reference

Supporting detail for the `verify` skill. Read this when you need the specifics; `SKILL.md` stays
the short, always-relevant checklist.

## Tier routing

```bash
git diff --stat HEAD   # or against the target branch
```

| Touched | Minimum verification |
|---|---|
| `backend/**` (not messaging) | `cd backend && mvn test -Dtest=<RelevantTest>` (fast, no Docker) |
| `investments-service/**` (not messaging) | `cd investments-service && mvn test -Dtest=<RelevantTest>` |
| Anything under `**/messaging/` or `**/messaging/contract/` in **either** module | Also run the contract test: `cd backend && mvn test -Dtest=InvestmentMessageContractTest` — this is the one test whose entire job is catching drift between the investments-service's canonical message records and the backend's mirror records |
| A balance/budget formula (`BalanceService`, `BudgetService`) | `BalanceServiceTest` / `BudgetServiceTest` plus check whether `docs/DATA_MODEL.md`'s formulas still match |
| A buy/cash-out/pricing/news code path | The corresponding `*IT` in `investments-service` (`HoldingServiceIT`, `PriceRefreshIT`, `NewsServiceIT`) — these need Docker (Testcontainers Mongo + RabbitMQ) |
| `frontend/**` | `cd frontend && npm run build` — no lint/test runner configured, so a successful build is the only automated gate; see SKILL.md's frontend note for the manual behavior check |
| Both services' `pom.xml` / module wiring | `mvn test` from the repo root (both modules, fast tier only) |

Only reach for the **full** `mvn verify` (both modules, all Testcontainers ITs) when the change is
broad (touches shared messaging contracts or module wiring) or you're about to hand off/merge — it's
slow and needs Docker up. For a scoped change, a targeted `-Dtest=`/`-Dit.test=` run is faster and
just as conclusive.

```bash
# Fast tier (no Docker) — always safe, always fast
cd backend && mvn test -Dtest=<Test>[#<method>]
cd investments-service && mvn test -Dtest=<Test>[#<method>]

# Testcontainers tier (needs Docker) — scope to one IT when possible
cd investments-service && mvn verify -Dit.test=<NameIT>
```

## Weakened-test red flags

Things that make a suite go green without validating real behavior — treat any of these, found in a
diff, as a finding to report, not a detail to wave through:

- **Assertion removed or commented out** entirely, with no replacement.
- **Assertion loosened**: `assertEquals(exact, actual)` → `assertNotNull(actual)` / `assertTrue(true)`,
  or a numeric tolerance widened (`assertEquals(x, y, 0.001)` → `assertEquals(x, y, 0.5)`) without a
  stated reason tied to an actual precision change in the code.
- **Expected value edited to match new output** with no corresponding, justified behavior change in
  the diff — i.e., the assertion was bent to fit the code instead of the code being fixed to fit the
  assertion. This is the single most common way a "passing" test suite hides a real regression.
- **`@Disabled` / `@Ignore` / `.skip()` / `xit`/`xdescribe`** added to a previously-active test.
- **A test method or class deleted** without an equivalent replacement, and without the removed
  behavior being explicitly called out as now out of scope.
- **Exception assertion narrowed or removed**: `assertThrows(SpecificException.class, ...)` replaced
  with a bare try/catch that swallows the exception, or removed outright.
- **Mocking introduced around the exact thing the test exercises** (e.g., mocking `BalanceService`
  inside `BalanceServiceTest`) — makes the test tautological, it no longer exercises real logic.
- **Timeout/retry counts increased** specifically to paper over a slow or flaky real regression,
  rather than fixing the underlying cause.
- **Test data narrowed**: an edge-case value quietly dropped from a parameterized test so the case
  that used to fail no longer runs at all.

How to check: read every hunk in a touched test file, don't skim the `--stat`. If a test file shows
up in the diff and the production-code change alone doesn't obviously justify the specific assertion
edit, that's suspicious — dig into what the original assertion was protecting (git blame / the
commit that introduced it) before accepting the change.

## Known noise

- **Shared-broker test isolation**: if a Testcontainers IT you didn't touch fails intermittently with
  a message-consumption assertion, check whether it's the known cross-context `@RabbitListener`
  contention (see `docs/TESTING.md`) before assuming your change broke it.
- **Provider adapter tests** (`FinnhubProviderTest`, `FinnhubNewsProviderTest`) use
  `MockRestServiceServer`, not a real HTTP server or live Finnhub — no network/API key needed to run
  them, and a failure here is never a live-API issue.
