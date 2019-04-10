ALTER TABLE oauth_client ADD CONSTRAINT fk_default_user_id_user FOREIGN KEY (default_user_id) REFERENCES make_user(uuid);

COMMIT;