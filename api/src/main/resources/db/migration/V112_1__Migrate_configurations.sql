BEGIN;

INSERT INTO exploration_sequence_configuration (
  exploration_sequence_configuration_id,
  sequence_size,
  max_tested_proposal_count,
  new_ratio,
  controversy_ratio,
  top_sorter,
  controversy_sorter
) SELECT spec.id, spec.sequence_size, 1000, 0.5, 0.1, 'bandit', 'bandit'
    FROM sequence_configuration as conf INNER JOIN specific_sequence_configuration AS spec ON
      conf.main = spec.id;

COMMIT;
