BEGIN;

ALTER TABLE make_user ADD COLUMN reconnect_token STRING NULL DEFAULT NULL;
ALTER TABLE make_user ADD COLUMN reconnect_token_created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT NULL;

COMMIT;