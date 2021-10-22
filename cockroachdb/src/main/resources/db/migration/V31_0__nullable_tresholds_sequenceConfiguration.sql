BEGIN;

UPDATE sequence_configuration SET max_available_proposals = NULL, max_votes = NULL WHERE 1=1;

COMMIT;
