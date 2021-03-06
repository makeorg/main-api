BEGIN;

INSERT INTO tag (slug, label, enabled, created_at, updated_at) VALUES
    ('action--des-associations', e'action : des associations', true, NOW(), NOW()),
    ('action--des-entreprises', e'action : des entreprises', true, NOW(), NOW()),
    ('action--des-individus', e'action : des individus', true, NOW(), NOW()),
    ('action--des-syndicats', e'action : des syndicats', true, NOW(), NOW()),
    ('cible--collectivites-territoriales', e'cible : collectivités territoriales', true, NOW(), NOW()),
    ('cible--associations', e'cible : associations', true, NOW(), NOW()),
    ('cible--citadins', e'cible : citadins', true, NOW(), NOW()),
    ('cible--elus', e'cible : élus', true, NOW(), NOW()),
    ('cible--entreprises', e'cible : entreprises', true, NOW(), NOW()),
    ('cible--etats--gouvernements', e'cible : États & gouvernements', true, NOW(), NOW()),
    ('cible--individus', e'cible : individus', true, NOW(), NOW()),
    ('cible--ruraux', e'cible : ruraux', true, NOW(), NOW()),
    ('civisme', e'civisme', true, NOW(), NOW()),
    ('consommation', e'consommation', true, NOW(), NOW()),
    ('couverture-sociale', e'couverture sociale', true, NOW(), NOW()),
    ('droits-libertes', e'droits & libertés', true, NOW(), NOW()),
    ('effort-individuel', e'effort individuel', true, NOW(), NOW()),
    ('emploi', e'emploi', true, NOW(), NOW()),
    ('engagement-associatif', e'engagement associatif', true, NOW(), NOW()),
    ('laicite-religions', e'laïcité & religions', true, NOW(), NOW()),
    ('mixite-sociale', e'mixité sociale', true, NOW(), NOW()),
    ('mobilite', e'mobilité', true, NOW(), NOW()),
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
