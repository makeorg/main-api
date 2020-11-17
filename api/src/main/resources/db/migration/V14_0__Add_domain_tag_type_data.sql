BEGIN;

INSERT INTO tag_type(id, label, display) VALUES
('1edeb3ff-a6fb-4002-99f8-622b8d8655b2', 'Domain', 'DISPLAYED') ON CONFLICT (id) DO NOTHING;

UPDATE tag_type SET weight_type = 60 WHERE id = '1edeb3ff-a6fb-4002-99f8-622b8d8655b2';
UPDATE tag_type SET display = 'DISPLAYED' WHERE id = '1edeb3ff-a6fb-4002-99f8-622b8d8655b2';

COMMIT;
