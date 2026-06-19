# Engineering Review — Oz Deals Bot

Reviewed against: `.claude/skills/senior-engineering-reviewer/SKILL.md`
Date: 2026-06-19
Scope: full codebase

---

## Summary

The core pipeline is structurally sound and the multi-source architecture is correct. However, the project has zero unit tests for any business logic, one production-impacting bug (deals saved as posted even when Telegram delivery fails), and several maintainability issues that will cause friction as the codebase grows. None of these are architectural regressions — they are gaps that existed from the initial implementation. All items are fixable without changes to the data model or the pipeline flow.

---

## Architecture Issues

### A1 — `DealsPipelineScheduler` depends directly on `PostRepository`

**File:** `scheduler/DealsPipelineScheduler.java:6,86–96`

The scheduler has six dependencies, one of which is a repository. `wasRecentlyPosted()` and `savePost()` are deduplication concerns — they belong in a service, not in the orchestrator. The scheduler's job is to call services in order. Direct repository access in the scheduler violates single responsibility and makes the deduplication logic untestable in isolation.

**Impact:** Cannot unit-test deduplication logic without wiring the full scheduler. Adding a second pipeline variant in the future requires duplicating this logic.

---

### A2 — `TelegramPublisherService` mixes message formatting with HTTP delivery

**File:** `service/TelegramPublisherService.java:37–47`

`buildMessage()` is pure business logic (string formatting, emoji, price presentation). It is a private method inside a class that requires an HTTP client to instantiate. This makes the message format untestable without mocking `RestTemplate`.

**Impact:** Any change to message format (adding a field, changing currency symbol, adjusting layout) cannot be verified by a test.

---

### A3 — `Product` entity never updates after first insert

**File:** `service/PriceTrackingService.java:26–35`

`saveProduct()` only inserts when `!existsById(asin)`. If a product's title, brand, or image URL changes on Amazon (common for relisted items), the stored data goes stale permanently.

**Impact:** Deals can be published with outdated titles or broken image URLs pulled from the database. Low severity for MVP but a known correctness gap.

---

### A4 — No transaction boundary around `saveProduct` + `recordPrice`

**File:** `scheduler/DealsPipelineScheduler.java:57–61`

`saveProduct()` and `recordPrice()` are separate calls with no `@Transactional` grouping. If `recordPrice()` throws after `saveProduct()` succeeds, the product record exists but has no price history. This is an unlikely partial-failure scenario but leaves the system in an inconsistent state with no recovery path.

---

## Testing Gaps

The project contains one test file: `OzDealsBotApplicationTests.contextLoads()`. There are no unit tests for any business logic.

### T1 — `ScoringService` — no tests

**File:** `service/ScoringService.java`

This is the most critical testing gap. `ScoringService` contains multiple scoring tiers and two independent scoring dimensions. Bugs here directly determine what gets published. The class has no external dependencies and is trivially testable with plain JUnit.

Untested cases include:
- `discountPercent()` when `listPrice` is null (returns 0 — correct, but unverified)
- `discountPercent()` when `listPrice` equals `currentPrice` (returns 0)
- `discountScore()` boundary values (exactly 10%, 20%, 30%, 40%, 50%)
- `historicalLowScore()` when `currentPrice` equals `historicalLow` (should return 50)
- `historicalLowScore()` when `historicalLow` is empty (returns 0 — correct, but unverified)
- Combined scores at the publish threshold (score = 69 must not publish, score = 70 must)

---

### T2 — `ValidationService` — no tests

**File:** `service/ValidationService.java`

Untested cases include:
- null `asin`
- blank `title`
- `currentPrice` of zero
- null `currentPrice`
- blank `imageUrl`
- blank `affiliateLink`
- a fully valid product

---

### T3 — `ProductDiscoveryService` — deduplication untested

**File:** `service/ProductDiscoveryService.java`

The ASIN deduplication (`LinkedHashMap.putIfAbsent`) is the only cross-source correctness guarantee. If it regresses, the same product can be scored and (if it qualifies) published multiple times in a single cycle. It is not tested.

Untested cases include:
- same ASIN returned by two sources — first occurrence must win
- null ASIN in result — must be skipped
- one source throwing an exception — others must still be aggregated

