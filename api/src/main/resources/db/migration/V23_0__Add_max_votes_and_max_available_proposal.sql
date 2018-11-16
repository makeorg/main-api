ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN max_available_proposals INTEGER NULL DEFAULT 1000:::INT;
ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN tested_proposals_max_votes_threshold INTEGER NULL DEFAULT 1500:::INT;

COMMIT;