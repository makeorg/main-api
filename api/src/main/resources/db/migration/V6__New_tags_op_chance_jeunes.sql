INSERT INTO tag (slug, label, enabled, created_at, updated_at) VALUES
    ('civisme', e'civisme', true, NOW(), NOW()),
    ('consommation', e'consommation', true, NOW(), NOW()),
    ('couverture-sociale', e'couverture sociale', true, NOW(), NOW()),
    ('droits-libertes', e'droits & libertés', true, NOW(), NOW()),
    ('effort-individuel', e'effort individuel', true, NOW(), NOW()),
    ('emploi', e'emploi', true, NOW(), NOW()),
    ('engagement-associatif', e'engagement associatif', true, NOW(), NOW()),
    ('laicite-religions', e'laïcité & religions', true, NOW(), NOW()),
    ('mixite-sociale', e'mixité sociale', true, NOW(), NOW()),
    ('numerique', e'numérique', true, NOW(), NOW()),
    ('pauvrete-precarite', e'pauvreté & précarité', true, NOW(), NOW()),
    ('politique-economique', e'politique économique', true, NOW(), NOW()),
    ('regulation', e'régulation', true, NOW(), NOW()),
    ('securite', e'sécurité', true, NOW(), NOW()),
    ('services-publics', e'services publics', true, NOW(), NOW()),
    ('sport', e'sport', true, NOW(), NOW()),
    ('villes', e'villes', true, NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;

COMMIT;