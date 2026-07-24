# CLAUDE.md

This file provides authoritative guidance to Claude Code when working in this repository.

## Documentation Rule

`CLAUDE.md` contains stable product, architecture, and current-phase decisions.

- Do not use this file as a session log.
- Do not append changed-file lists, test results, temporary notes, or pending tasks here.
- Record implementation history and session progress in `PROGRESS.md`.
- Update this file only when an approved product or architecture decision changes.
- Prefer targeted edits over full rewrites.

## Public Product Name

The public product name is:

> **Oz Radar**

Do not rename existing Java packages, database names, repository names, or internal classes only for branding.

## Long-Term Mission

Oz Radar is a validation MVP for affiliate deal discovery and publishing in Australia.

The long-term product is an automated system that:

1. discovers products from approved official sources;
2. stores product and price data;
3. tracks price history;
4. detects relevant offers;
5. scores and validates deals;
6. avoids duplicate or excessive republication;
7. publishes selected deals to Telegram with affiliate links.

The business validation goal is:

> prove that affiliate deal publishing can generate real clicks and qualifying sales.

The long-term target remains automation.

## Current Delivery Phase

The project is currently in a temporary **manual-first launch phase**.

This phase exists because Amazon automatic API access is not yet available.

The current objective is:

> put Oz Radar into production now, publish real Amazon Australia deals manually, build an audience, and generate the qualifying sales required for future API access.

Manual input is a bootstrap phase, not a replacement for the automated product vision.

When Amazon API access becomes available, automatic discovery will be added back through the existing source architecture while manual publishing remains supported as an operational fallback.

## Current Phase Flow

For the first production release:

1. The administrator finds an Amazon Australia product manually.
2. The administrator generates the affiliate link using an official Amazon Associates tool.
3. The administrator creates the deal through a private Telegram conversation with the bot.
4. The application validates and persists the deal.
5. The application calculates prices, savings, discount percentage, and internal score.
6. The application shows a private preview.
7. The administrator explicitly confirms publication.
8. The application publishes the deal to the public Oz Radar Telegram channel.
9. The same persisted deal is available through public Spring Boot pages.
10. The application manages editing, expiration, archiving, and republication.

The public Telegram channel is the primary user experience.

## Stable Architecture Decisions

### Product Sources

Automatic discovery remains abstracted through:

```java
ProductSourceClient
```

`ProductSourceClient` is for automatic discovery sources only.

Future automatic implementations may include:

```text
AmazonCreatorsApiProductSourceClient
EbayBrowseApiProductSourceClient
AwinProductSourceClient
```

The manual Telegram workflow must not implement `ProductSourceClient`.

Do not introduce a parallel abstraction such as:

```text
MarketplaceAdapter
AmazonMarketplaceAdapter
EbayMarketplaceAdapter
```

The current source abstraction is sufficient.

### Product Identity

Product identity is:

```text
ProductSource + externalId
```

Do not return to ASIN as the universal database key.

For Amazon:

```text
source = AMAZON
externalId = ASIN
```

ASIN is Amazon-specific only.

### Existing Components to Preserve

Reuse the existing architecture wherever practical:

- `ProductSourceClient`
- `ProductDiscoveryService`
- `Product`
- `ProductSource`
- `DiscoveredProduct`
- `PriceHistory`
- `Post`
- `PriceTrackingService`
- `ScoringService`
- `ValidationService`
- `TelegramMessageFormatter`
- `TelegramPublisherService`
- `DealsPipelineScheduler`

Do not duplicate existing responsibilities without a clear technical reason.

## Manual Deal Workflow

The same Telegram bot has two responsibilities:

1. receive private administrator commands;
2. publish approved deals to the public Oz Radar channel.

Use Telegram long polling for the first MVP.

Required environment variables:

```text
TELEGRAM_BOT_TOKEN
TELEGRAM_CHANNEL_ID
TELEGRAM_ADMIN_USER_ID
```

Only `TELEGRAM_ADMIN_USER_ID` may create, edit, publish, expire, republish, archive, or cancel deals.

Administrative progress must be persisted where practical. Do not keep a complete draft only in memory.

### Main Commands

```text
/newdeal
/deals
```

### Deal Creation Flow

The bot should collect:

1. normal Amazon product URL;
2. ASIN extracted from the URL, or requested manually when extraction fails;
3. official affiliate URL;
4. title;
5. short description;
6. current price;
7. original price;
8. product image by Telegram upload or external image URL;
9. optional expiration date and time;
10. preview;
11. explicit Publish, Edit, or Cancel action.

