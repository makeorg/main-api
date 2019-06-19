CREATE TABLE IF NOT EXISTS featured_operation(
    id STRING NOT NULL PRIMARY KEY,
    question_id STRING,
    title STRING(90),
    description STRING,
    landscape_picture STRING,
    portrait_picture STRING,
    alt_picture STRING(80),
    label STRING(25),
    button_label STRING(25),
    internal_link STRING,
    external_link STRING,
    slot INT UNIQUE
);

COMMIT;