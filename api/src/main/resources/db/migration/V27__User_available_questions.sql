ALTER TABLE make_user ADD COLUMN IF NOT EXISTS available_questions STRING[];

COMMIT;