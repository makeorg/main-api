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
  id VARCHAR(256) PRIMARY KEY,
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
  theme_id VARCHAR(256) NOT NULL REFERENCES theme,
  language VARCHAR(3),
  title VARCHAR(256),
  slug VARCHAR(256)
);
%
INSERT into oauth_client
    (uuid, secret, allowed_grant_types)
    VALUES
    ('#clientid#', '#clientsecret#', '{"password", "refresh_token", "client_credentials"}');
%
INSERT INTO tag (slug, label, enabled) VALUES
	('absenteisme-activite', e'absentéisme / activité', true),
	('acces-a-l-emploi', e'accès à l\'emploi', true),
	('acces-aux-soins', e'accès aux soins', true),
	('accessibilite', e'accessibilité', true),
	('accession', 'accession', true),
	('accords', 'accords', true),
	('acquis-sociaux', 'acquis sociaux', true),
	('acquisition', 'acquisition', true),
	('addictions', 'addictions', true),
	('affaires', 'affaires', true),
	('agriculture', 'agriculture', true),
	('agriculture-intensive', 'agriculture intensive', true),
	('aides', 'aides', true),
	('aides-de-l-etat', e'aides de l\'etat', true),
	('alimentation', 'alimentation', true),
	('animaux', 'animaux', true),
	('application-des-peines', 'application (des peines)', true),
	('apprentissage', 'apprentissage', true),
	('armes', 'armes', true),
	('bio-le-bio', 'bio / le bio', true),
	('budget-depenses', e'budget / dépenses', true),
	('carburant-s', 'carburant(s)', true),
	('carriere', e'carrière', true),
	('charges', 'charges', true),
	('chomage', e'chômage', true),
	('circuit-courts', 'circuit-courts', true),
	('collectivite-s', e'collectivité(s)', true),
	('collectivites', e'collectivités', true),
	('commerce-international', 'commerce international', true),
	('competitivite', e'compétitivité', true),
	('compostage', 'compostage', true),
	('conditions-de-travail', 'conditions de travail', true),
	('conflits', 'conflits', true),
	('consommation', 'consommation', true),
	('construction', 'construction', true),
	('contrat-de-travail', 'contrat de travail', true),
	('controle-des-institutions', e'contrôle (des institutions)', true),
	('cooperation-internationale', e'coopération internationale', true),
	('coupables', 'coupables', true),
	('crimes', 'crimes', true),
	('croissance', 'croissance', true),
	('culture', 'culture', true),
	('decrochage', e'décrochage', true),
	('defense', e'défense', true),
	('delits', e'délits', true),
	('delocalisation', e'délocalisation', true),
	('derives', e'dérives', true),
	('devoirs', 'devoirs', true),
	('diplomes', e'diplômes', true),
	('discipline', 'discipline', true),
	('discrimination', 'discrimination', true),
	('drogues', 'drogues', true),
	('eco-conception', e'éco-conception', true),
	('ecole-primaire', e'école primaire', true),
	('economie-du-partage', e'économie du partage', true),
	('education-civique', e'éducation civique', true),
	('effectifs', 'effectifs', true),
	('elections', e'élections', true),
	('electricite', e'électricité', true),
	('eligibilite', e'éligibilité', true),
	('energies-fossiles-energies-sales', e'énergies fossiles / énergies sales', true),
	('energies-renouvelables-propres', e'énergies renouvelables / propres', true),
	('enfants', 'enfants', true),
	('enseignants', 'enseignants', true),
	('enseignement-secondaire', 'enseignement secondaire', true),
	('enseignement-superieur', e'enseignement supérieur', true),
	('entrepreneuriat', 'entrepreneuriat', true),
	('entreprise-s', 'entreprise(s)', true),
	('entreprises', 'entreprises', true),
	('epanouissement', e'épanouissement', true),
	('etats-unis', 'etats-unis', true),
	('ethique', e'éthique', true),
	('etiquettage-label', e'étiquettage / label', true),
	('etudiants', e'étudiants', true),
	('examens', 'examens', true),
	('finance', 'finance', true),
	('financement', 'financement', true),
	('fiscalite', e'fiscalité', true),
	('flux-migratoires', 'flux migratoires', true),
	('fonction-publique-fonctionnaires', 'fonction publique / fonctionnaires', true),
	('formation', 'formation', true),
	('gachis', e'gâchis', true),
	('gerance', e'gérance', true),
	('gouvernance', 'gouvernance', true),
	('handicap', 'handicap', true),
	('harcelement', e'harcèlement', true),
	('immobilier', 'immobilier', true),
	('importations', 'importations', true),
	('impots', e'impôts', true),
	('impunite', e'impunité', true),
	('industrie', 'industrie', true),
	('inegalites', e'inégalités', true),
	('influence', 'influence', true),
	('infrastructures', 'infrastructures', true),
	('insalubrite', e'insalubrité', true),
	('institutions', 'institutions', true),
	('langue', 'langue', true),
	('langues', 'langues', true),
	('les-elus', e'les élus', true),
	('les-institutions', '(les) institutions', true),
	('licenciement', 'licenciement', true),
	('location', 'location', true),
	('logements-sociaux', 'logements sociaux', true),
	('loisirs', 'loisirs', true),
	('loyers', 'loyers', true),
	('mandats', 'mandats', true),
	('marche-immobilier', e'marché immobilier', true),
	('matieres', e'matières', true),
	('medias', e'médias', true),
	('metiers', e'métiers', true),
	('militaires', 'militaires', true),
	('mineurs', 'mineurs', true),
	('minorites', e'minorités', true),
	('monnaie', 'monnaie', true),
	('niveau-de-vie', 'niveau de vie', true),
	('numerique', e'numérique', true),
	('ogms', 'ogms', true),
	('orientation', 'orientation', true),
	('parents', 'parents', true),
	('parite', e'parité', true),
	('particulier-s', 'particulier(s)', true),
	('particuliers', 'particuliers', true),
	('patrimoine', 'patrimoine', true),
	('patronnat', 'patronnat', true),
	('pedagogie', e'pédagogie', true),
	('peines', 'peines', true),
	('permaculture', 'permaculture', true),
	('personnel-soignant', 'personnel soignant', true),
	('pesticides', 'pesticides', true),
	('photovoltaique', e'photovoltaïque', true),
	('police', 'police', true),
	('pollution', 'pollution', true),
	('populations', 'populations', true),
	('precarite', e'précarité', true),
	('pret-a-porter', e'prêt-à-porter', true),
	('processus-legislatif', e'processus législatif', true),
	('propriete', e'propriété', true),
	('protection', 'protection', true),
	('protectionnisme', 'protectionnisme', true),
	('recherche', 'recherche', true),
	('recyclage', 'recyclage', true),
	('referendum', e'référendum', true),
	('reglementation', e'réglementation', true),
	('religion', 'religion', true),
	('remboursement-des-soins', 'remboursement des soins', true),
	('remuneration', e'rémunération', true),
	('ressources-pedagogiques', e'ressources pédagogiques', true),
	('restauration', 'restauration', true),
	('retraite', 'retraite', true),
	('revente', 'revente', true),
	('richesses', 'richesses', true),
	('role-du-citoyen', e'rôle du citoyen', true),
	('rythmes-scolaires', 'rythmes scolaires', true),
	('salaires', 'salaires', true),
	('sante', e'santé', true),
	('securite-routiere', e'sécurité routière', true),
	('seniors', 'seniors', true),
	('sensibilisation', 'sensibilisation', true),
	('solidarite', e'solidarité', true),
	('sortie-de-l-europe', e'sortie de l\'europe', true),
	('soutien-scolaire', 'soutien scolaire', true),
	('stages', 'stages', true),
	('statut-de-l-elu', e'statut de l\'élu', true),
	('statuts', 'statuts', true),
	('strategie-economique', e'stratégie économique', true),
	('subventions', 'subventions', true),
	('systeme-carceral', e'système carcéral', true),
	('systeme-judiciaire', e'système judiciaire', true),
	('taxes-taxation', 'taxes / taxation', true),
	('territoire', 'territoire', true),
	('terrorisme', 'terrorisme', true),
	('transition', 'transition', true),
	('transports-propres', 'transports propres', true),
	('travailleurs-independants', e'travailleurs indépendants', true),
	('uniforme', 'uniforme', true),
	('union-europeenne', e'union européenne', true),
	('urbanisme', 'urbanisme', true),
	('vegetalisation', e'végétalisation', true),
	('vegetarisme-vegetariens', e'végétarisme / végétariens', true),
	('victimes', 'victimes', true),
	('vie-en-entreprise', 'vie en entreprise', true),
	('violence', 'violence', true),
	('vivre-ensemble', 'vivre ensemble', true),
	('vote-blanc', 'vote blanc', true),
	('zero-dechets', e'zéro-déchets', true);
