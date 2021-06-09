BEGIN;

DELETE FROM specific_sequence_configuration WHERE id IN (SELECT main FROM sequence_configuration);

COMMIT;
