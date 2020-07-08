BEGIN;

ALTER TABLE make_user alter column avatar_url type STRING(2048);

COMMIT;
