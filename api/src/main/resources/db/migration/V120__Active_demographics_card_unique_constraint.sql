BEGIN;

ALTER TABLE active_demographics_card ADD CONSTRAINT question_card_unicity UNIQUE (demographics_card_id, question_id);

COMMIT;
