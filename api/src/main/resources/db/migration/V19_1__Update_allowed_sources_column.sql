UPDATE operation SET allowed_sources = ARRAY['core'];

UPDATE operation SET allowed_sources = ARRAY['huffingpost'] WHERE slug = 'ecologie-huffpost' OR slug = 'culture-huffpost' OR
slug = 'economie-huffpost' OR slug = 'international-huffpost' OR slug = 'politique-huffpost' OR slug = 'education-huffpost'
OR slug = 'societe-huffpost';

COMMIT;
