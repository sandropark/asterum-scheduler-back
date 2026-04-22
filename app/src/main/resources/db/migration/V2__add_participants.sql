CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    is_team    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX idx_users_email
    ON users (email) WHERE deleted_at IS NULL;

CREATE TABLE user_memberships
(
    id         BIGSERIAL PRIMARY KEY,
    team_id    BIGINT    NOT NULL REFERENCES users (id),
    member_id  BIGINT    NOT NULL REFERENCES users (id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_user_memberships_team_member
    ON user_memberships (team_id, member_id);
CREATE INDEX idx_user_memberships_member
    ON user_memberships (member_id);

CREATE TABLE event_participants
(
    id         BIGSERIAL PRIMARY KEY,
    event_id   BIGINT    NOT NULL REFERENCES events (id),
    user_id    BIGINT    NOT NULL REFERENCES users (id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_event_participants_event_user
    ON event_participants (event_id, user_id);
CREATE INDEX idx_event_participants_user
    ON event_participants (user_id);

CREATE TABLE instance_participants
(
    id          BIGSERIAL PRIMARY KEY,
    instance_id BIGINT    NOT NULL REFERENCES events_instances (id),
    user_id     BIGINT    NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uq_instance_participants_instance_user
    ON instance_participants (instance_id, user_id);
CREATE INDEX idx_instance_participants_user
    ON instance_participants (user_id);

ALTER TABLE events_instances
    ADD COLUMN has_override_participants BOOLEAN NOT NULL DEFAULT FALSE;
