BEGIN;

UPDATE make_user SET user_type = 'ORGANISATION' WHERE is_organisation = true;

COMMIT;