## Amazon URL and ASIN

Support common Amazon Australia URL formats, including:

```text
/dp/{ASIN}
/gp/product/{ASIN}
```

ASIN format:

```text
10 alphanumeric characters
```

Store separately:

```text
sourceUrl
externalId
affiliateUrl
```

Do not rebuild, cloak, or append affiliate parameters to the supplied affiliate URL.

Use the exact URL generated by the affiliate tool.

## Deal Lifecycle

The manual workflow needs a dedicated deal lifecycle.

Statuses:

```java
DRAFT
PUBLISHED
EXPIRED
ARCHIVED
```

If the existing entities cannot represent this lifecycle clearly, introduce a `Deal` entity associated with `Product`.

A deal may contain:

- `id`
- `product`
- `sourceUrl`
- `affiliateUrl`
- `shortDescription`
- `currentPrice`
- `originalPrice`
- `imageUrl`
- `telegramImageFileId`
- `slug`
- `status`
- `expiresAt`
- `createdAt`
- `updatedAt`
- `publishedAt`

Reuse `Post` for Telegram publication history and metadata where practical.

Possible publication metadata:

- deal reference;
- Telegram chat ID;
- Telegram message ID;
- published price;
- publication timestamp;
- message hash.

## Validation Rules

Before publication:

- source must be present;
- external ID must be valid;
- title must not be blank;
- short description must not be blank;
- current price must be greater than zero;
- original price must be greater than current price;
- affiliate URL must be present and valid;
- source URL must be present and valid;
- image is mandatory;
- duplicate checks must pass;
- cooldown checks must pass;
- publication must be explicitly confirmed by the administrator.

Calculate:

```text
saving amount = original price - current price
discount percentage = saving amount / original price × 100
```

## Scoring

Keep the existing scoring model:

```text
Final Score = Discount Score + Historical Low Score
```

For automatic discovery, the configured score threshold may continue to control publication.

For manually confirmed deals:

- calculate the score;
- show it in the private preview;
- do not block publication only because the score is below the automatic threshold;
- continue enforcing validation, duplicates, and cooldown.

## Price History

Continue storing price history by internal product ID.

A valid manually created deal should record its current price.

Do not return to ASIN-based price history.

## Images

Every published deal must have an image.

Support:

1. Telegram image upload;
2. external image URL.

For Telegram uploads:

- retain the Telegram file ID;
- download a persistent copy;
- store it through an image-storage abstraction;
- expose the stored image to the public Spring Boot page.

Use an abstraction such as:

```java
DealImageStorage
```

Provide a filesystem implementation for the MVP.

Configure storage through:

```text
DEAL_IMAGES_DIRECTORY
```

Do not hardcode an operating-system-specific path.

## Telegram Publication

Reuse or extend `TelegramMessageFormatter`.

A published deal should contain:

```text
mandatory image
product title
short description
original price
current price
saving amount
discount percentage
direct affiliate URL
#ad
```

Prefer `sendPhoto` with a caption.

Store the Telegram message ID and chat ID returned by Telegram.

A successful publication must:

1. validate the deal;
2. create or reuse `Product` by `source + externalId`;
3. record price history;
4. calculate discount;
5. calculate score;
6. enforce duplicate and cooldown rules;
7. publish only after explicit administrator confirmation;
8. persist Telegram publication metadata;
9. change the deal status to `PUBLISHED`;
10. set `publishedAt`.

A failed Telegram delivery must not mark the deal as published.

## Editing, Republishing, and Expiration

A published deal may be:

- edited;
- marked as ended;
- republished;
- archived.

Editing a published deal should:

- update persisted deal data;
- update the public page automatically;
- edit the existing Telegram caption using the stored Telegram message ID.

Republishing should:

- create a new Telegram publication;
- preserve publication history;
- respect cooldown;
- never silently bypass duplicate rules.

`expiresAt` is optional.

When a published deal passes `expiresAt`:

1. change status to `EXPIRED`;
2. edit the Telegram publication to clearly state that the deal has ended;
3. mark the public page as ended;
4. keep the Telegram message;
5. keep price history and publication records.

The expiration scheduler must be independent from automatic product discovery.

## Public Spring Boot Pages

Public pages are part of the same Spring Boot application.

