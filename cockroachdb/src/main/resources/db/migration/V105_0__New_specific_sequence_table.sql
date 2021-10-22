BEGIN;

CREATE TABLE IF NOT EXISTS specific_sequence_configuration(
    id STRING PRIMARY KEY,
    sequence_size INT,
    new_proposals_ratio DECIMAL,
    max_tested_proposal_count INT,
    selection_algorithm_name STRING,
    intra_idea_enabled BOOL,
    intra_idea_min_count INT,
    intra_idea_proposals_ratio DECIMAL,
    inter_idea_competition_enabled BOOL,
    inter_idea_competition_target_count INT,
    inter_idea_competition_controversial_ratio DECIMAL,
    inter_idea_competition_controversial_count INT
);

COMMIT;