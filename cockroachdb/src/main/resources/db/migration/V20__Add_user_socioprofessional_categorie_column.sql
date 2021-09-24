BEGIN;

ALTER TABLE make_user ADD COLUMN socio_professional_category STRING(1024) NULL;

COMMIT;
