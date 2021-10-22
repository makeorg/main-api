BEGIN;

ALTER TABLE question rename COLUMN country TO countries;

ALTER TABLE idea DROP COLUMN country;
ALTER TABLE idea DROP COLUMN language;

ALTER TABLE tag DROP COLUMN country;
ALTER TABLE tag DROP COLUMN language;

COMMIT;
