DROP DATABASE makeapi;
CREATE DATABASE makeapi;

CREATE TABLE citizen (
  id VARCHAR(256) NOT NULL PRIMARY KEY,
  email VARCHAR(512) NOT NULL,
  first_name VARCHAR(512) NOT NULL,
  last_name VARCHAR(512) NOT NULL,
  hashed_password VARCHAR(2048) NOT NULL,
  date_of_birth TIMESTAMP WITH TIME ZONE
);


CREATE TABLE token (
  id VARCHAR(256) NOT NULL PRIMARY KEY,
  refresh_token VARCHAR(256) NOT NULL,
  citizen_id VARCHAR(256) NOT NULL,
  scope VARCHAR(256) NOT NULL,
  creation_date TIMESTAMP WITH TIME ZONE,
  validity_duration_seconds INT NOT NULL,
  parameters TEXT
);