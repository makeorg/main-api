BEGIN;

ALTER TABLE make_user DROP COLUMN roles_csv;
ALTER TABLE make_user RENAME COLUMN roles_array TO roles;

COMMIT;
