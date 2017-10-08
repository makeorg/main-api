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
  title VARCHAR(256),
  slug VARCHAR(256),
  PRIMARY KEY (theme_uuid, language)
);
%
INSERT into oauth_client
    (uuid, secret, allowed_grant_types)
    VALUES
    ('#clientid#', '#clientsecret#', '{"password", "refresh_token", "client_credentials"}')
    ON CONFLICT (uuid) DO NOTHING;
%
INSERT INTO tag (slug, label, enabled) VALUES
	('industrie-agroalimentaire','industrie agroalimentaire',true),
	('energies-traditionnelles',e'énergies traditionnelles',true),
	('seniors-juniors','seniors / juniors',true),
	('actionnaires','actionnaires',true),
	('collectivites-locales',e'collectivités locales',true),
	('fiscalite-taxes',e'fiscalité & taxes',true),
	('aides','aides',true),
	('startups','startups',true),
	('famille','famille',true),
	('discipline','discipline',true),
	('urbanisme-habitat','urbanisme & habitat',true),
	('police','police',true),
	('acces-a-l-emploi',e'accès à l\'emploi',true),
	('reseaux-sociaux',e'réseaux sociaux',true),
	('monnaie','monnaie',true),
	('peines','peines',true),
	('prison','prison',true),
	('conflits','conflits',true),
	('parents-d-eleves',e'parents d\'élèves',true),
	('local','local',true),
	('systeme-judiciaire',e'système judiciaire',true),
	('bio','bio',true),
	('surpopulation','surpopulation',true),
	('acces-aux-soins',e'accès aux soins',true),
	('bien-etre-au-travail',e'bien-être au travail',true),
	('elections-vote',e'élections & vote',true),
	('corps-enseignant','corps enseignant ',true),
	('retraite','retraite',true),
	('recyclage-zero-dechets',e'recyclage & zéro-déchets',true),
	('deradicalisation',e'déradicalisation',true),
	('renouvellement','renouvellement',true),
	('publicite',e'publicité',true),
	('sensibilisation-education',e'sensibilisation & éducation',true),
	('reinsertion',e'réinsertion',true),
	('droit-du-travail','droit du travail',true),
	('economie-solidaire',e'économie solidaire',true),
	('medias',e'médias',true),
	('musique','musique',true),
	('systeme-de-sante',e'système de santé',true),
	('fiscalite-subventions',e'fiscalité & subventions',true),
	('diplomatie','diplomatie',true),
	('afrique','Afrique',true),
	('sensibilisation-prevention',e'sensibilisation & prévention',true),
	('transports-en-commun','transports en commun',true),
	('aide-au-developpement',e'aide au développement',true),
	('service-civique','service civique',true),
	('patrimoine','patrimoine',true),
	('pme','PME',true),
	('economie',e'économie',true),
	('egalite-hommes-femmes',e'égalité hommes femmes',true),
	('conges-duree-du-travail',e'congés & durée du travail',true),
	('agriculture','agriculture',true),
	('jeunesse','jeunesse',true),
	('vie-rurale','vie rurale',true),
	('rythmes-scolaires','rythmes scolaires ',true),
	('nucleaire','nucléaire',true),
	('permaculture','permaculture',true),
	('bien-etre-en-classe',e'bien-être en classe',true),
	('egalite-des-chances',e'égalité des chances',true),
	('international','international',true),
	('energies-renouvelables',e'énergies renouvelables',true),
	('produits-chimiques-ogms','produits chimiques & ogms',true),
	('etiquetage-label',e'étiquetage & label',true),
	('europe','europe',true),
	('evaluation-de-l-eleve',e'évaluation de l\'élève',true),
	('urbanisme','urbanisme',true),
	('aide-humanitaire','aide humanitaire',true),
	('acces-a-la-culture',e'accès à la culture',true),
	('ecologie',e'écologie',true),
	('formation','formation',true),
	('chomage',e'chômage',true),
	('education-civique',e'éducation civique',true),
	('competitivite',e'compétitivité',true),
	('addictions','addictions',true),
	('recherche','recherche',true),
	('fracture-numerique',e'fracture numérique',true),
	('culture-langues','culture & langues',true),
	('laicite',e'laïcité',true),
	('tpe-startups',e'TPE & startups',true),
	('securite-routiere',e'sécurité routière ',true),
	('dispositifs-de-securite',e'dispositifs de sécurité',true),
	('participation-citoyenne','participation citoyenne',true),
	('musees',e'musées',true),
	('exemplarite',e'exemplarité',true),
	('nouvelles-technologies','nouvelles technologies',true),
	('habitudes-alimentaires','habitudes alimentaires',true),
	('egalite',e'égalité ',true),
	('lien-social','lien social',true),
	('reseaux-de-distribution',e'réseaux de distribution',true),
	('sensibilisation','sensibilisation',true),
	('orientation','orientation',true),
	('street-art','street art',true),
	('logements-sociaux','logements sociaux',true),
	('representativite',e'représentativité',true),
	('port-d-armes',e'port d\'armes',true),
	('decrochage-redoublement',e'décrochage / redoublement',true),
	('contraception-ivg','contraception & IVG',true),
	('malbouffe','malbouffe',true),
	('logements-vacants','logements vacants',true),
	('stage-apprentissage','stage / apprentissage',true),
	('deconnexion',e'déconnexion',true),
	('consommation-raisonnee',e'consommation raisonnée',true),
	('fiscalite',e'fiscalité',true),
	('couts-de-scolarite-bourses',e'coûts de scolarité / bourses',true),
	('discriminations','discriminations',true),
	('loisirs','loisirs ',true),
	('ogms-pesticides','ogms & pesticides',true),
	('immigration','immigration',true),
	('professionnalisation','professionnalisation',true),
	('sdf','SDF',true),
	('insalubrite',e'insalubrité',true),
	('produits-agricoles','produits agricoles',true),
	('salaire-remuneration',e'salaire / rémunération',true),
	('moyen-orient','Moyen-Orient',true),
	('industrie','industrie',true),
	('television',e'télévision',true),
	('public-prive',e'public / privé',true),
	('entrepreneuriat','entrepreneuriat',true),
	('fonctionnaires','fonctionnaires',true),
	('distribution','distribution',true),
	('information-labels','information / labels',true),
	('associations','associations',true),
	('gafa','GAFA',true),
	('agriculture-raisonnee',e'agriculture raisonnée',true),
	('revenu-universel-de-base','revenu universel de base',true),
	('prevention-sante',e'prévention & santé ',true),
	('reglementation',e'réglementation',true),
	('drogues-et-trafics','drogues et trafics',true),
	('professionalisation','professionalisation',true),
	('exclusion-sociale','exclusion sociale',true),
	('cuisine','cuisine',true),
	('entreprises-collectivites',e'entreprises & collectivités',true),
	('information-transparence','information & transparence',true),
	('etudiants',e'étudiants',true),
	('consommation-responsable','consommation responsable',true),
	('terrorisme','terrorisme',true),
	('statut-de-l-elu',e'statut de l\'élu',true),
	('effectifs','effectifs',true),
	('acces-au-logement',e'accès au logement',true),
	('solidarite',e'solidarité',true),
	('action-publique','action publique',true),
	('theatre',e'théâtre',true),
	('information','information',true),
	('handicap','handicap ',true),
	('minorites',e'minorités',true),
	('loyers','loyers',true),
	('programmes-matieres',e'programmes & matières',true),
	('allocations-aides','allocations & aides',true),
	('cursus','cursus',true),
	('politique-de-l-education',e'politique de l\'éducation',true),
	('methodes-d-apprentissage',e'méthodes d\'apprentissage',true),
	('pouvoir-d-achat', e'pouvoir d\'achat',true),
	('quartiers-sensibles','quartiers sensibles',true),
	('renseignement','renseignement',true),
	('cooperation-internationale',e'coopération internationale',true),
	('circuits-courts','circuits-courts',true),
	('armee',e'armée',true),
	('decrochage-soutien',e'décrochage / soutien',true),
	('financement','financement',true),
	('union-europeenne',e'Union européenne',true),
	('redistribution','redistribution',true),
	('transports','transports',true),
	('institutions','institutions',true),
	('vegetarien',e'végétarien',true),
	('travailleurs-independants',e'travailleurs indépendants',true)
	ON CONFLICT (slug) DO NOTHING;
