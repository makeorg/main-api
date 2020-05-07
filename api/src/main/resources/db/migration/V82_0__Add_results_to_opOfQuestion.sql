ALTER TABLE IF EXISTS operation_of_question
    ADD COLUMN IF NOT EXISTS results_link STRING NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS proposals_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS participants_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS actions STRING NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS featured BOOLEAN DEFAULT false;

COMMIT;