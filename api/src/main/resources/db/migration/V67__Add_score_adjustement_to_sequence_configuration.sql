ALTER TABLE sequence_configuration
    ADD COLUMN score_adjustement_votes_threshold DECIMAL DEFAULT 100:::DECIMAL,
    ADD COLUMN score_adjustement_factor DECIMAL DEFAULT 1000:::DECIMAL;

COMMIT;