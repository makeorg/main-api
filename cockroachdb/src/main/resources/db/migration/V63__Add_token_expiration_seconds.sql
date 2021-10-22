BEGIN;

ALTER TABLE oauth_client ADD COLUMN token_expiration_seconds INT4 NOT NULL DEFAULT 300;

COMMIT;
