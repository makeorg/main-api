CREATE TABLE user_to_anonymize (
  email STRING(256) NOT NULL PRIMARY KEY,
  request_date TIMESTAMP WITH TIME ZONE DEFAULT now()
);

COMMIT;