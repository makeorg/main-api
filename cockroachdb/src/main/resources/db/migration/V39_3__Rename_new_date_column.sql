BEGIN;

ALTER TABLE operation_of_question RENAME COLUMN new_start_date TO start_date;
ALTER TABLE operation_of_question RENAME COLUMN new_end_date TO end_date;

COMMIT;
