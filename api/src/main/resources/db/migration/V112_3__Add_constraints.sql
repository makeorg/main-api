BEGIN;

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_main
  FOREIGN KEY(main) REFERENCES exploration_sequence_configuration(exploration_sequence_configuration_id);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_controversial
  FOREIGN KEY (controversial) REFERENCES specific_sequence_configuration(id);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_popular
  FOREIGN KEY (popular) REFERENCES specific_sequence_configuration(id);

ALTER TABLE sequence_configuration
  ADD CONSTRAINT fk_configuration_keyword
  FOREIGN KEY(keyword) REFERENCES specific_sequence_configuration(id);

COMMIT;
