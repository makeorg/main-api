BEGIN;

ALTER TABLE tag_type ADD COLUMN IF NOT EXISTS weight_type INT;

COMMIT;