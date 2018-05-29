ALTER TABLE make_user ADD COLUMN organisation_name STRING(255) NULL;
ALTER TABLE make_user ADD COLUMN is_organisation BOOL DEFAULT false;
ALTER TABLE make_user RENAME COLUMN verified to email_verified;

COMMIT;