ALTER TABLE make_user
    ADD COLUMN  IF NOT EXISTS register_question_id STRING(256) NULL,
    ADD COLUMN  IF NOT EXISTS opt_in_partner BOOL NULL;
COMMIT;