BEGIN;

ALTER TABLE sequence_configuration
  DROP COLUMN score_adjustement_votes_threshold,
  DROP COLUMN score_adjustement_factor;

COMMIT;
