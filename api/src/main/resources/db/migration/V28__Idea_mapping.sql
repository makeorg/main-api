CREATE TABLE idea_mapping(
  id STRING NOT NULL PRIMARY KEY,
  question_id  STRING NOT NULL,
  stake_tag_id STRING NULL,
  solution_type_tag_id STRING NULL,
  idea_id STRING NOT NULL,
  CONSTRAINT fk_idea_mapping_question FOREIGN KEY (question_id) REFERENCES question(question_id),
  CONSTRAINT fk_idea_mapping_stake FOREIGN KEY (stake_tag_id) REFERENCES tag(id),
  CONSTRAINT fk_idea_mapping_solution FOREIGN KEY (solution_type_tag_id) REFERENCES tag(id),
  CONSTRAINT fk_idea_mapping_idea FOREIGN KEY (idea_id) REFERENCES idea(id),
  INDEX index_stake_tag_id (stake_tag_id),
  INDEX index_solution_type_tag_id(solution_type_tag_id),
  INDEX index_idea_id(idea_id),
  UNIQUE INDEX index_tags_unicity(stake_tag_id, solution_type_tag_id)
);

COMMIT;