BEGIN;

ALTER TABLE operation_of_question
  DROP COLUMN signup_card_enabled,
  DROP COLUMN signup_card_title,
  DROP COLUMN signup_card_next_cta;

COMMIT;
