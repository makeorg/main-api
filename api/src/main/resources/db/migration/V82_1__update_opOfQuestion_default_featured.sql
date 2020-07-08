BEGIN;

UPDATE operation_of_question ooq SET featured = true WHERE (SELECT operation_kind FROM operation WHERE uuid = ooq.operation_id) = 'GREAT_CAUSE';

COMMIT;
