ALTER TABLE sequence_configuration
    ADD COLUMN maxTestedProposalCount INTEGER,
    ADD COLUMN sequenceSize INTEGER,
    ADD COLUMN maxVotes INTEGER,
    ADD COLUMN question_id STRING(256) NOT NULL DEFAULT 'default';

COMMIT;