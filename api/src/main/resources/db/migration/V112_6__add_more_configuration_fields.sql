BEGIN;

ALTER TABLE exploration_sequence_configuration
  ADD COLUMN keywords_threshold DECIMAL NOT NULL DEFAULT 0.2,
  ADD COLUMN candidates_pool_size INT NOT NULL DEFAULT 10
;

COMMIT;
