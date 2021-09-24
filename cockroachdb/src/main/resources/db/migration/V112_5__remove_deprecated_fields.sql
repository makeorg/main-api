BEGIN;

ALTER TABLE specific_sequence_configuration
  DROP COLUMN intra_idea_enabled,
  DROP COLUMN intra_idea_min_count,
  DROP COLUMN intra_idea_proposals_ratio,
  DROP COLUMN inter_idea_competition_enabled,
  DROP COLUMN inter_idea_competition_target_count,
  DROP COLUMN inter_idea_competition_controversial_ratio,
  DROP COLUMN inter_idea_competition_controversial_count
;

COMMIT;
