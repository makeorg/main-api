BEGIN;

CREATE TABLE feature(
  id STRING NOT NULL PRIMARY KEY,
  slug STRING NOT NULL,
  name STRING NOT NULL,
  UNIQUE INDEX slug_unique_index (slug)
);

COMMIT;
