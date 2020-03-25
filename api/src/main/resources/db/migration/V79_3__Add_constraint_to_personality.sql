ALTER TABLE personality ADD CONSTRAINT fk_personality_role_id FOREIGN KEY (personality_role_id) REFERENCES personality_role(id);

COMMIT;