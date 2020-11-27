BEGIN;

CREATE TABLE IF NOT EXISTS crm_template_kind(
  id STRING NOT NULL PRIMARY KEY
);

INSERT INTO crm_template_kind values
  ('Registration'),
  ('Welcome'),
  ('ResendRegistration'),
  ('ForgottenPassword'),
  ('ProposalAccepted'),
  ('ProposalRefused'),
  ('B2BRegistration'),
  ('B2BEmailChanged'),
  ('B2BForgottenPassword'),
  ('B2BProposalAccepted'),
  ('B2BProposalRefused')
;

CREATE TABLE IF NOT EXISTS crm_language_template(
  id STRING NOT NULL PRIMARY KEY,
  kind STRING NOT NULL,
  language STRING NOT NULL,
  template STRING NOT NULL,
  UNIQUE INDEX crm_language_template_kind_language (kind, language),
  CONSTRAINT fk_crm_language_template_kind_ref_crm_template_kind FOREIGN KEY (kind) REFERENCES crm_template_kind(id)
);

COMMIT;
