BEGIN;

UPDATE operation_of_question SET results_link = 'results' WHERE display_results IS true AND results_link IS NULL;
UPDATE operation_of_question SET results_link = NULL WHERE display_results IS false;

COMMIT;
