CREATE TABLE IF NOT EXISTS personality(
    id STRING NOT NULL PRIMARY KEY,
    user_id STRING,
    question_id STRING,
    personality_role STRING,
    CONSTRAINT fk_personality_user_id FOREIGN KEY (user_id) REFERENCES make_user(uuid),
    CONSTRAINT fk_personality_question_id FOREIGN KEY (question_id) REFERENCES question(question_id)
);

COMMIT;