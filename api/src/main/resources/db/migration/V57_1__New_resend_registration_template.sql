ALTER TABLE crm_templates ADD COLUMN IF NOT EXISTS resend_registration STRING;

COMMIT;