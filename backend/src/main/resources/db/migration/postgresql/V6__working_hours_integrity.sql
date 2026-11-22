-- Working hours are a wall-clock schedule: one row per doctor/day/start time.
-- Earlier builds could insert the same shift more than once when two schedule
-- replacements raced. Keep the oldest row, then let the database enforce it.
DELETE FROM working_hours a
USING working_hours b
WHERE a.id > b.id
  AND a.doctor_id = b.doctor_id
  AND a.day_of_week = b.day_of_week
  AND a.start_time = b.start_time;

ALTER TABLE working_hours
    ADD CONSTRAINT uk_working_hours_doctor_day_start
        UNIQUE (doctor_id, day_of_week, start_time);

ALTER TABLE working_hours
    ADD CONSTRAINT chk_working_hours_range
        CHECK (start_time < end_time AND slot_minutes > 0);
