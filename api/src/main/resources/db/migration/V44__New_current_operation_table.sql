CREATE TABLE IF NOT EXISTS current_operation(
    id STRING NOT NULL PRIMARY KEY,
    question_id STRING,
    label STRING(25),
    picture STRING,
    alt_picture STRING(80),
    description STRING(130),
    link_label STRING(25),
    internal_link STRING,
    external_link STRING
);

COMMIT;