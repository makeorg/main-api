BEGIN;

CREATE TABLE IF NOT EXISTS keyword(
  question_id STRING NOT NULL,
  key STRING NOT NULL,
  label STRING NOT NULL,
  score FLOAT NOT NULL,
  count INT NOT NULL,
  PRIMARY KEY (question_id, key),
  CONSTRAINT fk_keyword_question_id_ref_question FOREIGN KEY (question_id) REFERENCES question(question_id)
);

COMMIT;