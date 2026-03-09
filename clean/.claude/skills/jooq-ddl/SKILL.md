---
name: jooq-ddl
description: >
  jOOQ DDL-based code generation and runtime configuration. Use when setting up jOOQ codegen,
  writing blocking JDBC repository implementations, or troubleshooting identifier case issues.
---

# jOOQ Setup Guide

## Identifier Case Problem (Common Pitfall)

jOOQ DDLDatabase generates UPPERCASE names. PostgreSQL stores unquoted as lowercase.
At runtime, quoted `"ORDERS"` → PostgreSQL: `relation does not exist`.

Fix in `JooqConfig.kt`:
```kotlin
Settings()
    .withRenderQuotedNames(RenderQuotedNames.NEVER)
    .withRenderNameCase(RenderNameCase.LOWER)
```

## Code Generation

From Flyway SQL files using `DDLDatabase` — no live DB needed.

```
./gradlew :infrastructure:jooqCodegen
```

Migration files: `infrastructure/src/main/resources/db/migration/*.sql`

## Repository Pattern (jOOQ + JDBC, blocking on virtual threads)

```kotlin
@Repository
class OrderRepositoryImpl(private val dsl: DSLContext) : OrderRepository {

    override fun findById(id: OrderId): Either<OrderError, Order> =
        either {
            val record = Either.catch {
                dsl.selectFrom(ORDERS)
                    .where(ORDERS.ID.eq(id.value))
                    .fetchOne()
            }.mapLeft { OrderError.RepositoryError("findById failed: ${it.message}", it) }
                .bind()

            ensureNotNull(record) { OrderError.NotFound(id) }
            record.toDomain(loadItems(id.value))
        }

    override fun save(order: Order): Either<OrderError, Order> =
        Either.catch {
            dsl.insertInto(ORDERS)
                .set(ORDERS.CUSTOMER_ID, order.customerId.value)
                .set(ORDERS.STATUS, order.status.toDbValue())
                // ...
                .onConflict(ORDERS.ID).doUpdate()
                .set(ORDERS.STATUS, excluded(ORDERS.STATUS))
                // ...
                .returning()
                .fetchOne()!!
                .toDomain(order.items)
        }.mapLeft { OrderError.RepositoryError("save failed: ${it.message}", it) }
}
```

## PostgreSQL-Specific Syntax in Migrations

```sql
-- [jooq ignore start]
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- [jooq ignore stop]
```

## Upsert Pattern (MUST follow)

`onConflict()` must reference a column with a UNIQUE constraint — typically a natural key, NOT the auto-generated ID.

```kotlin
// WRONG — ID is auto-generated (BIGSERIAL), new rows never conflict on ID
.onConflict(TABLE.ID).doUpdate()

// CORRECT — use a natural key or unique constraint column
.onConflict(TABLE.SUBSCRIPTION_ID).doUpdate()
```

Also ensure the `doUpdate()` block sets ALL updatable columns — missing columns won't be updated on conflict.

## `!!` on jOOQ Record Fields

- `!!` on NOT NULL columns is ALLOWED (Java interop returns `T?` even for NOT NULL columns)
- `!!` on NULLABLE columns is FORBIDDEN — use `ensureNotNull()` inside `either {}`

## Clean Architecture Placement

- Generated Tables/Records: `infrastructure` only
- Never import jOOQ from `domain` or `application`

See the `ca-kotlin` skill for Clean Architecture placement rules.
