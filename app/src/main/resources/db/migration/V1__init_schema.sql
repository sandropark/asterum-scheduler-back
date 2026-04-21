CREATE TABLE events
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255) NOT NULL,
    start_at   TIMESTAMP    NOT NULL,
    end_at     TIMESTAMP    NOT NULL,
    rrule      VARCHAR(500),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE events_instances
(
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT       NOT NULL REFERENCES events (id),
    title      VARCHAR(255),
    start_at   TIMESTAMP    NOT NULL,
    end_at     TIMESTAMP    NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_events_instances_event
    ON events_instances (event_id) WHERE deleted_at IS NULL;
