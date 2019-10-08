CREATE TABLE active_feature(
  id STRING NOT NULL PRIMARY KEY,
  feature_id STRING NOT NULL,
  question_id STRING,
  CONSTRAINT fk_active_feature_feature_id FOREIGN KEY (feature_id) REFERENCES feature(id),
  CONSTRAINT fk_active_feature_question_id FOREIGN KEY (question_id) REFERENCES question(question_id)
);

COMMIT;