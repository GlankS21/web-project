-- payment-service schema: one payment per order and the transactional outbox

CREATE TABLE payments
(
    id                    UUID PRIMARY KEY,
    order_id              UUID        NOT NULL,
    order_number          VARCHAR(30) NOT NULL,
    user_id               UUID,
    amount                NUMERIC(14, 2),
    currency              VARCHAR(3),
    method                VARCHAR(20),
    status                VARCHAR(20) NOT NULL,
    transaction_id        VARCHAR(100),
    failure_reason        VARCHAR(500),
    refund_transaction_id VARCHAR(100),
    refunded_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_status CHECK (status IN ('SUCCEEDED', 'FAILED', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT ck_payments_method CHECK (method IN ('COD', 'CARD', 'E_WALLET')),
    -- only the marker of an order cancelled before payment has no amount
    CONSTRAINT ck_payments_amount CHECK (status = 'CANCELLED' OR (amount > 0 AND currency IS NOT NULL))
);

CREATE INDEX idx_payments_user_created ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_status_created ON payments (status, created_at DESC);

-- Events written in the same transaction as the state change, relayed to RabbitMQ by a poller
CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    aggregate_id   VARCHAR(100)  NOT NULL,
    exchange       VARCHAR(255)  NOT NULL,
    routing_key    VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(255)  NOT NULL,
    payload        TEXT          NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ   NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000)
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_events_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;