---

### T4 — `TelegramPublisherService.buildMessage()` — untested and untestable

**File:** `service/TelegramPublisherService.java:37–47`

`buildMessage()` is private. The message format (emoji, price labels, affiliate link) cannot be tested without making an HTTP call or restructuring the class. See A2.

---

### T5 — Deduplication window logic — untested

**File:** `scheduler/DealsPipelineScheduler.java:86–88`

`wasRecentlyPosted()` uses `LocalDateTime.now().minusHours(24)`. An off-by-one error here (e.g., `minusHours(23)` or using a different time zone) would either allow duplicate posts or suppress valid deals. The logic is private and not reachable by a unit test in its current location.

---

### T6 — `MockProductSourceClient` — no shape validation test

**File:** `source/MockProductSourceClient.java`

The mock data is the only fully exercised source in default configuration. No test verifies that the returned products have valid ASINs, non-null prices, and non-blank affiliate links. If a mock product is malformed, `ValidationService` will silently discard it with no indication of why.

---

## Maintainability Concerns

### M1 — `messageHash` is not a hash

**File:** `scheduler/DealsPipelineScheduler.java:95`

```java
.messageHash(product.getAsin() + "_" + product.getCurrentPrice())
```

This is a deterministic string concatenation, not a hash. The field is named `messageHash` and its column is in the `posts` table, implying it identifies a unique Telegram message. It does not. If the message format changes (new fields, reordered content), the "hash" stays the same. If the price changes by one cent, it produces a new "hash" for the same product in the same 24-hour window — but deduplication uses `findTopByAsinAndPostedAtAfter`, not this field, so the field is currently unused for any actual logic.

**Impact:** Dead field with a misleading name. Low severity now; confusing in future.

---

### M2 — `RestTemplate` has no timeout configuration

**File:** `config/AppConfig.java`

The `RestTemplate` bean is created with default settings: no connect timeout, no read timeout. A hung Telegram call or a stalled Amazon PA-API response will block the scheduler thread indefinitely. Because the `AtomicBoolean` lock is only released in the `finally` block of the running thread, a permanently blocked thread means the scheduler never runs again until the application is restarted.

---

### M3 — `TelegramPublisherService` credentials have no defaults

**File:** `service/TelegramPublisherService.java:15–18`

`${telegram.bot.token}` and `${telegram.channel.id}` have no default values. The bean is always created (no `@ConditionalOnProperty`), so the application fails to start if these env vars are absent — even in a local run with `sources.mock.enabled=true` and no intent to publish anything.

**Impact:** Developers cannot run the pipeline locally to test scoring or validation without providing real or placeholder Telegram credentials.

---

### M4 — `AwsV4Signer` is a static utility class with no interface

**File:** `amazon/AwsV4Signer.java`

`AwsV4Signer.sign()` is called statically from `AmazonPaApiClient`. It cannot be mocked. Any test that exercises `AmazonPaApiClient`'s parsing or retry logic must work around or accept that the signing step runs (and fails, since credentials are not available in tests).

**Impact:** Low for MVP. Noted for when `AmazonPaApiClient` tests are added.

---

## Production Risks

### R1 — Deals saved as posted even when Telegram delivery fails (BUG)

**File:** `scheduler/DealsPipelineScheduler.java:71–73`
**Severity: High**

```java
telegramPublisherService.publish(product, discountPercent);
savePost(product);
published++;
```

`TelegramPublisherService.publish()` catches its own exceptions internally and logs them. It returns `void`. If the Telegram API call fails, `publish()` returns normally. The scheduler then calls `savePost()`, writing a `Post` record to the database. The product is now marked as posted for 24 hours. It will not be retried.

**Result:** A qualifying deal can fail to reach the Telegram channel and be silently suppressed for 24 hours with no observable indication beyond a single log line in `TelegramPublisherService`.

---

### R2 — Mock products will publish to Telegram on the second pipeline run

**File:** `source/MockProductSourceClient.java`
**Severity: Medium**

The three mock products have hardcoded prices and list prices. On the first pipeline run, `getHistoricalLow()` returns empty (no history yet), so `historicalLowScore` = 0:

