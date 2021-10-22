BEGIN;

UPDATE make_user SET roles_array = string_to_array(roles_csv, ',') WHERE 1=1;

COMMIT;
