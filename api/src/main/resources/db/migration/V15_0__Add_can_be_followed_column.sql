ALTER TABLE make_user ADD COLUMN public_profile BOOL DEFAULT false;

CREATE TABLE IF NOT EXISTS followed_users(
    user_id STRING(256) NOT NULL,
    followed_user_id STRING(256) NOT NULL,
    date TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
    PRIMARY KEY (user_id, followed_user_id)
);

COMMIT;

UPDATE make_user SET public_profile = true WHERE is_organisation = true;

COMMIT;