BEGIN;

ALTER TABLE make_user
  ADD COLUMN website STRING NULL,
  ADD COLUMN political_party STRING NULL;

COMMIT;
