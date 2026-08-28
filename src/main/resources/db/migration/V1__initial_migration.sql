-- ============================================
-- V1__initial_migration.sql
-- ============================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. TRANSACTIONS (the ledger, built from webhooks)
CREATE TABLE transactions (
                              id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              provider            VARCHAR(50)     NOT NULL,
                              provider_reference  VARCHAR(255)    NOT NULL,
                              amount              NUMERIC(19, 4)  NOT NULL,
                              currency            VARCHAR(3)      NOT NULL,
                              status              VARCHAR(30)     NOT NULL,
                              customer_reference  VARCHAR(255),
                              received_at         TIMESTAMPTZ     NOT NULL,
                              version             BIGINT          NOT NULL DEFAULT 0,

                              CONSTRAINT uq_transactions_provider_reference
                                  UNIQUE (provider, provider_reference)
);

CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_received_at ON transactions (received_at);


-- 2. SETTLEMENT_RECORDS (the provider's official statement)
CREATE TABLE settlement_records (
                                    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                    provider            VARCHAR(50)     NOT NULL,
                                    provider_reference  VARCHAR(255)    NOT NULL,
                                    amount              NUMERIC(19, 4)  NOT NULL,
                                    fee                 NUMERIC(19, 4),
                                    currency            VARCHAR(3)      NOT NULL,
                                    status              VARCHAR(30)     NOT NULL,
                                    settled_at          TIMESTAMPTZ     NOT NULL,
                                    imported_at         TIMESTAMPTZ     NOT NULL,

                                    CONSTRAINT uq_settlement_provider_reference
                                        UNIQUE (provider, provider_reference)
);

CREATE INDEX idx_settlement_settled_at ON settlement_records (settled_at);


-- 3. RECONCILIATION_ITEMS (the review queue — output of matching the two above)
CREATE TABLE reconciliation_items (
                                      id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      transaction_id          UUID,
                                      settlement_record_id    UUID,
                                      reason                  VARCHAR(30)  NOT NULL,
                                      status                  VARCHAR(20)  NOT NULL,
                                      notes                   TEXT,
                                      created_at              TIMESTAMPTZ  NOT NULL,
                                      resolved_at             TIMESTAMPTZ,

                                      CONSTRAINT fk_reconciliation_transaction
                                          FOREIGN KEY (transaction_id) REFERENCES transactions (id),

                                      CONSTRAINT fk_reconciliation_settlement
                                          FOREIGN KEY (settlement_record_id) REFERENCES settlement_records (id),

                                      CONSTRAINT chk_reconciliation_has_reference
                                          CHECK (transaction_id IS NOT NULL OR settlement_record_id IS NOT NULL)
);

CREATE INDEX idx_reconciliation_status ON reconciliation_items (status);
CREATE INDEX idx_reconciliation_reason ON reconciliation_items (reason);