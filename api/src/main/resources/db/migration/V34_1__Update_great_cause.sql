UPDATE operation SET operation_kind = 'PUBLIC_CONSULTATION';
UPDATE operation SET operation_kind = 'GREAT_CAUSE' WHERE slug IN
    ('mieuxmanger','aines','vff','chance-aux-jeunes','mieux-vivre-ensemble','culture');
UPDATE operation SET operation_kind = 'PRIVATE_CONSULTATION' WHERE slug = 'ditp';

COMMIT;