BEGIN;

ALTER TABLE crm_user ADD COLUMN account_creation_location STRING NULL default NULL;

COMMIT;
