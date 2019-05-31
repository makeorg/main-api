ALTER TABLE operation_of_question ADD COLUMN IF NOT EXISTS new_start_date TIMESTAMP;
ALTER TABLE operation_of_question ADD COLUMN IF NOT EXISTS new_end_date TIMESTAMP;

COMMIT;