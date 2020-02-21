ALTER TABLE idea DROP COLUMN theme_id;
ALTER TABLE question DROP COLUMN theme_id;
ALTER TABLE tag DROP COLUMN theme_id;
ALTER TABLE crm_user DROP COLUMN active_core;
DROP TABLE theme_translation;
DROP TABLE theme;

COMMIT;
