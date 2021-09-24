BEGIN;

UPDATE feature
SET slug = 'sequence-custom-data-segment', name = 'Get the segment from the "segment" custom data'
WHERE slug = 'sequence-utm-segment';

COMMIT;