BEGIN;

UPDATE make_user SET roles_array = string_to_array(roles_csv, ',');

COMMIT;
