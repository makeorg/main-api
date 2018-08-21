CREATE TABLE IF NOT EXISTS question(
    question_id STRING(256) NOT NULL PRIMARY KEY,
    country STRING(256) NOT NULL,
    language STRING(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
    operation_id STRING(256) NULL,
    theme_id STRING(256) NULL,
    question TEXT
);

COMMIT;