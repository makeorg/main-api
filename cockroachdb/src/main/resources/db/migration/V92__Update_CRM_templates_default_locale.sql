BEGIN;

UPDATE crm_templates t SET locale = (SELECT CONCAT(language, '_', countries) FROM question q WHERE q.question_id = t.question_id) WHERE locale IS NULL;

COMMIT;
