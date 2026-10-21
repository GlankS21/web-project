-- notification-service schema: one e-mail per consumed event

CREATE TABLE notifications
(
    id         UUID PRIMARY KEY,
    -- id of the event that triggered the e-mail; makes redelivered events harmless
    event_id   UUID          NOT NULL,
    type       VARCHAR(30)   NOT NULL,
    user_id    UUID,
    recipient  VARCHAR(255)  NOT NULL,
    reference  VARCHAR(100),
    subject    VARCHAR(255)  NOT NULL,
    body       TEXT          NOT NULL,
    status     VARCHAR(20)   NOT NULL,
    attempts   INT           NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    sent_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    version    BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_notifications_event UNIQUE (event_id),
    CONSTRAINT ck_notifications_type CHECK (type IN ('WELCOME', 'ORDER_PLACED', 'ORDER_CONFIRMED', 'ORDER_CANCELLED')),
    CONSTRAINT ck_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_status_created ON notifications (status, created_at DESC);
