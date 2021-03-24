BEGIN;

ALTER TABLE crm_user ADD COLUMN days_before_deletion INT;
ALTER TABLE crm_user ADD COLUMN last_activity_date STRING;
ALTER TABLE crm_user ADD COLUMN sessions_count INT;
ALTER TABLE crm_user ADD COLUMN events_count INT;

CREATE INDEX ON crm_user (days_before_deletion);

COMMIT;