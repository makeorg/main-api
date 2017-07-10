CREATE DATABASE IF NOT EXISTS #dbname#;
%
CREATE TABLE IF NOT EXISTS make_user (
  uuid STRING PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  email VARCHAR(255) NOT NULL,
  first_name VARCHAR(512) NOT NULL,
  last_name VARCHAR(512) NOT NULL,
  last_ip VARCHAR(50) NOT NULL,
  hashed_password VARCHAR(512) DEFAULT NULL,
  salt VARCHAR(512) DEFAULT NULL,
  enabled BOOLEAN NOT NULL,
  verified BOOLEAN NOT NULL,
  last_connection TIMESTAMP WITH TIME ZONE DEFAULT NULL,
  verification_token VARCHAR(255) DEFAULT NULL,
  verification_token_expires_at TIMESTAMP WITH TIME ZONE,
  reset_token VARCHAR(255) DEFAULT NULL,
  reset_token_expires_at TIMESTAMP WITH TIME ZONE,
  roles TEXT NOT NULL,

  date_of_birth DATE DEFAULT NULL,
  avatar_url VARCHAR(512) DEFAULT NULL,
  profession VARCHAR(512) DEFAULT NULL,
  phone_number VARCHAR(50) DEFAULT NULL,
  twitter_id VARCHAR(255) DEFAULT NULL,
  facebook_id VARCHAR(255) DEFAULT NULL,
  google_id VARCHAR(255) DEFAULT NULL,
  gender VARCHAR(1) DEFAULT NULL,
  gender_name VARCHAR(20) DEFAULT NULL,
  department_number VARCHAR(3) DEFAULT NULL,
  karma_level INT DEFAULT 0,
  locale VARCHAR(8) DEFAULT NULL,
  opt_in_newsletter BOOLEAN DEFAULT FALSE NOT NULL
);
%
CREATE TABLE IF NOT EXISTS oauth_client (
  uuid VARCHAR(256) PRIMARY KEY,
  secret VARCHAR(256) NOT NULL,
  allowed_grant_types VARCHAR(256),
  scope VARCHAR(2048),
  redirect_uri VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
%
CREATE TABLE IF NOT EXISTS auth_code (
  authorization_code VARCHAR(256) PRIMARY KEY,
  scope VARCHAR(2048),
  redirect_uri VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  expires_in INT NOT NULL,
  make_user_uuid VARCHAR(256) NOT NULL REFERENCES make_user,
  client_uuid VARCHAR(256) NOT NULL REFERENCES oauth_client
);
%
CREATE TABLE IF NOT EXISTS access_token (
  access_token VARCHAR(256) PRIMARY KEY,
  refresh_token VARCHAR(256),
  scope VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  expires_in INT NOT NULL,
  make_user_uuid VARCHAR(256) NOT NULL REFERENCES make_user,
  client_uuid VARCHAR(256) NOT NULL REFERENCES oauth_client
);
%
INSERT into oauth_client
    (uuid, secret, allowed_grant_types)
    VALUES
    ('#clientid#', '#clientsecret#', '{"password", "refresh_token", "client_credentials"}');
%