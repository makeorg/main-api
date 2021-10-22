BEGIN;

ALTER TABLE sequence_configuration
    ADD COLUMN max_tested_proposal_count INTEGER,
    ADD COLUMN sequence_size INTEGER,
    ADD COLUMN max_votes INTEGER,
    ADD COLUMN question_id STRING(256) NOT NULL DEFAULT 'default';

COMMIT;
