BEGIN;

ALTER TABLE operation_of_question ADD COLUMN IF NOT EXISTS description_image STRING;

COMMIT;
