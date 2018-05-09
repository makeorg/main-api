ALTER TABLE make_user ADD COLUMN organisation STRING(255) NULL;
ALTER TABLE make_user RENAME COLUMN verified to email_verified;

COMMIT;