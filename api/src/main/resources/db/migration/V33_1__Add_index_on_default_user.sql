BEGIN;

CREATE INDEX index_default_user_id ON oauth_client(default_user_id);

COMMIT;
