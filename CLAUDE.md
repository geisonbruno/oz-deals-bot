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

1. Discover products from Amazon Australia
2. Store product data
3. Track price history over time
4. Detect price drops
5. Score deals using simple logic
6. Filter only good opportunities
7. Publish to Telegram automatically

## System Flow

```
Amazon PA-API
→ Keyword Search
→ Normalize Products
→ Store Products
→ Store Price History
→ Simple Scoring
→ Deduplication Check
→ Telegram Publisher
```

## Data Source

Primary source: Amazon Product Advertising API (PA-API)

## Product Discovery

Use fixed keyword list:

- iphone
- samsung
- sony
- headphones
- tv
- laptop
- gaming monitor
- air fryer
- protein
- creatine
- fitness
- smartwatch

System must continuously query these keywords. No advanced discovery engine required.

## Database

Use PostgreSQL with Spring Data JPA. No manual SQL required.

### Entities

**Product**
- asin (primary key)
- title
- brand
- category
- image_url
- created_at

**PriceHistory**
- id
- asin
- price
- timestamp

**Post**
- id
- asin
- price
- posted_at
- message_hash

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

## Validation Rules

Before posting:

- product must be valid
- price must exist
- image must exist
- affiliate link must exist

If invalid → discard

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
- Include image if available

## Scheduler

- Runs every 10–15 minutes
- Executes full pipeline automatically

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

Modular Monolith (SIMPLE). Single Spring Boot application with distinct service classes:

| Service | Responsibility |
|---|---|
| `ProductDiscoveryService` | Queries Amazon PA-API using fixed keyword list |
| `PriceTrackingService` | Stores price snapshots, detects price drops |
| `ScoringService` | Computes deal score (0–100); threshold ≥ 70 to publish |
| `ValidationService` | Ensures product has price, image, and affiliate link |
| `TelegramPublisherService` | Formats and sends messages to Telegram channel |

## Non-Goals

DO NOT implement:

- microservices
- dashboards
- analytics
- machine learning
- multi-retailer support
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
