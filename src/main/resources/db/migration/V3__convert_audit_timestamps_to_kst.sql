UPDATE meeting
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE schedule_poll
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE location_poll
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE participant
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE schedule_vote
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE location_vote
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE nearby_place
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';

UPDATE nearby_place_hours
SET created_at = created_at + INTERVAL '9 hours',
    updated_at = updated_at + INTERVAL '9 hours';
