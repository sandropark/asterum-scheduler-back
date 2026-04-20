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
    creator_id  BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    created_by  BIGINT       NOT NULL,
    updated_by  BIGINT       NOT NULL
);

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
