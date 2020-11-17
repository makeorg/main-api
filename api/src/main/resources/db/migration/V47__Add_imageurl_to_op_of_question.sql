BEGIN;

ALTER TABLE operation_of_question ADD COLUMN image_url STRING NULL;

COMMIT;
