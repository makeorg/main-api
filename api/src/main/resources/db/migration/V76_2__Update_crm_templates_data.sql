UPDATE crm_templates as ct SET organisation_email_change_confirmation = ct.forgotten_password_organisation WHERE 1 = 1;

COMMIT;