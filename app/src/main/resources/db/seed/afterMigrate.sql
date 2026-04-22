TRUNCATE instance_participants, event_participants, user_memberships,
         events_instances, events, users RESTART IDENTITY CASCADE;

INSERT INTO users (email, name, is_team, created_at, updated_at) VALUES
    ('alice@dev.com',      'Alice',      false, now(), now()),
    ('bob@dev.com',        'Bob',        false, now(), now()),
    ('charlie@dev.com',    'Charlie',    false, now(), now()),
    ('dave@dev.com',       'Dave',       false, now(), now()),
    ('team-alpha@dev.com', 'Team Alpha', true,  now(), now()),
    ('team-beta@dev.com',  'Team Beta',  true,  now(), now());

INSERT INTO user_memberships (team_id, member_id, created_at, updated_at)
SELECT t.id, m.id, now(), now()
FROM users t
JOIN users m ON (
    (t.email = 'team-alpha@dev.com' AND m.email IN ('alice@dev.com', 'bob@dev.com'))
 OR (t.email = 'team-beta@dev.com'  AND m.email IN ('charlie@dev.com', 'dave@dev.com'))
);

INSERT INTO events (title, start_at, end_at, rrule, created_at, updated_at) VALUES
    ('팀 미팅',
     date_trunc('month', now()) + interval '4 days' + interval '10 hours',
     date_trunc('month', now()) + interval '4 days' + interval '11 hours',
     null, now(), now()),
    ('점심 약속',
     date_trunc('month', now()) + interval '9 days' + interval '12 hours',
     date_trunc('month', now()) + interval '9 days' + interval '13 hours',
     null, now(), now()),
    ('주간 스탠드업',
     date_trunc('month', now()) + interval '9 hours',
     date_trunc('month', now()) + interval '9 hours 30 minutes',
     'FREQ=WEEKLY;COUNT=5;BYDAY=' || (ARRAY['SU','MO','TU','WE','TH','FR','SA'])[EXTRACT(DOW FROM date_trunc('month', now()))::int + 1],
     now(), now());

INSERT INTO events_instances (event_id, start_at, end_at, has_override_participants, created_at, updated_at)
SELECT id,
       date_trunc('month', now()) + interval '4 days' + interval '10 hours',
       date_trunc('month', now()) + interval '4 days' + interval '11 hours',
       false, now(), now()
FROM events WHERE title = '팀 미팅';

INSERT INTO events_instances (event_id, start_at, end_at, has_override_participants, created_at, updated_at)
SELECT id,
       date_trunc('month', now()) + interval '9 days' + interval '12 hours',
       date_trunc('month', now()) + interval '9 days' + interval '13 hours',
       false, now(), now()
FROM events WHERE title = '점심 약속';

INSERT INTO events_instances (event_id, start_at, end_at, has_override_participants, created_at, updated_at)
SELECT e.id,
       date_trunc('month', now()) + (n || ' days')::interval + interval '9 hours',
       date_trunc('month', now()) + (n || ' days')::interval + interval '9 hours 30 minutes',
       false, now(), now()
FROM events e, unnest(ARRAY[0, 7, 14, 21, 28]) AS n
WHERE e.title = '주간 스탠드업';

INSERT INTO event_participants (event_id, user_id, created_at, updated_at)
SELECT e.id, u.id, now(), now()
FROM events e, users u
WHERE e.title = '팀 미팅' AND u.email = 'team-alpha@dev.com';

INSERT INTO event_participants (event_id, user_id, created_at, updated_at)
SELECT e.id, u.id, now(), now()
FROM events e, users u
WHERE e.title = '점심 약속' AND u.email IN ('alice@dev.com', 'bob@dev.com');

INSERT INTO event_participants (event_id, user_id, created_at, updated_at)
SELECT e.id, u.id, now(), now()
FROM events e, users u
WHERE e.title = '주간 스탠드업' AND u.email = 'team-beta@dev.com';
