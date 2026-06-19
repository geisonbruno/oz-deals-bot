# PROGRESS.md

## Session: 2026-06-08

---

## Done

### Project structure
- `pom.xml` — Spring Boot 3.3.6, Java 21, dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, postgresql, lombok, h2 (test scope)
- `.gitignore`
- `src/main/resources/application.properties` — DB, PA-API, Telegram, scheduler config via env vars
- `src/test/resources/application-test.properties` — H2 in-memory config for tests (`@ActiveProfiles("test")`)

### Entities (Spring Data JPA — auto DDL)
- `entity/Product.java` — asin (PK), title, brand, category, imageUrl, createdAt
- `entity/PriceHistory.java` — id, asin, price, timestamp; index on `asin`
- `entity/Post.java` — id, asin, price, postedAt, messageHash; indexes on `asin` and `posted_at`

### Repositories
- `repository/ProductRepository.java` — extends JpaRepository<Product, String>
- `repository/PriceHistoryRepository.java` — custom `@Query` for `findMinPriceByAsin`
- `repository/PostRepository.java` — `findTopByAsinAndPostedAtAfter` for 24h deduplication

### DTO
- `dto/DiscoveredProduct.java` — carries asin, title, brand, category, imageUrl, affiliateLink, currentPrice, listPrice through the pipeline

### Amazon PA-API
- `amazon/AwsV4Signer.java` — full manual AWS Signature V4 (HmacSHA256, SHA-256, no SDK dependency). Signs POST to PA-API with headers: Content-Encoding, Content-Type, X-Amz-Date, X-Amz-Target, Authorization
- `amazon/AmazonPaApiClient.java` — rate limiting (1 req/s), retry with exponential backoff (up to 3 retries: 1s/2s/4s), pagination support via `ItemPage` parameter, parses JSON response with Jackson `JsonNode`, normalizes to `DiscoveredProduct`

### Services
- `service/ProductDiscoveryService.java` — iterates all 12 fixed keywords, calls `AmazonPaApiClient`, deduplicates by ASIN using `LinkedHashMap.putIfAbsent`
- `service/PriceTrackingService.java` — `saveProduct()` (upsert), `recordPrice()`, `getHistoricalLow()`
- `service/ScoringService.java` — `score()` = discountScore (0–50) + historicalLowScore (0–50); `discountPercent()` helper. Scoring tiers implemented per spec.
- `service/ValidationService.java` — validates price > 0, imageUrl not blank, affiliateLink not blank
- `service/TelegramPublisherService.java` — `sendPhoto` when imageUrl present, `sendMessage` fallback. Message format matches spec exactly.

### Scheduler
- `scheduler/DealsPipelineScheduler.java` — `@Scheduled(cron = "${deals.scheduler.cron}")`. Overlap prevention via `AtomicBoolean` (skips if previous run still in progress). Pipeline order: discoverAll → saveProduct → **validate** → recordPrice → deduplication check → score ≥ 70 → publish → savePost

### Config & Infrastructure
- `config/AppConfig.java` — `RestTemplate` bean
- `OzDealsBotApplication.java` — `@SpringBootApplication` + `@EnableScheduling`
- `docker-compose.yml` — PostgreSQL 16 with persistent volume, env-var credentials, restart policy, port 5432
- `.env.example` — documents all required environment variables

### Tests
- `OzDealsBotApplicationTests.java` — `contextLoads()` with `@ActiveProfiles("test")` (H2)

---

## Session: 2026-06-10 — Hardening improvements

### Changes applied

**1. PA-API rate limiting**
- `AmazonPaApiClient` now enforces 1 request per second via `synchronized enforceRateLimit()` using `AtomicLong lastRequestTimeMs`
- Rate limit applies to every API call, regardless of keyword or page

**2. Retry with exponential backoff**
- `searchPage()` retries up to 3 times on HTTP 429 (throttling) or any connection error
- Backoff delays: 1s → 2s → 4s (powers of 2)
- 429 specifically is detected via `HttpClientErrorException` status code check

**3. Overlap prevention in scheduler**
- `DealsPipelineScheduler` uses `AtomicBoolean running` with `compareAndSet(false, true)`
- If a previous execution is still running, the new one logs a warning and skips immediately
- `running` is always reset in `finally` block to prevent deadlock on exception

**4. ASIN deduplication before pipeline**
- `ProductDiscoveryService.discoverAll()` uses `LinkedHashMap<String, DiscoveredProduct>`
- `putIfAbsent` ensures the first occurrence of each ASIN wins (preserves keyword order)
- Prevents the same product from being processed multiple times when it appears across keywords

