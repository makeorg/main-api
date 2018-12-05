CREATE TABLE operation_of_question (
  question_id STRING(256) NOT NULL PRIMARY KEY,
  operation_id STRING(256) NOT NULL,
  start_date DATE NULL DEFAULT NULL,
  end_date DATE NULL DEFAULT NULL,
  created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
  operation_title STRING(512),
  landing_sequence_id STRING(256) NOT NULL,
  CONSTRAINT fk_question_of_operation_operation FOREIGN KEY (operation_id) REFERENCES operation ("uuid"),
  CONSTRAINT fk_question_of_operation_question FOREIGN KEY (question_id) REFERENCES question ("question_id")
);

COMMIT;