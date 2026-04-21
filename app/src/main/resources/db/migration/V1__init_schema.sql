CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE teams
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    created_by BIGINT       NOT NULL,
    updated_by BIGINT       NOT NULL
);

CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    team_id    BIGINT REFERENCES teams (id),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    created_by BIGINT       NOT NULL,
    updated_by BIGINT       NOT NULL
);

CREATE TABLE locations
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    capacity   INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    created_by BIGINT       NOT NULL,
    updated_by BIGINT       NOT NULL
);

CREATE TABLE events
(
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    start_time  TIMESTAMP    NOT NULL,
    end_time    TIMESTAMP    NOT NULL,
    location_id BIGINT,
    notes       VARCHAR(500),
    rrule       VARCHAR(500),
    creator_id  BIGINT       NOT NULL,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  BIGINT       NOT NULL,
    updated_by  BIGINT       NOT NULL
);
CREATE INDEX idx_events_active ON events (id) WHERE deleted_at IS NULL;

CREATE TABLE event_participants
(
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT    NOT NULL REFERENCES events (id),
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by BIGINT    NOT NULL,
    updated_by BIGINT    NOT NULL,
    CONSTRAINT uq_event_participants UNIQUE (event_id, user_id)
);

CREATE TABLE event_overrides
(
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT    NOT NULL REFERENCES events (id),
    override_date DATE      NOT NULL,
    title         VARCHAR(255) NOT NULL,
    start_time    TIMESTAMP NOT NULL,
    end_time      TIMESTAMP NOT NULL,
    location_id   BIGINT,
    notes         VARCHAR(500),
    deleted_at    TIMESTAMP,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    created_by    BIGINT    NOT NULL,
    updated_by    BIGINT    NOT NULL,
    CONSTRAINT uq_event_overrides UNIQUE (event_id, override_date)
);
CREATE INDEX idx_event_overrides_active
    ON event_overrides (event_id, override_date) WHERE deleted_at IS NULL;

CREATE TABLE event_instances
(
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT      NOT NULL REFERENCES events (id),
    override_id BIGINT      REFERENCES event_overrides (id),
    location_id BIGINT,
    date_key    DATE        NOT NULL,
    start_time  TIMESTAMP   NOT NULL,
    end_time    TIMESTAMP   NOT NULL,
    status      VARCHAR(20) NOT NULL,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    created_by  BIGINT      NOT NULL,
    updated_by  BIGINT      NOT NULL
);
CREATE INDEX idx_event_instances_location_range
    ON event_instances
    USING gist (location_id, tsrange(start_time, end_time, '[)'))
    WHERE deleted_at IS NULL;
CREATE INDEX idx_event_instances_event
    ON event_instances (event_id) WHERE deleted_at IS NULL;

CREATE TABLE sync_status
(
    date_key     DATE PRIMARY KEY,
    is_generated BOOLEAN NOT NULL DEFAULT FALSE
);
