ALTER TABLE operation_of_question DROP COLUMN IF EXISTS start_date;
ALTER TABLE operation_of_question DROP COLUMN IF EXISTS end_date;

COMMIT;