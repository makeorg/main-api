BEGIN;

CREATE TABLE crm_templates(
  id STRING NOT NULL PRIMARY KEY,
  question_id STRING,
  locale STRING,
  registration STRING,
  welcome STRING,
  proposal_accepted STRING,
  proposal_refused STRING,
  forgotten_password STRING,
  proposal_accepted_organisation STRING,
  proposal_refused_organisation STRING,
  forgotten_password_organisation STRING,
  CONSTRAINT fk_template_id_question FOREIGN KEY (question_id) REFERENCES question(question_id),
  UNIQUE INDEX index_question_unicity(question_id)
);

COMMIT;
