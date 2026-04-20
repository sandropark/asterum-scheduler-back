CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE events
    ADD CONSTRAINT no_overlapping_events_at_same_location
        EXCLUDE USING gist (
        location_id WITH =,
        tsrange(start_time, end_time, '[)') WITH &&
        ) WHERE (location_id IS NOT NULL);
