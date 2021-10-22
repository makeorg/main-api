BEGIN;

CREATE TABLE widget (
  id STRING(256) NOT NULL PRIMARY KEY,
  source_id STRING(256) NOT NULL,
  question_id STRING(256) NOT NULL,
  country STRING(3) NOT NULL,
  author STRING(256) NOT NULL,
  version STRING(3) NOT NULL,
  script STRING NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_widget_source_id FOREIGN KEY (source_id) REFERENCES source(id),
  CONSTRAINT fk_widget_question_id FOREIGN KEY (question_id) REFERENCES question(question_id),
  CONSTRAINT fk_widget_author FOREIGN KEY (author) REFERENCES make_user(uuid)
);

COMMIT;