**5. Database indexes**
- `PriceHistory`: `idx_price_history_asin` on `asin` — speeds up historical low queries
- `Post`: `idx_post_asin` on `asin` and `idx_post_posted_at` on `posted_at` — speeds up deduplication check
- `Product.asin` is the primary key — PostgreSQL already creates an implicit index for it

**6. Validation moved before price history**
- Previous order: `saveProduct → recordPrice → validate`
- New order: `saveProduct → validate → recordPrice`
- Invalid products (no price, no image, no affiliate link) are discarded before writing to `price_history`
- Keeps the database clean and avoids storing useless price snapshots

**7. Pagination support in PA-API**
- `AmazonPaApiClient` now accepts `ItemPage` in the request body
- `searchItems(keyword)` loops from page 1 to `maxPages`, stopping early if a page returns fewer than 10 items
- Controlled by `amazon.paapi.max-pages` in `application.properties` (default: 1 = current behavior)
- **Limitation**: Each page is a separate signed API call. At max-pages=3, 12 keywords × 3 pages = 36 API calls minimum per cycle (≥36s with rate limiting). Budget accordingly.
- **Pagination constraint**: PA-API's `ItemPage` supports values 1–10 (max 100 results per keyword). Not all keywords will have 10 full pages.

**8. docker-compose.yml**
- PostgreSQL 16 Alpine container
- Persistent named volume (`postgres_data`)
- Credentials from `.env` file via `${DB_USERNAME:-postgres}` syntax
- `restart: unless-stopped` for VPS deployment
- Exposes port 5432

**9. .env.example**
- Documents all 7 required environment variables with example values and instructions

---

## Session: 2026-06-19 — Multi-source product discovery

### Changes applied

**1. `ProductSourceClient` interface**
- New package `com.ozdeals.bot.source`
- Single method: `List<DiscoveredProduct> discoverProducts()`

**2. `AmazonProductSourceClient`**
- Implements `ProductSourceClient`; delegates to existing `AmazonPaApiClient`
- Owns the 12-keyword list (moved from `ProductDiscoveryService`)
- Enabled via `sources.amazon.enabled=true` (`@ConditionalOnProperty`)

**3. `MockProductSourceClient`**
- Implements `ProductSourceClient`; returns 3 hardcoded products (iPhone, Sony headphones, Samsung TV)
- Intended for local testing without Amazon credentials
- Enabled via `sources.mock.enabled=true`

**4. `AwinProductSourceClient`**
- Placeholder only — `discoverProducts()` logs a warning and returns an empty list
- Enabled via `sources.awin.enabled=true`

**5. `AmazonPaApiClient`**
- Added `@ConditionalOnProperty(name = "sources.amazon.enabled", havingValue = "true")`
- Prevents startup failure when Amazon is disabled and `AMAZON_*` env vars are not set

**6. `ProductDiscoveryService`**
- Now accepts `List<ProductSourceClient>` — Spring auto-injects all enabled source beans
- ASIN deduplication with `LinkedHashMap.putIfAbsent` preserved across all sources
- No longer holds the keyword list (moved to `AmazonProductSourceClient`)

**7. `application.properties`**
- Added `sources.amazon.enabled=false`, `sources.mock.enabled=true`, `sources.awin.enabled=false`
- Default config runs with mock data — safe to start locally without credentials

### No changes to
- Scoring, validation, Telegram publisher, deduplication, scheduler, entities, Docker

---

## Session: 2026-06-19 — Documentation restructure

### Changes applied

**1. `README.md` — rewritten as public GitHub README**
- Project overview and goal
- Current status checklist (what works, what is pending)
- Architecture summary with pipeline diagram
- Technology stack table
- Full setup instructions (env vars, Docker, build, run, tests)
- Roadmap

**2. `CLAUDE.md` — consolidated as the authoritative Claude Code guide**
- All implementation rules, architecture decisions, and MVP scope moved here
- Updated architecture table to include the `source` package and all four source classes
- Added product sources section documenting `ProductSourceClient` contract, per-source behavior, and `application.properties` toggles
- Non-goals updated to reflect that multi-source architecture is in scope; full retailer integrations are not
- Scheduler, scoring, Telegram, deduplication, and validation rules preserved in full

### No changes to
- Source code, entities, scheduler, Docker setup

---

## Pending

### Before first run
- [ ] Create `.env` from `.env.example`: copy and fill in real credentials
- [ ] Start database: `docker-compose up -d`
- [ ] (Alternative to Docker) Create PostgreSQL database manually: `CREATE DATABASE ozdeals;`
- [ ] Generate Maven wrapper if needed: `mvn wrapper:wrapper`

### Risks before first production deployment