%
INSERT INTO theme
    (uuid, actions_count, proposals_count, country, color, gradient_from, gradient_to, tags_ids)
    VALUES
    ('4f79b301-6735-4e88-ad36-69320d69cf2e', 0, 0, 'FR', '#E91E63', '#E81E61', '#7F2FD0', 'collectivites-locales,representativite,elections-vote,exemplarite,laicite,information-transparence,nouvelles-technologies,egalite-hommes-femmes,renouvellement,statut-de-l-elu,participation-citoyenne,institutions,education-civique'),
    ('036f24fa-dc32-4808-bca9-7ccec1665585', 0, 0, 'FR', '#8BC34A', '#83BB1A', '#1FC8F1', 'urbanisme-habitat,recherche,action-publique,energies-traditionnelles,fiscalite-subventions,transports-en-commun,bio,agriculture,nucleaire,entreprises-collectivites,recyclage-zero-dechets,circuits-courts,consommation-responsable,nouvelles-technologies,international,sensibilisation-education,transports,energies-renouvelables,ogms-pesticides'),
    ('b3b37b86-1198-4e06-94ff-cb04f57c6b67', 0, 0, 'FR', '#26A69A', '#26A69A', '#1CEBA0', 'addictions,recherche,fiscalite,acces-aux-soins,cuisine,malbouffe,industrie-agroalimentaire,nouvelles-technologies,sensibilisation,habitudes-alimentaires,publicite,reseaux-de-distribution,systeme-de-sante,produits-chimiques-ogms,vegetarien,etiquetage-label'),
    ('706b277c-3db8-403c-b3c9-7f69939181df', 0, 0, 'FR', '#9173C5', '#9173C6', '#EE98D7', 'ecole-primaire,enseignement-secondaire,enseignement-superieur,carriere,orientation,soutien-scolaire,pedagogie,effectifs,infrastructures,loisirs,epanouissement,matieres,decrochage,examens,rythmes-scolaires,handicap,discipline,violence,parents,enfants,enseignants,harcelement,stages,apprentissage,devoirs,uniforme,langues,numerique,restauration,ressources-pedagogiques,education-civique,sante,diplomes'),
    ('bae38574-56a7-4b91-8540-1428e737881a', 0, 0, 'FR', '#0E75C6', '#0E75C6', '#41CED6', 'terrorisme,immigration,aide-humanitaire,diplomatie,culture-langues,reglementation,cooperation-internationale,institutions,monnaie,ecologie,economie,moyen-orient,conflits,union-europeenne,aide-au-developpement,afrique'),
    ('d6f4d333-2dc8-493b-aeac-e4a8f9a0f9af', 0, 0, 'FR', '#B7588B', '#FF9047', '#B7588B', 'exclusion-sociale,renseignement,police,terrorisme,port-d-armes,medias,cooperation-internationale,drogues-et-trafics,systeme-judiciaire,dispositifs-de-securite,peines,prison,deradicalisation,securite-routiere,quartiers-sensibles,sensibilisation-prevention,armee'),
    ('980e6688-c169-4810-98d8-2b68043559bd', 0, 0, 'FR', '#FF9800', '#FF9800', '#FFEA9F', 'acces-au-logement,solidarite,surpopulation,urbanisme,logements-vacants,loyers,fiscalite-taxes,etudiants,ecologie,sdf,insalubrite,nouvelles-technologies,aides,logements-sociaux'),
    ('f1990dfb-7b92-47cd-8015-e7ca0f961006', 0, 0, 'FR', '#F9E42A', '#ECD400', '#FF9FFD', 'associations,solidarite,lien-social,contraception-ivg,information,discriminations,service-civique,laicite,egalite,reinsertion,minorites,aides,jeunesse,redistribution,famille'),
    ('969118ae-949f-4a4a-af33-ec1bcc107450', 0, 0, 'FR', '#2E7D32', '#2E7D32', '#8FCF4B', 'information-labels,vie-rurale,action-publique,agriculture-raisonnee,bio,distribution,nouvelles-technologies,permaculture,produits-chimiques-ogms,produits-agricoles,formation,union-europeenne,local,consommation-raisonnee'),
    ('8a4a9d0a-92b8-48e4-946d-3e6986296ec7', 0, 0, 'FR', '#311B92', '#311B92', '#54A0E3', 'territoire,gouvernance,institutions,monnaie,richesses,flux-migratoires,accords,derives,culture,fiscalite,cooperation-internationale,sortie-de-l-europe,commerce-international,langue,etats-unis,populations,conflits,protectionnisme,defense'),
    ('5f6bc4e8-e353-4afa-8001-24232b2f8816', 0, 0, 'FR', '#F5515F', '#F5515F', '#9F031B', ''),
    ('6fb8d14a-388c-4713-a8a9-52ef89de3888', 0, 0, 'FR', '#03A9F4', '#4FC8FF', '#FFDC00', 'television,fiscalite,musees,theatre,fracture-numerique,reseaux-sociaux,deconnexion,gafa,patrimoine,nouvelles-technologies,acces-a-la-culture,action-publique,formation,musique,financement,startups,street-art,sensibilisation')
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
    ('5f6bc4e8-e353-4afa-8001-24232b2f8816', 'fr', 'transports-deplacement', 'transports / déplacement'),
    ('6fb8d14a-388c-4713-a8a9-52ef89de3888', 'fr', 'numerique-culture', 'numérique / culture')
    ON CONFLICT (theme_uuid, language) DO NOTHING;
%
INSERT INTO make_user
VALUES
('11111111-1111-1111-1111-111111111111','2017-09-15 08:43:30','2017-09-15 08:43:30','#adminemail#','#adminfirstname#',NULL,NULL,'#adminencryptedpassword#',true,false,'2017-09-15 08:43:30',NULL,'2017-10-15 08:43:30',NULL,NULL,'ROLE_ADMIN,ROLE_CITIZEN',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,false)
ON CONFLICT (uuid) DO NOTHING
%