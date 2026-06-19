# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Mission

This is a **validation MVP system**.

The goal is to build an automated Amazon Australia deals bot that:

- discovers products via Amazon PA-API
- tracks price history
- detects real discounts
- publishes selected deals to Telegram using affiliate links

## IMPORTANT

This is NOT a scalable architecture project.

This is NOT a production enterprise system.

This is a VALIDATION SYSTEM.

The goal is:

> Validate if automated Amazon affiliate deals can generate clicks and sales.

## Core System Idea

The system runs continuously:

1. Discover products from enabled sources
2. Store product data
3. Track price history over time
4. Detect price drops
5. Score deals using simple logic
6. Filter only good opportunities
7. Publish to Telegram automatically

## System Flow

```
ProductSourceClient implementations
→ ProductDiscoveryService (aggregate + deduplicate by ASIN)
→ PriceTrackingService (upsert product, record price)
→ ValidationService (price + image + affiliate link)
→ ScoringService (score ≥ 70 to qualify)
→ TelegramPublisherService (format + send)
```

## Product Sources

Product discovery is abstracted behind the `ProductSourceClient` interface (`com.ozdeals.bot.source`).

Enable or disable each source in `application.properties`:

```properties
sources.amazon.enabled=false
sources.mock.enabled=true
sources.awin.enabled=false
```

`ProductDiscoveryService` receives the enabled sources as `List<ProductSourceClient>` via Spring injection — no conditional logic needed in the service itself.

### AmazonProductSourceClient

- Delegates to `AmazonPaApiClient` (rate limiting, retry, pagination)
- Searches 12 fixed keywords; collects results into a flat list
- Requires `AMAZON_ACCESS_KEY`, `AMAZON_SECRET_KEY`, `AMAZON_PARTNER_TAG` in environment
- Both `AmazonProductSourceClient` and `AmazonPaApiClient` are conditional on `sources.amazon.enabled=true` — neither bean is created when Amazon is disabled, so missing env vars do not cause startup failure

### MockProductSourceClient

- Returns hardcoded `DiscoveredProduct` objects for local testing
- Use when Amazon credentials are not available
- Safe to run with no external dependencies

### AwinProductSourceClient

- Placeholder only — logs a warning and returns an empty list
- Not implemented

### Fixed keyword list (Amazon source)

iphone, samsung, sony, headphones, tv, laptop, gaming monitor, air fryer, protein, creatine, fitness, smartwatch

No advanced discovery engine required. No dynamic keyword expansion.

## Database

Use PostgreSQL with Spring Data JPA. No manual SQL required.

### Entities

**Product**
- asin (primary key)
- title, brand, category, image_url, created_at

**PriceHistory**
- id, asin, price, timestamp
- Index on `asin`

**Post**
- id, asin, price, posted_at, message_hash
- Index on `asin` and `posted_at`

## Scoring System

```
Final Score = Discount Score (0–50) + Historical Low Score (0–50)
```

### Discount Score (0–50)

Based on percentage discount:

- small discount → low score
- medium discount → medium score
- high discount → high score

### Historical Low Score (0–50)

Based on comparison with lowest recorded price:

- at historical low → max score
- near low → medium score
- far from low → low score

### Publish Rule

Only publish if:

```
Score ≥ 70
```

## Deduplication Rules

- Use ASIN as unique identifier
- Do not post same product within 24 hours
- Store last post timestamp per ASIN
- ASIN deduplication also applied across sources in `ProductDiscoveryService` using `LinkedHashMap.putIfAbsent`

## Validation Rules

Before posting:

- product must be valid
- price must exist
- image must exist
- affiliate link must exist

If invalid → discard. Validation runs before price history is recorded to keep the database clean.

## Telegram Publisher

### Message Format

```
🔥 30% OFF

Product Title

💰 Now: $99
💸 Was: $149

👉 Affiliate Link
```

### Rules

- English only
- Simple format
- No reviews
- No commentary
- No extra marketing text
- Include image if available (`sendPhoto`); fall back to `sendMessage` if no image

## Scheduler

- Runs every 10 minutes (`cron: 0 */10 * * * *`)
- Executes the full pipeline automatically
- Overlap prevention: uses `AtomicBoolean` — skips run if previous one is still in progress
- `running` flag always reset in `finally` block

## Technology Stack

- Java 21+
- Spring Boot 3
- Spring Data JPA
- Spring Web
- PostgreSQL
- Spring Scheduler

## Build & Run

```bash
# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName

# Run a single test method
./mvnw test -Dtest=ClassName#methodName
```

## Architecture

Modular Monolith (SIMPLE). Single Spring Boot application with distinct classes:

| Layer | Class | Responsibility |
|---|---|---|
| Source | `ProductSourceClient` | Interface — contract for all product source implementations |
| Source | `AmazonProductSourceClient` | Queries Amazon PA-API using fixed keyword list |
| Source | `MockProductSourceClient` | Returns hardcoded products for local testing |
| Source | `AwinProductSourceClient` | Placeholder — not implemented |
| Service | `ProductDiscoveryService` | Aggregates all enabled sources, deduplicates by ASIN |
| Service | `PriceTrackingService` | Upserts products, stores price snapshots, retrieves historical low |
| Service | `ScoringService` | Computes deal score (0–100); threshold ≥ 70 to publish |
| Service | `ValidationService` | Ensures product has price, image, and affiliate link |
| Service | `TelegramPublisherService` | Formats and sends messages to Telegram channel |
| Scheduler | `DealsPipelineScheduler` | Orchestrates the full pipeline on a cron schedule |
| Amazon | `AmazonPaApiClient` | Raw PA-API caller: rate limiting, retry, exponential backoff, pagination |
| Amazon | `AwsV4Signer` | Manual AWS Signature V4 — no SDK dependency |

## Non-Goals

DO NOT implement:

- microservices
- dashboards
- analytics
- machine learning
- full multi-retailer integrations (Awin/JB Hi-Fi/AliExpress real implementation)
- event-driven architecture
- complex scoring systems

## Success Criteria

The MVP is successful if:

- products are discovered automatically
- price history is stored
- deals are detected
- posts are published to Telegram
- affiliate links generate clicks/sales

## Final Note

Keep the system minimal. Do NOT overengineer.

Focus on:

> first real deal posted → first real click → first real sale