| Risk | Severity | Notes |
|------|----------|-------|
| PA-API credentials not tested | High | Sign-up and approval can take days; test with a single keyword before enabling scheduler |
| Telegram bot not in channel | High | Bot must be added as admin to the channel before it can post |
| `ddl-auto=update` in production | Medium | Safe for MVP but can cause issues on complex schema changes; consider `validate` after first run |
| No Telegram rate limiting | Medium | Telegram allows ~30 msg/s; unlikely to hit with this volume but worth noting |
| `SavingBasis` not always present | Medium | `listPrice` may be null for many products, scoring falls back to discount-only logic |
| Max-pages=1 limits deal surface | Low | With 12 keywords × 10 items = 120 products/cycle; increase `max-pages` if deals are sparse |
| H2 test profile not updated | Low | Tests pass but don't cover new pagination/retry behavior; add unit tests before scaling |

### Recommendations before first production deployment
1. **Test locally first** — run with `max-pages=1`, real PA-API credentials, and a private Telegram test channel
2. **Monitor first cycle** — check logs for PA-API response structure; `SavingBasis` field behavior varies by product type
3. **Start with score threshold at 70** — lower to 60 only if no deals are being published after 48h
4. **Set `ddl-auto=validate`** after the schema stabilizes (first successful run creates tables)
5. **Add `max-pages=2`** only after confirming the base pipeline is stable
6. **VPS deployment** — use `docker-compose up -d` for PostgreSQL and run the JAR as a systemd service or second Docker container

### Included in MVP
- Multi-source architecture via `ProductSourceClient` abstraction
- `MockProductSourceClient` for local testing without credentials
- `AmazonProductSourceClient` disabled by default until credentials are available
- `AwinProductSourceClient` as a placeholder only

### Out of scope for MVP
- Full real implementation of multiple retailers
- Real Awin/JB Hi-Fi/AliExpress integrations
- Multi-affiliate production support
- Microservices, dashboards, analytics, ML, event-driven architecture

---

## Session: 2026-06-19 — Reliability fixes and ScoringService tests

### What changed

**1. Telegram publish reliability fix (R1)**
- `TelegramPublisherService.publish()` now returns `boolean`
- `sendPhoto()` and `sendMessage()` return `true` on successful delivery, `false` on any exception
- `DealsPipelineScheduler` now calls `savePost()` and increments `published` only when `publish()` returns `true`
- A failed Telegram delivery no longer marks the deal as posted; it will be retried on the next qualifying cycle

**2. HTTP timeouts (M2 / R3)**
- `AppConfig` now builds `RestTemplate` with `SimpleClientHttpRequestFactory`
- Connect timeout: 5 seconds
- Read timeout: 15 seconds
- Prevents indefinite thread blocking that would permanently deadlock the scheduler

**3. ScoringService unit tests (T1)**
- 16 test cases added in `ScoringServiceTest`
- Plain JUnit 5, no Spring context, no Mockito — `ScoringService` has no dependencies
- Covers: `discountPercent()` null/zero/normal cases; all `discountScore()` tier boundaries (10/20/30/40/50%); `historicalLowScore()` at/above/empty historical low; combined score below/at/above the 70 publish threshold

### Files modified
- `src/main/java/com/ozdeals/bot/service/TelegramPublisherService.java`
- `src/main/java/com/ozdeals/bot/scheduler/DealsPipelineScheduler.java`
- `src/main/java/com/ozdeals/bot/config/AppConfig.java`
- `src/test/java/com/ozdeals/bot/service/ScoringServiceTest.java` (new)
- `PROGRESS.md`

### Risks
- HTTP timeout change affects both Amazon PA-API and Telegram calls, as they share the same `RestTemplate` bean. The 15-second read timeout is generous for Telegram but may be short under PA-API throttle-and-retry scenarios; the retry backoff delays (1s/2s/4s) are unaffected since they happen between requests, not within one.
- A failed Telegram delivery now leaves the deal unposted and eligible for retry on the next cycle. If Telegram is intermittently failing, qualifying deals may be published later than expected, but this is strictly safer than the previous behavior of silently dropping them.

### Pending work
- `ValidationService` unit tests — next highest-priority testing gap
- `ProductDiscoveryService` unit tests — ASIN deduplication logic across sources is untested
- `TelegramPublisherService.buildMessage()` is still private and untestable without restructuring
- `DealsPipelineScheduler` still holds direct `PostRepository` dependency (deduplication in scheduler, not a service)
- `TelegramPublisherService` credentials still have no defaults — app fails to start locally without them

### Next recommended step
Add unit tests for `ValidationService` — no external dependencies, all cases are straightforward, and validation is the gate that determines which products enter price tracking and scoring.
