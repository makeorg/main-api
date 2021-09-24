BEGIN;

ALTER TABLE operation_of_question
  ADD COLUMN action_date_text STRING(20),
  ADD COLUMN action_description STRING(150),
  ADD COLUMN result_date_text STRING(20),
  ADD COLUMN result_description STRING(150),
  ADD COLUMN workshop_date_text STRING(20),
  ADD COLUMN workshop_description STRING(150);

COMMIT;