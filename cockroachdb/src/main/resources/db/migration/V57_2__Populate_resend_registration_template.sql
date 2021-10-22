BEGIN;

UPDATE crm_templates SET resend_registration = registration WHERE 1 = 1;

COMMIT;
