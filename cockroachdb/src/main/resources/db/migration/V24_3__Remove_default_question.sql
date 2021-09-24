BEGIN;

ALTER TABLE sequence_configuration ALTER COLUMN question_id DROP DEFAULT;
COMMIT;
