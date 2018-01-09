CREATE DATABASE IF NOT EXISTS #dbname#;
%
CREATE TABLE IF NOT EXISTS make_user (
  uuid STRING PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  email VARCHAR(255) NOT NULL,
  first_name VARCHAR(512) DEFAULT NULL,
  last_name VARCHAR(512) DEFAULT NULL,
  last_ip VARCHAR(50) DEFAULT NULL,
  hashed_password VARCHAR(512) DEFAULT NULL,
  enabled BOOLEAN NOT NULL,
  verified BOOLEAN NOT NULL,
  last_connection TIMESTAMP WITH TIME ZONE DEFAULT NULL,
  verification_token VARCHAR(255) DEFAULT NULL,
  verification_token_expires_at TIMESTAMP WITH TIME ZONE,
  reset_token VARCHAR(255) DEFAULT NULL,
  reset_token_expires_at TIMESTAMP WITH TIME ZONE,
  roles TEXT NOT NULL,

  date_of_birth DATE DEFAULT NULL,
  avatar_url VARCHAR(512) DEFAULT NULL,
  profession VARCHAR(512) DEFAULT NULL,
  phone_number VARCHAR(50) DEFAULT NULL,
  twitter_id VARCHAR(255) DEFAULT NULL,
  facebook_id VARCHAR(255) DEFAULT NULL,
  google_id VARCHAR(255) DEFAULT NULL,
  gender VARCHAR(1) DEFAULT NULL,
  gender_name VARCHAR(20) DEFAULT NULL,
  postal_code VARCHAR(10) DEFAULT NULL,
  karma_level INT DEFAULT 0,
  locale VARCHAR(8) DEFAULT NULL,
  opt_in_newsletter BOOLEAN DEFAULT FALSE NOT NULL
);
%
CREATE UNIQUE index IF NOT EXISTS email_unique_index ON make_user (email);
%
CREATE TABLE IF NOT EXISTS oauth_client (
  uuid VARCHAR(256) PRIMARY KEY,
  secret VARCHAR(256) NOT NULL,
  allowed_grant_types VARCHAR(256),
  scope VARCHAR(2048),
  redirect_uri VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
%
CREATE TABLE IF NOT EXISTS auth_code (
  authorization_code VARCHAR(256) PRIMARY KEY,
  scope VARCHAR(2048),
  redirect_uri VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  expires_in INT NOT NULL,
  make_user_uuid VARCHAR(256) NOT NULL REFERENCES make_user,
  client_uuid VARCHAR(256) NOT NULL REFERENCES oauth_client
);
%
CREATE TABLE IF NOT EXISTS access_token (
  access_token VARCHAR(256) PRIMARY KEY,
  refresh_token VARCHAR(256),
  scope VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  expires_in INT NOT NULL,
  make_user_uuid VARCHAR(256) NOT NULL REFERENCES make_user,
  client_uuid VARCHAR(256) NOT NULL REFERENCES oauth_client
);
%
CREATE TABLE IF NOT EXISTS tag (
  slug VARCHAR(256) PRIMARY KEY,
  label VARCHAR(256),
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
%
CREATE TABLE IF NOT EXISTS theme (
  uuid  VARCHAR(256) PRIMARY KEY,
  actions_count INT,
  proposals_count INT,
  country VARCHAR(3),
  color VARCHAR(7),
  gradient_from VARCHAR(7),
  gradient_to VARCHAR(7),
  tags_ids VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
%
CREATE TABLE IF NOT EXISTS theme_translation (
  theme_uuid VARCHAR(256) NOT NULL REFERENCES theme,
  language VARCHAR(3),
  title VARCHAR(1024),
  slug VARCHAR(256),
  PRIMARY KEY (theme_uuid, language)
);
%
CREATE TABLE IF NOT EXISTS idea (
  id  VARCHAR(256) PRIMARY KEY,
  name STRING,
  operation VARCHAR(256),
  question VARCHAR(256),
  country VARCHAR(3),
  language VARCHAR(3),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
%
ALTER TABLE IF EXISTS idea RENAME COLUMN operation TO operation_id
%
INSERT into oauth_client
    (uuid, secret, allowed_grant_types)
    VALUES
    ('#clientid#', '#clientsecret#', '{"password", "refresh_token", "client_credentials"}')
    ON CONFLICT (uuid) DO NOTHING;
%
INSERT INTO tag (slug, label, enabled) VALUES
	('acces-a-l-emploi', e'accès à l\'emploi', true),
	('acces-a-la-culture', e'accès à la culture', true),
	('acces-a-la-propriete', e'accès à la propriété', true),
	('acces-au-logement', e'accès au logement', true),
	('acces-aux-soins', e'accès aux soins', true),
	('accessibilite', e'accessibilité', true),
	('action-publique', e'action publique', true),
	('actionnaires', e'actionnaires', true),
	('addictions', e'addictions', true),
	('afrique', e'Afrique', true),
	('agissements-sexistes', e'agissements sexistes', true),
	('agressions-physiques', e'agressions physiques', true),
	('agriculture', e'agriculture', true),
	('agriculture-raisonnee', e'agriculture raisonnée', true),
	('aide-au-developpement', e'aide au développement', true),
	('aide-humanitaire', e'aide humanitaire', true),
	('aides', e'aides', true),
	('allocations-aides', e'allocations & aides', true),
	('amerique-du-nord', e'Amérique du Nord', true),
	('amerique-latine', e'Amérique Latine', true),
	('animaux', e'animaux', true),
	('armee', e'armée', true),
	('asie', e'Asie', true),
	('associations', e'associations', true),
	('bien-etre-au-travail', e'bien-être au travail', true),
	('bien-etre-en-classe', e'bien-être en classe', true),
	('bio', e'bio', true),
	('budget-depenses', e'budget / dépenses', true),
	('chomage', e'chômage', true),
	('cinema', e'cinéma', true),
	('circuits-courts', e'circuits-courts', true),
	('collectivites-locales', e'collectivités locales', true),
	('commerce', e'commerce', true),
	('competitivite', e'compétitivité', true),
	('conflits', e'conflits', true),
	('conges-duree-du-travail', e'congés & durée du travail', true),
	('consommation-raisonnee', e'consommation raisonnée', true),
	('consommation-responsable', e'consommation responsable', true),
	('contraception-ivg', e'contraception & IVG', true),
	('cooperation-internationale', e'coopération internationale', true),
	('corps-enseignant', e'corps enseignant ', true),
	('couts-de-scolarite-bourses', e'coûts de scolarité / bourses', true),
	('cuisine', e'cuisine', true),
	('culture-langues', e'culture & langues', true),
	('cursus', e'cursus', true),
	('cyber-harcelement', e'cyber-harcèlement', true),
	('danse', e'danse', true),
	('deconnexion', e'déconnexion', true),
	('decrochage-soutien', e'décrochage / soutien', true),
	('deradicalisation', e'déradicalisation', true),
	('dessin', e'dessin', true),
	('diplomatie', e'diplomatie', true),
	('discipline', e'discipline', true),
	('discriminations', e'discriminations', true),
	('dispositifs-de-securite', e'dispositifs de sécurité', true),
	('distribution', e'distribution', true),
	('drogues-et-trafics', e'drogues et trafics', true),
	('droit-du-travail', e'droit du travail', true),
	('ecologie', e'écologie', true),
	('economie', e'économie', true),
	('economie-solidaire', e'économie solidaire', true),
	('education-civique', e'éducation civique', true),
	('education-sensibilisation', e'éducation & sensibilisation', true),
	('effectifs', e'effectifs', true),
	('egalite', e'égalité ', true),
	('egalite-des-chances', e'égalité des chances', true),
	('egalite-hommes-femmes', e'égalité hommes femmes', true),
	('elections-vote', e'élections & vote', true),
	('energies-renouvelables', e'énergies renouvelables', true),
	('energies-traditionnelles', e'énergies traditionnelles', true),
	('entrepreneuriat', e'entrepreneuriat', true),
	('entreprises-collectivites', e'entreprises & collectivités', true),
	('equipement-materiel', e'équipement / matériel', true),
	('etiquetage-label', e'étiquetage & label', true),
	('etudiants', e'étudiants', true),
	('europe', e'europe', true),
	('evaluation-de-l-eleve', e'évaluation de l\'élève', true),
	('exclusion-sociale', e'exclusion sociale', true),
	('exemplarite', e'exemplarité', true),
	('famille', e'famille', true),
	('finance-monnaie', e'finance & monnaie', true),
	('financement', e'financement', true),
	('fiscalite', e'fiscalité', true),
	('fiscalite-subventions', e'fiscalité & subventions', true),
	('fiscalite-taxes', e'fiscalité & taxes', true),
	('fonctionnaires', e'fonctionnaires', true),
	('formation', e'formation', true),
	('fracture-numerique', e'fracture numérique', true),
	('gafa', e'GAFA', true),
	('gastronomie', e'gastronomie', true),
	('habitudes-alimentaires', e'habitudes alimentaires', true),
	('handicap', e'handicap', true),
	('harcelement', e'harcèlement', true),
	('harcelement-violence', e'harcèlement / violence', true),
	('hebergement', e'hébergement', true),
	('image-des-femmes', e'image des femmes', true),
	('immigration', e'immigration', true),
	('independance-financiere', e'indépendance financière', true),
	('industrie', e'industrie', true),
	('industrie-agroalimentaire', e'industrie agroalimentaire', true),
	('information', e'information', true),
	('information-labels', e'information & labels', true),
	('information-transparence', e'information & transparence', true),
	('insalubrite', e'insalubrité', true),
	('institutions', e'institutions', true),
	('international', e'international', true),
	('jeunesse', e'jeunesse', true),
	('laicite', e'laïcité', true),
	('lien-social', e'lien social', true),
	('local', e'local', true),
	('logements-sociaux', e'logements sociaux', true),
	('logements-vacants', e'logements vacants', true),
	('loisirs', e'loisirs ', true),
	('loyers', e'loyers', true),
	('malbouffe', e'malbouffe', true),
	('marche-immobilier', e'marché immobilier', true),
	('medias', e'médias', true),
	('methodes-d-apprentissage', e'méthodes d\'apprentissage', true),
	('minorites', e'minorités', true),
	('mode', e'mode', true),
	('monde-du-travail', e'monde du travail', true),
	('monde-medical', e'monde médical', true),
	('monnaie', e'monnaie', true),
	('moyen-orient', e'Moyen-Orient', true),
	('musees', e'musées', true),
	('musique', e'musique', true),
	('nouvelles-technologies', e'nouvelles technologies', true),
	('nucleaire', e'nucléaire', true),
	('oceanie', e'Océanie', true),
	('ogms-pesticides', e'ogms & pesticides', true),
	('orientation', e'orientation', true),
	('parents-d-eleves', e'parents d\'élèves', true),
	('participation-citoyenne', e'participation citoyenne', true),
	('patrimoine', e'patrimoine', true),
	('pedagogie', e'pédagogie', true),
	('peines', e'peines', true),
	('peinture', e'peinture', true),
	('permaculture', e'permaculture', true),
	('plaintes', e'plaintes', true),
	('pme-tpe-startups', e'PME TPE & startups', true),
	('police', e'police', true),
	('police-justice', e'police & justice', true),
	('politique-de-l-education', e'politique de l\'éducation', true),
	('port-d-armes', e'port d\'armes', true),
	('pouvoir-d-achat', e'pouvoir d\'achat', true),
	('prevention', e'prévention', true),
	('prevention-sante', e'prévention & santé ', true),
	('prison', e'prison', true),
	('produits-agricoles', e'produits agricoles', true),
	('produits-chimiques-ogms', e'produits chimiques & ogms', true),
	('professionnalisation', e'professionnalisation', true),
	('programmes-matieres', e'programmes & matières', true),
	('protection', e'protection', true),
	('protection-des-victimes', e'protection des victimes', true),
	('public-prive', e'public / privé', true),
	('publicite', e'publicité', true),
	('quartiers-sensibles', e'quartiers sensibles', true),
	('recherche', e'recherche', true),
	('recyclage-zero-dechets', e'recyclage & zéro-déchets', true),
	('refugies', e'réfugiés', true),
	('reglementation', e'règlementation', true),
	('reinsertion', e'réinsertion', true),
	('renouvellement', e'renouvellement', true),
	('renseignement', e'renseignement', true),
	('reponses', e'réponses', true),
	('representativite', e'représentativité', true),
	('reseaux-de-distribution', e'réseaux de distribution', true),
	('reseaux-sociaux', e'réseaux sociaux', true),
	('retraite', e'retraite', true),
	('revenu-universel-de-base', e'revenu universel de base', true),
	('rythmes-scolaires', e'rythmes scolaires ', true),
	('salaire-remuneration', e'salaire / rémunération', true),
	('sante', e'santé', true),
	('sdf', e'SDF', true),
	('secu', e'Sécu', true),
	('securite-routiere', e'sécurité routière', true),
	('seniors', e'seniors', true),
	('seniors-juniors', e'seniors / juniors', true),
	('sensibilisation', e'sensibilisation', true),
	('sensibilisation-education', e'sensibilisation & éducation', true),
	('sensibilisation-prevention', e'sensibilisation & prévention', true),
	('service-civique', e'service civique', true),
	('signalement', e'signalement', true),
	('solidarite', e'solidarité', true),
	('soutien-psychologique', e'soutien psychologique', true),
	('startups', e'startups', true),
	('statut-de-l-elu', e'statut de l\'élu', true),
	('street-art', e'street art', true),
	('surpopulation', e'surpopulation', true),
	('syndicats', e'syndicats', true),
	('systeme-de-sante', e'système de santé', true),
	('systeme-judiciaire', e'système judiciaire', true),
	('television', e'télévision', true),
	('terrorisme', e'terrorisme', true),
	('theatre', e'théâtre', true),
	('traditions-nefastes-mutilations', e'traditions néfastes & mutilations', true),
	('transports', e'transports', true),
	('transports-en-commun', e'transports en commun', true),
	('travailleurs-independants', e'travailleurs indépendants', true),
	('union-europeenne', e'Union Européenne', true),
	('urbanisme', e'urbanisme', true),
	('urbanisme-habitat', e'urbanisme & habitat', true),
	('vegetarien', e'végétarien', true),
	('vie-rurale', e'vie rurale', true),
	('violences-conjugales', e'violences conjugales', true),
	('violences-sexuelles', e'violences-sexuelles', true)
	ON CONFLICT (slug) DO NOTHING;
%
INSERT INTO theme
    (uuid, actions_count, proposals_count, country, color, gradient_from, gradient_to, tags_ids)
    VALUES
    ('4f79b301-6735-4e88-ad36-69320d69cf2e', 0, 0, 'FR', '#E91E63', '#E81E61', '#7F2FD0', 'budget-depenses|collectivites-locales|education-civique|egalite-hommes-femmes|elections-vote|exemplarite|information-transparence|institutions|international|laicite|nouvelles-technologies|participation-citoyenne|renouvellement|representativite|statut-de-l-elu'),
    ('036f24fa-dc32-4808-bca9-7ccec1665585', 0, 0, 'FR', '#8BC34A', '#83BB1A', '#1FC8F1', 'action-publique|agriculture|bio|circuits-courts|consommation-responsable|energies-renouvelables|energies-traditionnelles|entreprises-collectivites|fiscalite-subventions|international|nouvelles-technologies|nucleaire|ogms-pesticides|recherche|recyclage-zero-dechets|sante|sensibilisation-education|transports|transports-en-commun|urbanisme-habitat'),
    ('b3b37b86-1198-4e06-94ff-cb04f57c6b67', 0, 0, 'FR', '#26A69A', '#26A69A', '#1CEBA0', 'acces-aux-soins|addictions|bio|cuisine|etiquetage-label|fiscalite|habitudes-alimentaires|handicap|industrie-agroalimentaire|malbouffe|nouvelles-technologies|produits-chimiques-ogms|publicite|recherche|reseaux-de-distribution|secu|sensibilisation|systeme-de-sante|vegetarien'),
    ('706b277c-3db8-403c-b3c9-7f69939181df', 0, 0, 'FR', '#9173C5', '#9173C6', '#EE98D7', 'bien-etre-en-classe|corps-enseignant|couts-de-scolarite-bourses|cursus|decrochage-soutien|discipline|discriminations|ecologie|education-civique|effectifs|egalite-des-chances|equipement-materiel|europe|evaluation-de-l-eleve|handicap|harcelement-violence|international|loisirs|methodes-d-apprentissage|nouvelles-technologies|orientation|parents-d-eleves|politique-de-l-education|prevention-sante|professionnalisation|programmes-matieres|public-prive|rythmes-scolaires|solidarite'),
    ('bae38574-56a7-4b91-8540-1428e737881a', 0, 0, 'FR', '#0E75C6', '#0E75C6', '#41CED6', 'acces-a-l-emploi|action-publique|actionnaires|allocations-aides|bien-etre-au-travail|chomage|competitivite|conges-duree-du-travail|droit-du-travail|ecologie|economie-solidaire|egalite-hommes-femmes|entrepreneuriat|exemplarite|finance-monnaie|fiscalite|fonctionnaires|formation|industrie|international|local|nouvelles-technologies|pme-tpe-startups|pouvoir-d-achat|retraite|revenu-universel-de-base|salaire-remuneration|seniors-juniors|syndicats|travailleurs-independants|union-europeenne'),
    ('d6f4d333-2dc8-493b-aeac-e4a8f9a0f9af', 0, 0, 'FR', '#B7588B', '#FF9047', '#B7588B', 'armee|cooperation-internationale|deradicalisation|dispositifs-de-securite|drogues-et-trafics|exclusion-sociale|medias|nouvelles-technologies|peines|police|port-d-armes|prison|quartiers-sensibles|renseignement|securite-routiere|sensibilisation-prevention|systeme-judiciaire|terrorisme'),
    ('980e6688-c169-4810-98d8-2b68043559bd', 0, 0, 'FR', '#FF9800', '#FF9800', '#FFEA9F', 'acces-a-la-propriete|acces-au-logement|aides|ecologie|etudiants|fiscalite-taxes|insalubrite|logements-sociaux|logements-vacants|loyers|marche-immobilier|nouvelles-technologies|sdf|solidarite|surpopulation|urbanisme'),
    ('f1990dfb-7b92-47cd-8015-e7ca0f961006', 0, 0, 'FR', '#F9E42A', '#ECD400', '#FF9FFD', 'accessibilite|aides|associations|contraception-ivg|discriminations|egalite|famille|information|jeunesse|laicite|lien-social|minorites|reinsertion|seniors|service-civique|solidarite'),
    ('969118ae-949f-4a4a-af33-ec1bcc107450', 0, 0, 'FR', '#2E7D32', '#2E7D32', '#8FCF4B', 'action-publique|agriculture-raisonnee|animaux|bio|consommation-raisonnee|distribution|fiscalite|formation|industrie-agroalimentaire|information-labels|local|nouvelles-technologies|permaculture|produits-agricoles|produits-chimiques-ogms|union-europeenne|vie-rurale'),
    ('8a4a9d0a-92b8-48e4-946d-3e6986296ec7', 0, 0, 'FR', '#311B92', '#311B92', '#54A0E3', 'afrique|aide-au-developpement|aide-humanitaire|amerique-du-nord|amerique-latine|asie|commerce|conflits|cooperation-internationale|culture-langues|diplomatie|ecologie|economie|immigration|institutions|monnaie|moyen-orient|oceanie|refugies|reglementation|terrorisme|union-europeenne'),
    ('6fb8d14a-388c-4713-a8a9-52ef89de3888', 0, 0, 'FR', '#03A9F4', '#4FC8FF', '#FFDC00', 'acces-a-la-culture|action-publique|cinema|danse|deconnexion|dessin|financement|fiscalite|formation|fracture-numerique|gafa|gastronomie|mode|musees|musique|nouvelles-technologies|patrimoine|peinture|reglementation|reseaux-sociaux|sensibilisation|startups|street-art|television|theatre')
    ON CONFLICT (uuid) DO NOTHING;
%
INSERT INTO theme_translation
    (theme_uuid, language, slug, title)
    VALUES
    ('4f79b301-6735-4e88-ad36-69320d69cf2e', 'fr', 'democratie-vie-politique', 'démocratie / vie politique'),
    ('036f24fa-dc32-4808-bca9-7ccec1665585', 'fr', 'developpement-durable-energie', 'développement durable / énergie'),
    ('b3b37b86-1198-4e06-94ff-cb04f57c6b67', 'fr', 'sante-alimentation', 'santé / alimentation'),
    ('706b277c-3db8-403c-b3c9-7f69939181df', 'fr', 'education', 'éducation'),
    ('bae38574-56a7-4b91-8540-1428e737881a', 'fr', 'economie-emploi-travail', 'économie / emploi / travail'),
    ('d6f4d333-2dc8-493b-aeac-e4a8f9a0f9af', 'fr', 'securite-justice', 'sécurité / justice'),
    ('980e6688-c169-4810-98d8-2b68043559bd', 'fr', 'logement', 'logement'),
    ('f1990dfb-7b92-47cd-8015-e7ca0f961006', 'fr', 'vivre-ensemble-solidarites', 'vivre ensemble / solidarités'),
    ('969118ae-949f-4a4a-af33-ec1bcc107450', 'fr', 'agriculture-ruralite', 'agriculture / ruralité'),
    ('8a4a9d0a-92b8-48e4-946d-3e6986296ec7', 'fr', 'europe-monde', 'europe / monde'),
    ('6fb8d14a-388c-4713-a8a9-52ef89de3888', 'fr', 'numerique-culture', 'numérique / culture')
    ON CONFLICT (theme_uuid, language) DO NOTHING;
%
INSERT INTO make_user
VALUES
('11111111-1111-1111-1111-111111111111','2017-09-15 08:43:30','2017-09-15 08:43:30','#adminemail#','#adminfirstname#',NULL,NULL,'#adminencryptedpassword#',true,false,'2017-09-15 08:43:30',NULL,'2017-10-15 08:43:30',NULL,NULL,'ROLE_ADMIN,ROLE_CITIZEN',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,false)
ON CONFLICT (uuid) DO NOTHING
%
CREATE TABLE IF NOT EXISTS operation (
  uuid VARCHAR(256) PRIMARY KEY,
  status VARCHAR(20) NOT NULL,
  slug VARCHAR(256) NOT NULL,
  default_language VARCHAR(3) NOT NULL,
  sequence_landing_id VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
%
CREATE UNIQUE index IF NOT EXISTS operation_slug_unique_index ON operation (slug);
%
CREATE TABLE IF NOT EXISTS operation_translation (
  operation_uuid VARCHAR(256) NOT NULL REFERENCES operation,
  language VARCHAR(3),
  title VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (operation_uuid, language)
);
%
CREATE TABLE IF NOT EXISTS operation_country_configuration (
  operation_uuid VARCHAR(256) NOT NULL REFERENCES operation,
  country VARCHAR(3),
  tag_ids VARCHAR(2048),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  PRIMARY KEY (operation_uuid, country)
);
%
CREATE TABLE IF NOT EXISTS operation_action (
  operation_uuid VARCHAR(256) NOT NULL REFERENCES operation,
  make_user_uuid VARCHAR(256) NOT NULL REFERENCES make_user,
  action_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  action_type VARCHAR(256) NOT NULL,
  arguments STRING
);
%
CREATE INDEX IF NOT EXISTS operation_action_operation_id_index ON operation_action (operation_uuid);
%
CREATE INDEX IF NOT EXISTS operation_action_user_id_index ON make_user (uuid);
%
INSERT INTO operation
    (uuid, status, slug, default_language)
    VALUES
    ('vff', 'Active', 'vff', 'fr')
    ON CONFLICT (uuid) DO NOTHING