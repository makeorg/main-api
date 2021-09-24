BEGIN;

CREATE TABLE demographics_card (
  id STRING NOT NULL PRIMARY KEY,
  name STRING(256) NOT NULL,
  layout STRING NOT NULL,
  data_type STRING NOT NULL,
  language STRING NOT NULL,
  title STRING(64) NOT NULL,
  parameters STRING NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  INDEX demographics_card_data_type(data_type),
  INDEX demographics_card_language(language)
);

COMMIT;
