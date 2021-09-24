BEGIN;

UPDATE make_user SET roles = array_append(roles, 'ROLE_SUPER_ADMIN') where email = '${adminEmail}';

COMMIT;
