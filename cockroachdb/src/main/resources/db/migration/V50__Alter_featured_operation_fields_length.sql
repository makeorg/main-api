BEGIN;

ALTER TABLE featured_operation ALTER COLUMN description SET DATA TYPE String(140);
ALTER TABLE featured_operation ALTER COLUMN alt_picture SET DATA TYPE String(130);

COMMIT;
