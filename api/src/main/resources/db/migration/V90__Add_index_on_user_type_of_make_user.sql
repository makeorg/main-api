BEGIN;

CREATE INDEX index_user_type ON make_user(user_type);

COMMIT;