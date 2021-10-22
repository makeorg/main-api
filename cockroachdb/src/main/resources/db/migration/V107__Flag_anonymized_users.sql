BEGIN;

UPDATE make_user SET user_type = 'ANONYMOUS' where first_name = 'DELETE_REQUESTED';

COMMIT;
