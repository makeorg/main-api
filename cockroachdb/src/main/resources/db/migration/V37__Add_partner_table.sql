BEGIN;

CREATE TABLE partner(
  id STRING NOT NULL PRIMARY KEY,
  name STRING,
  logo STRING,
  link STRING,
  organisation_id STRING,
  partner_kind STRING NOT NULL,
  question_id STRING NOT NULL,
  weight FLOAT NOT NULL,
  CONSTRAINT fk_partner_question_id FOREIGN KEY (question_id) REFERENCES question(question_id),
  CONSTRAINT fk_partner_organisation_id FOREIGN KEY (organisation_id) REFERENCES make_user(uuid)
);

COMMIT;
