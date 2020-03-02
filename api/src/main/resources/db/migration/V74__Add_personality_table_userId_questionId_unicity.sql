ALTER TABLE personality ADD CONSTRAINT index_personality_unicity UNIQUE (user_id, question_id);

COMMIT;