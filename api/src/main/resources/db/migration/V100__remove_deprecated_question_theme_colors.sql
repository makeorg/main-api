BEGIN;

ALTER TABLE operation_of_question
  DROP COLUMN gradient_start,
  DROP COLUMN gradient_end,
  DROP COLUMN secondary_color,
  DROP COLUMN secondary_font_color;

COMMIT;
