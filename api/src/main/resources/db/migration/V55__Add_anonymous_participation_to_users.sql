ALTER TABLE make_user ADD COLUMN anonymous_participation BOOLEAN NOT NULL DEFAULT false;

COMMIT;