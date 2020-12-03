BEGIN;

ALTER TABLE operation_of_question
  ADD COLUMN votes_target INT NOT NULL DEFAULT 100000,
  ADD COLUMN votes_count INT NOT NULL DEFAULT 0,
  ADD COLUMN result_date DATE,
  ADD COLUMN workshop_date DATE,
  ADD COLUMN action_date DATE;

COMMIT;