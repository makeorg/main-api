BEGIN;

CREATE INDEX index_configuration_main ON sequence_configuration(main);
CREATE INDEX index_configuration_controversial ON sequence_configuration(controversial);
CREATE INDEX index_configuration_popular ON sequence_configuration(popular);
CREATE INDEX index_configuration_keyword ON sequence_configuration(keyword);

COMMIT;
