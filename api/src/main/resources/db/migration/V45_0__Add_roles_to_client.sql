ALTER TABLE oauth_client ADD COLUMN roles STRING NOT NULL DEFAULT '';

COMMIT;