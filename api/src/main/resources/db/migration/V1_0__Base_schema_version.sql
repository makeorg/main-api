BEGIN;

SAVEPOINT cockroach_restart;

CREATE TABLE make_user (
	"uuid" STRING NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
	email STRING(255) NOT NULL,
	first_name STRING(512) NULL DEFAULT NULL,
	last_name STRING(512) NULL DEFAULT NULL,
	last_ip STRING(50) NULL DEFAULT NULL,
	hashed_password STRING(512) NULL DEFAULT NULL,
	enabled BOOL NOT NULL,
	verified BOOL NOT NULL,
	last_connection TIMESTAMP WITH TIME ZONE NULL DEFAULT NULL,
	verification_token STRING(255) NULL DEFAULT NULL,
	verification_token_expires_at TIMESTAMP WITH TIME ZONE NULL,
	reset_token STRING(255) NULL DEFAULT NULL,
	reset_token_expires_at TIMESTAMP WITH TIME ZONE NULL,
	roles STRING NOT NULL,
	date_of_birth DATE NULL DEFAULT NULL,
	avatar_url STRING(512) NULL DEFAULT NULL,
	profession STRING(512) NULL DEFAULT NULL,
	phone_number STRING(50) NULL DEFAULT NULL,
	twitter_id STRING(255) NULL DEFAULT NULL,
	facebook_id STRING(255) NULL DEFAULT NULL,
	google_id STRING(255) NULL DEFAULT NULL,
	gender STRING(1) NULL DEFAULT NULL,
	gender_name STRING(20) NULL DEFAULT NULL,
	postal_code STRING(10) NULL DEFAULT NULL,
	karma_level INT NULL DEFAULT 0:::INT,
	locale STRING(8) NULL DEFAULT NULL,
	opt_in_newsletter BOOL NOT NULL DEFAULT false,
	country STRING(3) NULL DEFAULT 'FR':::STRING,
	language STRING(3) NULL DEFAULT 'fr':::STRING,
	CONSTRAINT "primary" PRIMARY KEY ("uuid" ASC),
	UNIQUE INDEX email_unique_index (email ASC),
	INDEX operation_action_user_id_index ("uuid" ASC),
	FAMILY "primary" ("uuid", created_at, updated_at, email, first_name, last_name, last_ip, hashed_password, enabled, verified, last_connection, verification_token, verification_token_expires_at, reset_token, reset_token_expires_at, roles, date_of_birth, avatar_url, profession, phone_number, twitter_id, facebook_id, google_id, gender, gender_name, postal_code, karma_level, locale, opt_in_newsletter, country, language)
);

CREATE TABLE oauth_client (
	"uuid" STRING(256) NOT NULL,
	secret STRING(256) NOT NULL,
	allowed_grant_types STRING(256) NULL,
	scope STRING(2048) NULL,
	redirect_uri STRING(2048) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	CONSTRAINT "primary" PRIMARY KEY ("uuid" ASC),
	FAMILY "primary" ("uuid", secret, allowed_grant_types, scope, redirect_uri, created_at, updated_at)
);

CREATE TABLE access_token (
	access_token STRING(256) NOT NULL,
	refresh_token STRING(256) NULL,
	scope STRING(2048) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	expires_in INT NOT NULL,
	make_user_uuid STRING(256) NOT NULL,
	client_uuid STRING(256) NOT NULL,
	CONSTRAINT "primary" PRIMARY KEY (access_token ASC),
	CONSTRAINT fk_make_user_uuid_ref_make_user FOREIGN KEY (make_user_uuid) REFERENCES make_user ("uuid"),
	INDEX access_token_auto_index_fk_make_user_uuid_ref_make_user (make_user_uuid ASC),
	CONSTRAINT fk_client_uuid_ref_oauth_client FOREIGN KEY (client_uuid) REFERENCES oauth_client ("uuid"),
	INDEX access_token_auto_index_fk_client_uuid_ref_oauth_client (client_uuid ASC),
	FAMILY "primary" (access_token, refresh_token, scope, created_at, updated_at, expires_in, make_user_uuid, client_uuid)
);

CREATE TABLE auth_code (
	authorization_code STRING(256) NOT NULL,
	scope STRING(2048) NULL,
	redirect_uri STRING(2048) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	expires_in INT NOT NULL,
	make_user_uuid STRING(256) NOT NULL,
	client_uuid STRING(256) NOT NULL,
	CONSTRAINT "primary" PRIMARY KEY (authorization_code ASC),
	CONSTRAINT fk_make_user_uuid_ref_make_user FOREIGN KEY (make_user_uuid) REFERENCES make_user ("uuid"),
	INDEX auth_code_auto_index_fk_make_user_uuid_ref_make_user (make_user_uuid ASC),
	CONSTRAINT fk_client_uuid_ref_oauth_client FOREIGN KEY (client_uuid) REFERENCES oauth_client ("uuid"),
	INDEX auth_code_auto_index_fk_client_uuid_ref_oauth_client (client_uuid ASC),
	FAMILY "primary" (authorization_code, scope, redirect_uri, created_at, expires_in, make_user_uuid, client_uuid)
);

