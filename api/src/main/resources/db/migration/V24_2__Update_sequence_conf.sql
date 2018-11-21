UPDATE sequence_configuration SET maxTestedProposalCount = 1000, sequenceSize = 12, maxVotes = 1500;
ALTER TABLE sequence_configuration ALTER COLUMN question_id DROP DEFAULT;
COMMIT;