CREATE DATABASE IF NOT EXISTS #dbname#;

CREATE TABLE IF NOT EXISTS user (
  id VARCHAR(256) NOT NULL PRIMARY KEY,
  email VARCHAR(512) NOT NULL,
  first_name VARCHAR(512) NOT NULL,
  last_name VARCHAR(512) NOT NULL,
  hashed_password VARCHAR(2048) NOT NULL,
  date_of_birth TIMESTAMP WITH TIME ZONE
);


CREATE TABLE IF NOT EXISTS token (
  id VARCHAR(256) NOT NULL PRIMARY KEY,
  refresh_token VARCHAR(256) NOT NULL,
  user_id VARCHAR(256) NOT NULL,
  scope VARCHAR(256) NOT NULL,
  creation_date TIMESTAMP WITH TIME ZONE,
  validity_duration_seconds INT NOT NULL,
  parameters TEXT
);