CREATE TABLE idea (
	id STRING(256) NOT NULL,
	"name" STRING NULL,
	operation_id STRING(256) NULL,
	question STRING(256) NULL,
	country STRING(3) NULL,
	language STRING(3) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	status STRING(20) NULL,
	CONSTRAINT "primary" PRIMARY KEY (id ASC),
	FAMILY "primary" (id, "name", operation_id, question, country, language, created_at, updated_at, status)
);

CREATE TABLE operation (
	"uuid" STRING(256) NOT NULL,
	status STRING(20) NOT NULL,
	slug STRING(256) NOT NULL,
	default_language STRING(3) NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	CONSTRAINT "primary" PRIMARY KEY ("uuid" ASC),
	UNIQUE INDEX operation_slug_unique_index (slug ASC),
	FAMILY "primary" ("uuid", status, slug, default_language, created_at, updated_at)
);

CREATE TABLE operation_action (
	operation_uuid STRING(256) NOT NULL,
	make_user_uuid STRING(256) NOT NULL,
	action_date TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	action_type STRING(256) NOT NULL,
	arguments STRING NULL,
	CONSTRAINT fk_operation_uuid_ref_operation FOREIGN KEY (operation_uuid) REFERENCES operation ("uuid"),
	INDEX operation_action_auto_index_fk_operation_uuid_ref_operation (operation_uuid ASC),
	CONSTRAINT fk_make_user_uuid_ref_make_user FOREIGN KEY (make_user_uuid) REFERENCES make_user ("uuid"),
	INDEX operation_action_auto_index_fk_make_user_uuid_ref_make_user (make_user_uuid ASC),
	INDEX operation_action_operation_id_index (operation_uuid ASC),
	FAMILY "primary" (operation_uuid, make_user_uuid, action_date, action_type, arguments, rowid)
);

CREATE TABLE operation_country_configuration (
	operation_uuid STRING(256) NOT NULL,
	country STRING(3) NOT NULL,
	tag_ids STRING(2048) NULL,
	landing_sequence_id STRING(256) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	start_date DATE NULL DEFAULT NULL,
	end_date DATE NULL DEFAULT NULL,
	CONSTRAINT "primary" PRIMARY KEY (operation_uuid ASC, country ASC),
	CONSTRAINT fk_operation_uuid_ref_operation FOREIGN KEY (operation_uuid) REFERENCES operation ("uuid"),
	FAMILY "primary" (operation_uuid, country, tag_ids, landing_sequence_id, created_at, updated_at, start_date, end_date)
);

CREATE TABLE operation_translation (
	operation_uuid STRING(256) NOT NULL,
	language STRING(3) NOT NULL,
	title STRING(256) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	CONSTRAINT "primary" PRIMARY KEY (operation_uuid ASC, language ASC),
	CONSTRAINT fk_operation_uuid_ref_operation FOREIGN KEY (operation_uuid) REFERENCES operation ("uuid"),
	FAMILY "primary" (operation_uuid, language, title, created_at, updated_at)
);

CREATE TABLE sequence_configuration (
	sequence_id STRING(256) NOT NULL,
	new_proposals_ratio DECIMAL NULL DEFAULT 0.5:::DECIMAL,
	new_proposals_vote_threshold INTEGER NULL DEFAULT 100:::INT,
	tested_proposals_engagement_threshold DECIMAL NULL DEFAULT 0.8:::DECIMAL,
	bandit_enabled BOOL NULL DEFAULT true,
	bandit_min_count INTEGER NULL DEFAULT 3:::INT,
	bandit_proposals_ratio DECIMAL NULL DEFAULT 0.3:::DECIMAL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	CONSTRAINT "primary" PRIMARY KEY (sequence_id ASC),
	FAMILY "primary" (sequence_id, new_proposals_ratio, new_proposals_vote_threshold, tested_proposals_engagement_threshold, bandit_enabled, bandit_min_count, bandit_proposals_ratio, created_at, updated_at)
);

CREATE TABLE tag (
	slug STRING(256) NOT NULL,
	label STRING(256) NULL,
	enabled BOOL NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	CONSTRAINT "primary" PRIMARY KEY (slug ASC),
	FAMILY "primary" (slug, label, enabled, created_at, updated_at)
);

CREATE TABLE theme (
	"uuid" STRING(256) NOT NULL,
	actions_count INT NULL,
	proposals_count INT NULL,
	country STRING(3) NULL,
	color STRING(7) NULL,
	gradient_from STRING(7) NULL,
	gradient_to STRING(7) NULL,
	tags_ids STRING(2048) NULL,
	created_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	updated_at TIMESTAMP WITH TIME ZONE NULL DEFAULT now(),
	CONSTRAINT "primary" PRIMARY KEY ("uuid" ASC),
	FAMILY "primary" ("uuid", actions_count, proposals_count, country, color, gradient_from, gradient_to, tags_ids, created_at, updated_at)
);

CREATE TABLE theme_translation (
	theme_uuid STRING(256) NOT NULL,
	language STRING(3) NOT NULL,
	title STRING(1024) NULL,
	slug STRING(256) NULL,
	CONSTRAINT "primary" PRIMARY KEY (theme_uuid ASC, language ASC),
	CONSTRAINT fk_theme_uuid_ref_theme FOREIGN KEY (theme_uuid) REFERENCES theme ("uuid"),
	FAMILY "primary" (theme_uuid, language, title, slug)
);

RELEASE SAVEPOINT cockroach_restart;
COMMIT;