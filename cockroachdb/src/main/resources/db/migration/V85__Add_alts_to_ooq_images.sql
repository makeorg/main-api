BEGIN;

ALTER TABLE operation_of_question
  ADD COLUMN description_image_alt STRING(130) DEFAULT NULL,
  ADD COLUMN consultation_image_alt STRING(130) DEFAULT NULL;

COMMIT;
