BEGIN;

ALTER TABLE sequence_configuration ADD COLUMN non_sequence_votes_weight DECIMAL DEFAULT 0.5:::DECIMAL;

COMMIT;
