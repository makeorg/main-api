BEGIN;

CREATE TABLE crm_user(
  user_id STRING NOT NULL PRIMARY KEY,
  email STRING NOT NULL,
  firstname STRING,
  zipcode STRING,
  date_of_birth STRING,
  email_validation_status BOOLEAN,
  email_hardbounce_status BOOLEAN,
  unsubscribe_status BOOLEAN,
  account_creation_date STRING,
  account_creation_source STRING,
  account_creation_origin STRING,
  account_creation_operation STRING,
  account_creation_country STRING,
  countries_activity STRING,
  last_country_activity STRING,
  last_language_activity STRING,
  total_number_proposals INTEGER,
  total_number_votes INTEGER,
  first_contribution_date STRING,
  last_contribution_date STRING,
  operation_activity STRING,
  source_activity STRING,
  active_core BOOLEAN,
  days_of_activity INTEGER,
  days_of_activity30d INTEGER,
  user_type STRING
);

COMMIT;
