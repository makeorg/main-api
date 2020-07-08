BEGIN;

ALTER TABLE operation_of_question ADD COLUMN IF NOT EXISTS secondary_color STRING(7) NULL DEFAULT NULL;
ALTER TABLE operation_of_question ADD COLUMN IF NOT EXISTS secondary_font_color STRING(7) NULL DEFAULT NULL;

COMMIT;
