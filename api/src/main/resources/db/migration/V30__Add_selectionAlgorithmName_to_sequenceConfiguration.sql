BEGIN;

ALTER TABLE IF EXISTS sequence_configuration ADD COLUMN selection_algorithm_name STRING NOT NULL DEFAULT 'Bandit';


COMMIT;