Do not create a separate frontend project.

Use server-rendered templates such as Thymeleaf.

Required routes:

```text
GET /
GET /deals/{slug}
```

### Homepage

The homepage should:

- display Oz Radar branding;
- explain briefly what Oz Radar does;
- list recent published deals;
- visibly identify expired deals;
- link to each deal page;
- contain a clear affiliate disclosure.

### Deal Page

The deal page should display:

- product image;
- title;
- short description;
- current price;
- original price;
- calculated saving and discount;
- retailer name;
- publication date;
- expiration state;
- visible affiliate disclosure;
- direct affiliate button.

Do not automatically redirect visitors.

Do not hide the affiliate destination.

Public pages must use the same persisted deal data used by Telegram.

A custom domain is not required for local development. The deployed application may initially use a hosting-provider URL. A custom domain may be connected later.

## Automatic Discovery During the Manual Phase

For the current manual-first launch:

```properties
sources.amazon.enabled=false
sources.mock.enabled=false
sources.awin.enabled=false
```

The automatic discovery scheduler must not run when no automatic source is enabled.

Do not remove existing source clients only because they are disabled.

## Future Amazon Automation

The repository contains legacy Amazon PA-API code.

Do not expand or build new functionality on that legacy integration.

Keep it isolated and disabled unless a separate approved task requests cleanup or migration.

When the account receives access to the current official Amazon product API, implement automatic discovery through a new `ProductSourceClient`.

At that point, the target flow becomes:

```text
official Amazon API
→ Amazon ProductSourceClient
→ existing product pipeline
→ scoring and validation
→ Telegram publication
```

The manual Telegram workflow must remain available after automation is introduced.

This transition should require an update to the current-phase section of this file, not a complete rewrite of the project mission or architecture.

## Awin and eBay

Awin is paused, not deleted.

Do not implement Awin in the current phase.

eBay is a future source after the Amazon manual MVP is live.

Do not add eBay implementation work unless a separate approved task requests it.

## Security

Never commit:

- Telegram bot tokens;
- database passwords;
- Amazon credentials;
- affiliate credentials;
- private production configuration.

Use environment variables.

Reject or ignore administrative commands from unauthorized Telegram users.

Do not expose private administration capabilities publicly.

## Database

Use PostgreSQL with Spring Data JPA.

Tests may use H2.

Avoid treating `ddl-auto=update` as the permanent production migration strategy.

For production-facing schema changes, prefer explicit database migrations when introduced by the implementation task.

## Build and Test

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
./mvnw test
./mvnw test -Dtest=ClassName
./mvnw test -Dtest=ClassName#methodName
```

Do not delete or weaken existing tests to make new work pass.

Use unit tests where possible.

## Documentation Responsibilities

### `CLAUDE.md`

Stable mission, architecture, and current phase.

Do not place session history here.

### `PROGRESS.md`

After every implementation session, record:

- decisions made;
- files created or modified;
- database changes;
- commands used;
- tests added;
- complete test result;
- unresolved risks;
- pending work;
- recommended next step.

### `README.md`

Public project documentation.

Update it when implemented public behavior, setup, supported features, or roadmap changes.

Do not use it as a private session log.

## Non-Goals

Do not overengineer.

Do not introduce:

- microservices;
- event sourcing;
- CQRS;
- multiple deployable applications;
- generic plugin frameworks;
- parallel marketplace abstraction layers;
- dashboards;
- analytics systems;
- machine learning;
- unnecessary cloud services.

## Current Phase Success Criteria

The manual launch phase succeeds when:

- the administrator can create an Amazon deal through Telegram;
- the bot shows a private preview;
- publication requires explicit confirmation;
- the public channel receives the deal with image and affiliate link;
- the deal and publication metadata are persisted;
- duplicate and cooldown rules work;
- the public deal page is available;
- published deals can be edited, expired, republished, and archived;
- the project generates real clicks and its first qualifying Amazon sales.

## Long-Term Success Criteria

The product direction remains successful automation:

- products are discovered from approved official APIs;
- price history is stored;
- relevant deals are detected and scored;
- qualifying deals are published automatically;
- manual operation remains available as a fallback;
- affiliate links generate consistent clicks and sales.

## Final Principle

Keep the system minimal and practical.

Current phase:

> launch manually → build audience → generate qualifying sales

Long-term direction:

> official API automation → scalable deal discovery → consistent affiliate revenue
