BEGIN;

CREATE TABLE IF NOT EXISTS personality_role_field(
  id STRING NOT NULL PRIMARY KEY,
  personality_role_id STRING NOT NULL,
  name STRING NOT NULL,
  field_type STRING NOT NULL,
  required BOOL NOT NULL,
  UNIQUE INDEX index_name_uniqueness(personality_role_id, name),
  CONSTRAINT fk_personality_role_id FOREIGN KEY (personality_role_id) REFERENCES personality_role(id)
);

COMMIT;
