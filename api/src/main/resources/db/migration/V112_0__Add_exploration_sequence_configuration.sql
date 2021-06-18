BEGIN;

CREATE TABLE exploration_sequence_configuration (
  exploration_sequence_configuration_id STRING NOT NULL PRIMARY KEY,
  sequence_size INT NOT NULL,
  max_tested_proposal_count Int NOT NULL,
  new_ratio DECIMAL NOT NULL,
  controversy_ratio DECIMAL NOT NULL,
  top_sorter String NOT NULL,
  controversy_sorter String NOT NULL
);

COMMIT;
