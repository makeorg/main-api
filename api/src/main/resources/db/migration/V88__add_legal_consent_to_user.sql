ALTER TABLE make_user
  ADD COLUMN legal_minor_consent BOOLEAN DEFAULT NULL,
  ADD COLUMN legal_advisor_approval BOOLEAN DEFAULT NULL;

COMMIT;