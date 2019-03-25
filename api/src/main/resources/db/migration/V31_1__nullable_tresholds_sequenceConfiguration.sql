ALTER TABLE IF EXISTS sequence_configuration
  ALTER COLUMN tested_proposals_engagement_threshold DROP NOT NULL,
  ALTER COLUMN tested_proposals_score_threshold DROP NOT NULL,
  ALTER COLUMN tested_proposals_controversy_threshold DROP NOT NULL,
  ALTER COLUMN tested_proposals_max_votes_threshold DROP NOT NULL,
  DROP COLUMN max_available_proposals,
  DROP COLUMN max_votes;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN bandit_enabled TO intra_idea_enabled;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN bandit_min_count TO intra_idea_min_count;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN bandit_proposals_ratio TO intra_idea_proposals_ratio;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN idea_competition_enabled TO inter_idea_competition_enabled;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN idea_competition_target_count TO inter_idea_competition_target_count;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN idea_competition_controversial_ratio TO inter_idea_competition_controversial_ratio;
ALTER TABLE IF EXISTS sequence_configuration RENAME COLUMN idea_competition_controversial_count TO inter_idea_competition_controversial_count;


COMMIT;