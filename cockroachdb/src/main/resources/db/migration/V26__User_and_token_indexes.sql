BEGIN;

CREATE INDEX ON make_user(email);
CREATE INDEX ON make_user(verification_token);
CREATE INDEX ON make_user(reset_token);
CREATE INDEX ON make_user(public_profile);
CREATE INDEX ON make_user(is_organisation);

CREATE INDEX ON access_token(refresh_token);

CREATE INDEX ON oauth_client(secret);

CREATE INDEX ON operation(slug);

CREATE INDEX ON question(slug);

CREATE INDEX ON sequence_configuration(question_id);

CREATE INDEX ON tag(question_id);

COMMIT;
