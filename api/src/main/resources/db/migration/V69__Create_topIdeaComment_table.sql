BEGIN;

CREATE TABLE IF NOT EXISTS top_idea_comment(
  id STRING NOT NULL PRIMARY KEY,
  top_idea_id STRING NOT NULL,
  personality_id STRING NOT NULL,
  comment1 STRING(280) NULL DEFAULT NULL,
  comment2 STRING(280) NULL DEFAULT NULL,
  comment3 STRING(280) NULL DEFAULT NULL,
  vote STRING  NULL DEFAULT NULL,
  qualification STRING  NULL DEFAULT NULL,
  UNIQUE INDEX index_tags_uniqueness(top_idea_id, personality_id),
  CONSTRAINT fk_top_idea_comment_top_idea_id FOREIGN KEY (top_idea_id) REFERENCES top_idea(id),
  CONSTRAINT fk_top_idea_comment_personality_id FOREIGN KEY (personality_id) REFERENCES make_user(uuid)
);

COMMIT;
