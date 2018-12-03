ALTER TABLE sequence_configuration RENAME COLUMN maxTestedProposalCount TO max_tested_proposal_count;
ALTER TABLE sequence_configuration RENAME COLUMN sequenceSize TO sequence_size;
ALTER TABLE sequence_configuration RENAME COLUMN maxVotes TO max_votes;

COMMIT;