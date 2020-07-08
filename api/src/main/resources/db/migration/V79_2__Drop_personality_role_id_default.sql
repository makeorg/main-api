BEGIN;

ALTER TABLE personality ALTER COLUMN personality_role_id DROP DEFAULT;

COMMIT;
