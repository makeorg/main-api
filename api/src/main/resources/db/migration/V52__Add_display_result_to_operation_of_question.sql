BEGIN;

ALTER TABLE operation_of_question ADD COLUMN display_results BOOLEAN NOT NULL DEFAULT false;

COMMIT;
