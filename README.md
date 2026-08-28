# Payment Reconciliation Engine

An event-driven service that ingests payment provider webhooks, normalizes them into a
canonical event, and persists them to an idempotent ledger in Postgres — the foundation
for reconciling what a provider *says* happened against what actually settled.

Built with Java 21 and Spring Boot 4.1.1.

## Why

A payment provider's webhook is not the truth. Webhooks get retried, arrive out of order,
get dropped, and the settlement statement at end of day rarely matches them exactly —
fees are deducted, charges get reversed, some transactions never settle at all.

This service treats the webhook stream and the settlement statement as two independent
sources and is designed to reconcile them, surfacing every discrepancy as a reviewable item
rather than silently trusting either side.

## Architecture

```
Providers (Paystack)
      | webhook (HTTPS, signed)
      v
Webhook Gateway ......... signature verification (HMAC-SHA512), returns 200 fast
      | publish raw event
      v
Event Queue (Kafka) ..... durable, replayable, ordered per partition
      | consume (consumer group)
      v
Normalizer .............. provider payload -> canonical TransactionEvent
      | 
      v
Ledger / Transaction Engine
                          Postgres, idempotency-key unique constraint,
                          optimistic locking via @Version
      | triggers (event or scheduled)
      v
Reconciliation Engine ... match ledger against settlement records
```

The webhook endpoint does no database work. It verifies the signature, publishes the raw
payload to Kafka, and returns — so a slow database or a downstream outage can never cause
the provider to see a timeout and start retrying.

## Design decisions

**Idempotency at the database, not in application code.** `transactions` carries a unique
constraint on `(provider, provider_reference)`. A duplicate webhook — and providers *will*
retry — hits that constraint and is discarded. No read-then-write race, because the check
and the insert are the same operation.

**Money is never a floating point number.** `BigDecimal` in Java, `NUMERIC(19,4)` in
Postgres, everywhere. Paystack reports amounts in kobo (minor units); conversion to major
units happens once, in the normalizer.

**Raw payloads go on the queue, not parsed objects.** Kafka holds what the provider actually
sent. If normalization logic has a bug, the topic can be replayed against fixed code — the
original evidence is never discarded.

**Timestamps are instants.** `Instant` in Java, `TIMESTAMPTZ` in Postgres. No local times,
no ambiguity.

**Schema is versioned.** Flyway owns the schema; Hibernate is set to `ddl-auto: validate`
and is never permitted to alter it.

## Data model

| Table | Purpose |
|---|---|
| `transactions` | The ledger, built from provider webhooks. Unique on `(provider, provider_reference)`; versioned for optimistic locking. |
| `settlement_records` | The provider's official statement of what actually settled, including fees. |
| `reconciliation_items` | The review queue — the output of matching the two above. |

A reconciliation item carries a reason: `AMOUNT_MISMATCH`, `MISSING_WEBHOOK`,
`ORPHAN_TRANSACTION`, or `STATUS_MISMATCH`; and a status: `OPEN`, `INVESTIGATING`,
or `RESOLVED`. A check constraint guarantees every item references at least one side.




## Running

```bash
./mvnw spring-boot:run
```

