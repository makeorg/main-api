BEGIN;

ALTER TABLE make_user ADD COLUMN IF NOT EXISTS roles_array STRING[];
ALTER TABLE make_user RENAME COLUMN roles TO roles_csv;

COMMIT;