| Product | Discount | Discount Score | Historical Low Score | Total |
|---|---|---|---|---|
| iPhone 13 | ~29% | 25 | 0 | 25 — not published |
| Sony WH-1000XM4 | ~49% | 45 | 0 | 45 — not published |
| Samsung TV | ~40% | 35 | 0 | 35 — not published |

On the second run, prices match the historical low exactly, so `historicalLowScore` = 50:

| Product | Discount Score | Historical Low Score | Total |
|---|---|---|---|
| iPhone 13 | 25 | 50 | 75 — **PUBLISHED** |
| Sony WH-1000XM4 | 45 | 50 | 95 — **PUBLISHED** |
| Samsung TV | 35 | 50 | 85 — **PUBLISHED** |

If a real Telegram bot and channel are configured alongside `sources.mock.enabled=true`, three fake product posts will appear in the channel approximately 10–20 minutes after the second scheduler execution. The placeholder image URLs (`https://m.media-amazon.com/images/I/placeholder.jpg`) will produce broken images or Telegram API errors.

---

### R3 — Scheduler permanently deadlocks if a thread blocks indefinitely

**File:** `config/AppConfig.java`, `scheduler/DealsPipelineScheduler.java:46–83`
**Severity: Medium**

Spring `@Scheduled` uses a single-thread executor by default. If an HTTP call to Amazon or Telegram hangs indefinitely (no timeout configured — see M2), the scheduler thread blocks. The `AtomicBoolean` lock is held. No future executions run. The application appears alive (Spring context up, no crash) but silently stops processing.

---

### R4 — `ddl-auto=update` remains active

**File:** `src/main/resources/application.properties:5`

`spring.jpa.hibernate.ddl-auto=update` auto-migrates the schema on startup. On a first run this is necessary. Left active in production it can silently apply schema changes (column additions) or fail silently on incompatible changes (column type changes). This is a known risk documented in PROGRESS.md but not yet addressed.

---

## Next Recommended Actions

Ordered by impact:

### 1. Fix R1 — make `publish()` signal success or failure

Change `TelegramPublisherService.publish()` to return `boolean`. Call `savePost()` only when `publish()` returns `true`. This is a small, focused change with immediate production correctness impact.

### 2. Add unit tests for `ScoringService`

No external dependencies. Covers the most business-critical logic in the system. Should cover all tier boundaries and the threshold rule. Estimated: 10–15 test cases.

### 3. Add unit tests for `ValidationService`

No external dependencies. Covers all null/blank/zero combinations. Estimated: 6–8 test cases.

### 4. Configure `RestTemplate` timeouts in `AppConfig`

Add connect timeout (5s) and read timeout (15s). Prevents indefinite thread blocking and scheduler deadlock. One-line change per timeout.

### 5. Move `wasRecentlyPosted()` and `savePost()` to a dedicated service

Extract to a `PostService` or `DeduplicationService`. This makes deduplication testable in isolation and removes the direct repository dependency from the scheduler.

### 6. Extract `buildMessage()` from `TelegramPublisherService`

Make it package-private, or move it to a `TelegramMessageFormatter` class. This enables unit testing of message format without an HTTP client.

### 7. Add `@ConditionalOnProperty` to `TelegramPublisherService` or add placeholder defaults

Allow the application to start locally without Telegram credentials when publishing is not needed. The simplest fix is adding `:` defaults to the `@Value` annotations and short-circuiting `publish()` when credentials are blank.

### 8. Add unit tests for `ProductDiscoveryService` aggregation

Mock two `ProductSourceClient` implementations. Test: same ASIN from two sources → first wins. Null ASIN → skipped. Source throws → others still aggregated.

---

## Checklist Summary

| Item | Status |
|---|---|
| Code simpler than the problem | Pass |
| Unit tests for business logic | Fail — none exist |
| Business logic isolated from integrations | Warn — `buildMessage()` trapped in HTTP class; deduplication in scheduler |
| External integrations isolated behind clients | Pass |
| Logging sufficient to diagnose failures | Warn — Telegram failure loses the deal silently (R1) |
| Scheduler safe and non-overlapping | Pass — lock mechanism is correct |
| `PROGRESS.md` maintained | Pass |
| Changes within MVP scope | Pass |

**Verdict: Changes required before production use. Safe to continue developing locally.**
