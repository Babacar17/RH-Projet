-- ═══════════════════════════════════════════════════════════════
-- Migration : hiérarchie flexible par institution
-- À exécuter APRÈS le premier démarrage de l'application
-- (Hibernate hbm2ddl=update crée les colonnes institution_id
--  et assouplit les contraintes NOT NULL au démarrage).
--
--   psql -U postgres -d soutenance_db -f migration_hierarchie_flexible.sql
-- ═══════════════════════════════════════════════════════════════

-- 1. Assouplir les contraintes NOT NULL héritées de l'ancien schéma
--    (hbm2ddl=update ne retire jamais un NOT NULL existant)
ALTER TABLE divisions ALTER COLUMN direction_id DROP NOT NULL;
ALTER TABLE services  ALTER COLUMN division_id  DROP NOT NULL;
ALTER TABLE bureaux   ALTER COLUMN service_id   DROP NOT NULL;

-- 2. Créer les colonnes institution_id si Hibernate ne l'a pas déjà fait
ALTER TABLE divisions ADD COLUMN IF NOT EXISTS institution_id BIGINT REFERENCES institutions(id);
ALTER TABLE services  ADD COLUMN IF NOT EXISTS institution_id BIGINT REFERENCES institutions(id);
ALTER TABLE bureaux   ADD COLUMN IF NOT EXISTS institution_id BIGINT REFERENCES institutions(id);

-- 3. Backfill : remonter l'institution depuis la chaîne des parents
UPDATE divisions dv
SET institution_id = d.institution_id
FROM directions d
WHERE dv.direction_id = d.id AND dv.institution_id IS NULL;

UPDATE services s
SET institution_id = dv.institution_id
FROM divisions dv
WHERE s.division_id = dv.id AND s.institution_id IS NULL;

UPDATE bureaux b
SET institution_id = s.institution_id
FROM services s
WHERE b.service_id = s.id AND b.institution_id IS NULL;

-- 4. Contrôle : aucune ligne ne doit rester sans institution
SELECT 'divisions sans institution' AS controle, count(*) FROM divisions WHERE institution_id IS NULL
UNION ALL
SELECT 'services sans institution', count(*) FROM services WHERE institution_id IS NULL
UNION ALL
SELECT 'bureaux sans institution', count(*) FROM bureaux WHERE institution_id IS NULL;
