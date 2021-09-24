BEGIN;

ALTER TABLE IF EXISTS tag_type ADD COLUMN required_for_enrichment BOOLEAN DEFAULT false;

COMMIT;