%
INSERT INTO theme
    (id, actions_count, proposals_count, country, color, gradient_from, gradient_to, tags_ids)
    VALUES
    ('4f79b301-6735-4e88-ad36-69320d69cf2e', 0, 0, 'FR', '#E91E63', '#E81E61', '#7F2FD0', 'les-institutions,elections,financement,ethique,statut-de-l-elu,absenteisme-activite,processus-legislatif,influence,controle-des-institutions,les-elus,collectivites,parite,mandats,eligibilite,remuneration,affaires,vote-blanc,budget-depenses,referendum,role-du-citoyen'),
    ('036f24fa-dc32-4808-bca9-7ccec1665585', 0, 0, 'FR', '#8BC34A', '#83BB1A', '#1FC8F1', 'energies-fossiles-energies-sales,carburant-s,alimentation,permaculture,bio-le-bio,sensibilisation,taxes-taxation,gachis,compostage,urbanisme,collectivite-s,electricite,ogms,sante,vegetalisation,recherche,pollution,impots,zero-dechets,recyclage,reglementation,entreprise-s,pesticides,energies-renouvelables-propres,vegetarisme-vegetariens,photovoltaique,eco-conception,transports-propres,subventions,economie-du-partage,circuit-courts,pret-a-porter,particulier-s'),
    ('b3b37b86-1198-4e06-94ff-cb04f57c6b67', 0, 0, 'FR', '#26A69A', '#26A69A', '#1CEBA0', 'pesticides,agriculture,sensibilisation,alimentation,agriculture,sante,acces-aux-soins,agriculture-intensive,zero-dechets,ogms,vegetarisme-vegetariens,consommation,reglementation,particulier-s,bio-le-bio,institutions,importations,addictions,animaux,recherche,remboursement-des-soins,etiquettage-label,handicap,personnel-soignant'),
    ('706b277c-3db8-403c-b3c9-7f69939181df', 0, 0, 'FR', '#673AB7', '#9173C6', '#EE98D7', 'ecole-primaire,enseignement-secondaire,enseignement-superieur,carriere,orientation,soutien-scolaire,pedagogie,effectifs,infrastructures,loisirs,epanouissement,matieres,decrochage,examens,rythmes-scolaires,handicap,discipline,violence,parents,enfants,enseignants,harcelement,stages,apprentissage,devoirs,uniforme,langues,numerique,restauration,ressources-pedagogiques,education-civique,sante,diplomes'),
    ('bae38574-56a7-4b91-8540-1428e737881a', 0, 0, 'FR', '#0E75C6', '#0E75C6', '#41CED6', 'formation,acces-a-l-emploi,statuts,discrimination,orientation,chomage,metiers,entreprises,commerce-international,vie-en-entreprise,precarite,acquis-sociaux,derives,immobilier,territoire,niveau-de-vie,competitivite,monnaie,finance,patronnat,retraite,union-europeenne,strategie-economique,industrie,impots,salaires,solidarite,transition,entrepreneuriat,patrimoine,contrat-de-travail,fonction-publique-fonctionnaires,licenciement,aides-de-l-etat,travailleurs-independants,conditions-de-travail,delocalisation,croissance'),
    ('d6f4d333-2dc8-493b-aeac-e4a8f9a0f9af', 0, 0, 'FR', '#FF9047', '#FF9047', '#B7588B', 'sensibilisation,securite-routiere,territoire,religion,crimes,police,peines,impunite,delits,systeme-carceral,systeme-judiciaire,terrorisme,mineurs,victimes,coupables,application-des-peines,vivre-ensemble,armes,medias,minorites,defense,drogues,inegalites,militaires'),
    ('980e6688-c169-4810-98d8-2b68043559bd', 0, 0, 'FR', '#FF9800', '#FF9800', '#FFEA9F', 'marche-immobilier,accession,propriete,acquisition,revente,insalubrite,financement,entreprises,particuliers,logements-sociaux,aides,gerance,accessibilite,location,precarite,protection,etudiants,charges,fiscalite,loyers,construction'),
    ('f1990dfb-7b92-47cd-8015-e7ca0f961006', 0, 0, 'FR', '#F9E42A', '#ECD400', '#FF9FFD', 'seniors'),
    ('969118ae-949f-4a4a-af33-ec1bcc107450', 0, 0, 'FR', '#2E7D32', '#2E7D32', '#8FCF4B', 'agriculture-intensive'),
    ('8a4a9d0a-92b8-48e4-946d-3e6986296ec7', 0, 0, 'FR', '#311B92', '#311B92', '#54A0E3', 'territoire,gouvernance,institutions,monnaie,richesses,flux-migratoires,accords,derives,culture,fiscalite,cooperation-internationale,sortie-de-l-europe,commerce-international,langue,etats-unis,populations,conflits,protectionnisme,defense'),
    ('5f6bc4e8-e353-4afa-8001-24232b2f8816', 0, 0, 'FR', '#F5515F', '#F5515F', '#9F031B', ''),
    ('6fb8d14a-388c-4713-a8a9-52ef89de3888', 0, 0, 'FR', '#03A9F4', '#4FC8FF', '#FFDC00', '');
%
INSERT INTO theme_translation
    (theme_id, language, slug, title)
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
    ('5f6bc4e8-e353-4afa-8001-24232b2f8816', 'fr', 'transports-deplacement', 'transports / déplacement'),
    ('6fb8d14a-388c-4713-a8a9-52ef89de3888', 'fr', 'numerique-culture', 'numérique / culture');
%