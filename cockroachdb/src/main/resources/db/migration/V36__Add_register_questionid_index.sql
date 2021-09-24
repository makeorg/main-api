BEGIN;

CREATE INDEX index_register_question_id ON make_user(register_question_id);

COMMIT;
