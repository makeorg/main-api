ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN tested_proposals_score_threshold DECIMAL NULL DEFAULT 0.0:::DECIMAL;
ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN tested_proposals_controversy_threshold DECIMAL NULL DEFAULT 0.0:::DECIMAL;

COMMIT;