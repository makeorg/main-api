BEGIN;

CREATE TABLE IF NOT EXISTS top_idea(
  id STRING NOT NULL PRIMARY KEY,
  idea_id STRING NOT NULL,
  question_id STRING NOT NULL,
  name STRING,
  total_proposals_ratio DECIMAL,
  agreement_ratio DECIMAL,
  like_it_ratio DECIMAL,
  weight DECIMAL,
  CONSTRAINT fk_top_idea_question_id FOREIGN KEY (question_id) REFERENCES question(question_id),
  CONSTRAINT fk_top_idea_idea_id FOREIGN KEY (idea_id) REFERENCES idea(id)
);

COMMIT;
