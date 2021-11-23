BEGIN;

ALTER TABLE active_feature ADD CONSTRAINT feature_question_unicity UNIQUE (feature_id, question_id);

COMMIT;
