# Statement Import (PDF → transactions) — Feature Spec

> **Status: SHELVED — not scheduled.** Proposed 2026-07-24. This is a design
> record to pick up later, not committed work. Nothing here is built. The
> [Open questions](#open-questions) must be answered before implementation
> starts.

## Context & motivation

Today transactions are entered one at a time through the UI (see
[DATA_MODEL.md](../DATA_MODEL.md)). The goal of this feature is to let a user
upload a **monthly bank statement PDF** and have the system extract, categorize,
and (after review) store the individual transactions — turning a tedious manual
task into an upload-and-approve flow.

Statements arrive in **different layouts per bank, and in different languages**.
That variance is precisely where hand-written parsers/templates break and where
an LLM generalizes well, which is why this routes extraction through
**Claude Sonnet 5** rather than a deterministic parser.

## Goals

- Upload a statement PDF, extract its line items as a sequence of transactions storable in the existing `transactions` table.
- Categorize each entry against the fixed `Category` enum; unknowns get an explicit `UNCATEGORIZED` value.
- **Guarantee the full statement value is accounted for** via arithmetic reconciliation, or else refuse to auto-commit.
- Let users review/correct before anything hits the ledger, and **minimize** how often correction is needed.
- Learn from corrections so recurring items stop needing them.

## Non-goals (for the first pass)

- Multi-currency (the app is single-currency today).
- Automatic cross-account transfer detection (a debit on one statement is a credit on another — see [Interactions](#interactions-with-the-existing-model)).
- Direct bank API / Open Banking integration (this is PDF-only).
- Auth / multi-user data isolation (unchanged from the current single-user scope).
- Fully unattended import (human review stays mandatory in v1).

## Why it's feasible (grounded in the Anthropic API)

- **Native PDF ingestion.** Claude accepts a PDF directly as a base64 `document` block, including **scanned/image** statements via built-in vision/OCR. No per-bank text-extraction library or template engine is required. Personal statements sit far inside the size/page limits.
- **Structured Outputs.** The response is constrained to a JSON schema that maps 1:1 to `List<TransactionRequest>` + statement metadata, so there's no fragile string parsing, and `category` is an `enum` constraint the model cannot violate.
- **Model.** `claude-sonnet-5` — strong multilingual extraction, cheap (cents per statement), large context. Opus 4.8 is a fallback for unusually poor scans.

## Core reliability principle

The model does **not** need to be perfect; the system needs to **detect** when it wasn't and route those cases to a human. Two halves:

1. **Completeness = mathematically verifiable → hard gate.** Extract the statement's opening balance, closing balance, and (if present) stated debit/credit totals alongside the line items, then assert:

   ```
   opening + Σ(credits) − Σ(debits) == closing        (within a small tolerance)
   ```

   Plus a second check: model-reported transaction count vs. count of extracted rows. If either fails, the extraction is provably incomplete/wrong → the import is flagged and **never auto-committed**. This satisfies "the complete statement value is accounted for."

2. **Categorization = not verifiable → soft signals.** `UNCATEGORIZED` fallback (the model is told to use it rather than guess), a per-line **confidence score**, and the learning loop below. Low-confidence or uncategorized rows surface for review even when reconciliation passes.

## Proposed architecture

### Data flow

```
upload PDF + pick account
      │
      ▼
StatementImport (PENDING) ──async──▶ call Claude Sonnet 5 (PDF + structured-output schema)
                                          │
                                          ▼
                                   parse → StagedTransaction[] + statement metadata
                                          │
                                   reconcile (opening+credits−debits == closing?)
                                     ├─ pass  → status NEEDS_REVIEW
                                     └─ fail  → status NEEDS_REVIEW (flagged) / FAILED
                                          │
                              user reviews / edits / approves in UI
                                          │
                          approved rows → real Transaction rows (ledger)
                          corrections   → CategoryRule (learning loop)
```

### New entities

- **`StatementImport`** — `id`, `filename`, `targetAccountType`, `status` (`PENDING/PROCESSING/NEEDS_REVIEW/READY/FAILED`), `statementPeriod` (from/to), `openingBalance`, `closingBalance`, `reconciliationResult` (passed + computed-vs-stated deltas), `rawModelOutput` ref (for audit/debugging), timestamps.
- **`StagedTransaction`** — mirrors `TransactionRequest` (description, amount, date, accountType, category, transactionType) **plus** `confidence`, `sourceLineRef`, `budgetImpactPreview?`, `approved` flag, FK to `StatementImport`. On approval, becomes a real `Transaction`.
- **`CategoryRule`** — `matchType` (EXACT/CONTAINS/REGEX), `pattern` (normalized description/merchant), optional `accountType`, `category`, `hitCount`, `createdAt`. Drives the learning loop.

### Structured-output schema (sketch)

```jsonc
{
  "statement": { "openingBalance": number, "closingBalance": number,
                 "periodFrom": "date", "periodTo": "date",
                 "statedTotalDebits": number?, "statedTotalCredits": number?,
                 "transactionCount": integer?, "currency": "string?" },
  "transactions": [
    { "date": "date", "description": "string", "amount": number,   // always positive
      "direction": "DEBIT" | "CREDIT",                              // mapped to type on approval
      "category": <Category enum | "UNCATEGORIZED">,
      "confidence": number }                                       // 0..1
  ]
}
```

### Backend components

- File-upload endpoint (multipart) with type/size validation.
- Async job (`@Async` + status-poll endpoint) — extraction can take seconds-to-minutes; must not block the request. Full queue/worker infra is out of scope for v1.
- Anthropic Java SDK (`com.anthropic:anthropic-java`): PDF via `DocumentBlockParam` + `Base64PdfSource`; typed structured output via `StructuredMessageCreateParams<T>`. **API key from env/secret, never in code.**
- `ReconciliationService`, `CategoryRuleEngine`, `DuplicateDetector`.

### Frontend components

- Upload screen (pick target account, drop PDF).
- Import status/progress (poll `StatementImport.status`).
- **Review screen** — editable staged-transaction table with category override + approve/reject, a reconciliation banner (pass/fail + deltas), and confidence/UNCATEGORIZED highlighting. This is the largest UI piece.

## Learning loop (self-improvement)

Cheapest-first; do (a)+(b), skip fine-tuning:

- **(a) Deterministic correction rules.** A user recategorizing a recurring merchant writes a `CategoryRule`; future imports apply it automatically after the model pass. Recurring items (salary, rent, subscriptions) get fixed once and stay fixed — explainable, no ML.
- **(b) In-context hints.** Feed the user's known merchant→category mappings + recent corrections into the prompt so the model self-corrects per request. **Prompt caching** makes repeating this near-free.
- **Not fine-tuning** — overkill, costly, goes stale; (a)+(b) capture the value immediately.

This is exactly "recurrent undefined transactions get taught as users specify them": resolving an `UNCATEGORIZED` item persists a rule; next time that merchant appears it's auto-categorized. `hitCount` shows value and drives pruning.

## Interactions with the existing model

- **Debit/credit → taxonomy.** Statements have debits/credits, not `INCOME/EXPENSE/TRANSFER/ADJUSTMENT`. Default map: debit→`EXPENSE`, credit→`INCOME`. `TRANSFER` is **not** auto-detected in v1 (see non-goals) — it's a manual reclassification, because a transfer between the user's own accounts appears as a debit on one statement and a credit on another and the model can't see the other account from a single PDF. This affects net-worth / net-investment math ([DATA_MODEL.md](../DATA_MODEL.md#dashboard-metrics)).
- **Opening balance = `ADJUSTMENT`.** The first import of an account can seed an `ADJUSTMENT` from the statement's opening balance — clean synergy with the existing opening-balance mechanism.
- **Budgets.** Approved `EXPENSE` rows in budget categories flow into budget `spent` automatically (see [DATA_MODEL.md](../DATA_MODEL.md#entity-budget)) — no extra work, but a reason duplicate detection matters.
- **Duplicate detection.** Re-importing a statement or overlapping periods must not double-count (balances *and* budgets depend on it). Dedupe on `(accountType, date, amount, normalized description)` + track imported periods per account.

## Suggested phasing

1. **Phase 1** — upload one PDF → pick account → async extraction → reconciliation gate → staging review → approve into ledger. `UNCATEGORIZED` fallback. No learning yet.
2. **Phase 2** — correction rules (learning loop a+b), duplicate detection, dashboard/budget integration polish.
3. **Phase 3** — cross-statement transfer matching, batch upload, line-level provenance via Claude PDF **citations** (tie each row to a page/coordinate).

## Testing approach

- The model can't be unit-tested; test the deterministic pieces — reconciliation math, rule application, dedupe, debit/credit→type mapping — with fixtures.
- Keep a small set of real/sample statements as integration fixtures; consider recording model outputs for regression.

## Risks, security & privacy

- **Sensitivity.** Bank statements are highly sensitive financial PII. Sending them to a third-party API is a deliberate decision, separate from "can we build it" — confirm comfort with Anthropic's API data handling (API inputs are not used for training by default), lock down the API key, and never log raw statement contents. **This is the biggest go/no-go question.**
- **Cost/latency.** A few pages is cents and seconds; fine for personal use. Needs timeouts, retries, and token accounting.
- **Silent partial commits.** Guarded by: staging (never write straight to ledger) + reconciliation gate + storing raw model output per import.

## Rough effort

Medium-high. The model call is the easy part (native PDF + structured outputs). The weight is the async job model, three new entities + migrations-equivalent, reconciliation/rule/dedupe services, and especially the review UI.

## Open questions

These need answers before Phase 1 starts:

1. **Privacy sign-off (blocking).** Is sending statement PDFs to the Anthropic API acceptable? If not, the whole feature is a non-starter in this form (would need an on-prem/local model instead).
2. **API key & hosting.** Where does the `ANTHROPIC_API_KEY` live in the Docker Compose / deployment story, and who pays for tokens? (Currently there is no secrets management in the stack.)
3. **Target account: user-selected or model-inferred?** Recommendation is user-selected at upload (explicit, avoids misassignment) — confirm.
4. **Reconciliation tolerance & no-metadata fallback.** What tolerance counts as "reconciled"? And what happens when a statement has **no** opening/closing balance printed (some don't)? Reject, or fall back to count-only + full manual review?
5. **Transfer handling.** Confirm v1 imports everything as INCOME/EXPENSE and transfers are a manual reclassification. Is cross-statement transfer matching wanted early, or genuinely Phase 3?
6. **Opening-balance seeding.** On an account's first import, auto-create an `ADJUSTMENT` from the opening balance, or leave that to the user?
7. **Duplicate/overlap policy.** Exact dedupe key, and behavior on partial period overlap between two statements (skip dupes silently, or show them in review flagged?).
8. **`UNCATEGORIZED` in the enum.** Adding it changes the `Category` enum and touches the existing manual-entry UI and budget logic — is a dedicated "uncategorized" state acceptable there too, or import-only?
9. **Rule scope.** Are `CategoryRule`s global to the user, or per-account? Case/locale-normalization rules for matching merchant strings?
10. **Retention of raw model output.** How long do we keep `rawModelOutput` (useful for debugging/audit, but it *is* the sensitive statement content)?
11. **Async infra bar.** Is `@Async` + a status column enough for v1, or is a real job table/queue wanted from the start?
12. **Failure UX.** When extraction fails or won't reconcile, what does the user see and what are the retry semantics?
