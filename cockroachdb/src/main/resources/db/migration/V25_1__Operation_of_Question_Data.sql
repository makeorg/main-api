BEGIN;

insert into operation_of_question(question_id, operation_id, start_date, end_date, landing_sequence_id, operation_title)
(SELECT question.question_id, operation.uuid, config.start_date, config.end_date, config.landing_sequence_id, translation.title FROM question
INNER JOIN operation ON question.operation_id = operation.uuid
INNER JOIN operation_country_configuration as config ON config.operation_uuid = operation.uuid AND config.country = question.country
LEFT JOIN operation_translation AS translation ON translation.operation_uuid = operation.uuid AND translation.language = question.language);

COMMIT;
