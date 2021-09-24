BEGIN;

CREATE TABLE active_demographics_card (
  id STRING(256) NOT NULL PRIMARY KEY,
  demographics_card_id STRING(256) NOT NULL,
  question_id STRING(256) NOT NULL,
  CONSTRAINT fk_active_demographics_card_demographics_card_id FOREIGN KEY (demographics_card_id) REFERENCES demographics_card(id),
  CONSTRAINT fk_active_demographics_card_question_id FOREIGN KEY (question_id) REFERENCES question(question_id)
);

COMMIT;
