CREATE TABLE IF NOT EXISTS followed_user(
    user_id STRING(256) NOT NULL,
    followed_user_id STRING(256) NOT NULL,
    date TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
    CONSTRAINT fk_user_id_ref_make_user FOREIGN KEY (user_id) REFERENCES make_user ("uuid"),
    CONSTRAINT fk_followed_user_id_ref_make_user FOREIGN KEY (followed_user_id) REFERENCES make_user ("uuid"),
    PRIMARY KEY (user_id, followed_user_id)
);

COMMIT;