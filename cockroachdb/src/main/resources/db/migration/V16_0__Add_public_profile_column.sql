BEGIN;

ALTER TABLE make_user ADD COLUMN public_profile BOOL DEFAULT false;

COMMIT;
