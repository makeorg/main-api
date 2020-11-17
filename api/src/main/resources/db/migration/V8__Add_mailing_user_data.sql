BEGIN;

ALTER TABLE make_user
ADD COLUMN is_hard_bounce BOOL NOT NULL DEFAULT false,
ADD COLUMN last_mailing_error_date TIMESTAMP WITH TIME ZONE NULL,
ADD COLUMN last_mailing_error_message STRING(255) NULL;

COMMIT;
