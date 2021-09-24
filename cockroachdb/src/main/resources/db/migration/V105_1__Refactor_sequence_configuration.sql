BEGIN;

ALTER TABLE sequence_configuration ADD COLUMN IF NOT EXISTS main STRING;
ALTER TABLE sequence_configuration ADD COLUMN IF NOT EXISTS controversial STRING;
ALTER TABLE sequence_configuration ADD COLUMN IF NOT EXISTS popular STRING;
ALTER TABLE sequence_configuration ADD COLUMN IF NOT EXISTS keyword STRING;

COMMIT;