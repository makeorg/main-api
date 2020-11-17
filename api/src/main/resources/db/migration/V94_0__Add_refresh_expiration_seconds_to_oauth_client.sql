BEGIN;

ALTER TABLE oauth_client
  ADD COLUMN refresh_expiration_seconds INT NOT NULL default 1500,
  ADD COLUMN reconnect_expiration_seconds INT NOT NULL default 900,
  ALTER COLUMN secret DROP NOT NULL;

ALTER TABLE access_token ADD COLUMN refresh_expires_in INT NOT NULL default 1500;

COMMIT;