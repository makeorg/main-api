BEGIN;

CREATE INDEX index_configuration_main ON sequence_configuration(main);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_main
  FOREIGN KEY(main) REFERENCES exploration_sequence_configuration(exploration_sequence_configuration_id);

CREATE INDEX index_configuration_controversial ON sequence_configuration(controversial);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_controversial
  FOREIGN KEY (controversial) REFERENCES specific_sequence_configuration(id);

CREATE INDEX index_configuration_popular ON sequence_configuration(popular);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_popular
  FOREIGN KEY (popular) REFERENCES specific_sequence_configuration(id);

CREATE INDEX index_configuration_keyword ON sequence_configuration(keyword);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_keyword
  FOREIGN KEY(keyword) REFERENCES specific_sequence_configuration(id);

COMMIT;
