BEGIN;

ALTER TABLE make_user ADD COLUMN description TEXT NULL;

COMMIT;
