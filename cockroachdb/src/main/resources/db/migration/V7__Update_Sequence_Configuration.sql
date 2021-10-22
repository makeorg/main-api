BEGIN;

ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN idea_competition_enabled BOOL NULL DEFAULT false;
ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN idea_competition_target_count INTEGER NULL DEFAULT 50:::INT;
ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN idea_competition_controversial_ratio DECIMAL NULL DEFAULT 0.0:::DECIMAL;
ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN idea_competition_controversial_count INTEGER NULL DEFAULT 50:::INT;

COMMIT;
