BEGIN;

ALTER TABLE oauth_client ADD COLUMN IF NOT EXISTS default_user_id STRING(256);

COMMIT;