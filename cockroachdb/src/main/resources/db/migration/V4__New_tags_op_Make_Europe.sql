BEGIN;

INSERT INTO tag (slug, label, enabled, created_at, updated_at) VALUES
	('european-commission', 'european commission', true, NOW(), NOW()),
	('social', 'social', true, NOW(), NOW()),
	('taxes', 'taxes', true, NOW(), NOW()),
	('environment', 'environment', true, NOW(), NOW()),
	('minimum-wage', 'minimum wage', true, NOW(), NOW()),
	('finance', 'finance', true, NOW(), NOW()),
	('budget', 'budget', true, NOW(), NOW()),
	('students', 'students', true, NOW(), NOW()),
	('erasmus', 'erasmus', true, NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;

COMMIT;
