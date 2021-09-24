BEGIN;

ALTER TABLE make_user DROP COLUMN IF EXISTS is_organisation;

COMMIT;
