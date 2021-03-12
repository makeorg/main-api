BEGIN;

ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS sequence_size;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS newProposalRatio;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS maxTestedProposalCount;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS selectionAlgorithmName;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS intraIdeaEnabled;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS intraIdeaMinCount;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS intraIdeaProposalsRatio;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS interIdeaCompetitionEnabled;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS interIdeaCompetitionTargetCount;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS interIdeaCompetitionControversialRatio;
ALTER TABLE sequence_configuration DROP COLUMN IF EXISTS interIdeaCompetitionControversialCount;

COMMIT;