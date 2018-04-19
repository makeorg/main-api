ALTER TABLE tag
    ADD CONSTRAINT tag_type_fk FOREIGN KEY (tag_type_id) REFERENCES tag_type (id) ON DELETE RESTRICT,
    ADD CONSTRAINT operation_fk FOREIGN KEY (operation_id) REFERENCES operation (uuid) ON DELETE RESTRICT,
    ADD CONSTRAINT theme_fk FOREIGN KEY (theme_id) REFERENCES theme(uuid) ON DELETE RESTRICT;

INSERT INTO tag_type(id, label, display) VALUES
('5e539923-c265-45d2-9d0b-77f29c8b0a06', 'Moment', 'HIDDEN'),
('c0d8d858-8b04-4dd9-add6-fa65443b622b', 'Stake', 'DISPLAYED'),
('cc6a16a5-cfa7-495b-a235-08affb3551af', 'Solution type', 'DISPLAYED'),
('982e6860-eb66-407e-bafb-461c2d927478', 'Actor', 'HIDDEN'),
('226070ac-51b0-4e92-883a-f0a24d5b8525', 'Target', 'HIDDEN'),
('8405aba4-4192-41d2-9a0d-b5aa6cb98d37', 'Legacy', 'DISPLAYED');

UPDATE tag SET tag_type_id='8405aba4-4192-41d2-9a0d-b5aa6cb98d37' WHERE 1=1;

COMMIT;