BEGIN;

CREATE TABLE IF NOT EXISTS crm_question_template(
  id STRING NOT NULL PRIMARY KEY,
  kind STRING NOT NULL,
  question_id STRING NOT NULL,
  template STRING NOT NULL,
  UNIQUE INDEX crm_question_template_kind_question_id (kind, question_id),
  CONSTRAINT fk_crm_question_template_kind_ref_crm_template_kind FOREIGN KEY (kind) REFERENCES crm_template_kind(id),
  CONSTRAINT fk_crm_question_template_question_id_ref_question FOREIGN KEY (question_id) REFERENCES question(question_id)
);

COMMIT;
