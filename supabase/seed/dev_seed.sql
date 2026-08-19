-- =============================================================================
-- Apachiy TV — dev seed (safe; no production data)
-- Apply only with:  psql ... -v dev_seed=true -f dev_seed.sql
-- =============================================================================
\set ON_ERROR_STOP on

-- Only run when the caller passes -v dev_seed=true
SELECT CASE WHEN current_setting('dev_seed', true) = 'true' THEN
    -- Insert placeholder avatar catalog entries (idempotent)
    INSERT INTO public.avatar_catalog(id, display_name, storage_path, category, sort_order, bg_color) VALUES
        ('apachiy-default-1', 'Apachiy Blue', 'avatars/default-blue.svg',   'default', 1, '#1E88E5'),
        ('apachiy-default-2', 'Apachiy Orange', 'avatars/default-orange.svg','default', 2, '#FF9800'),
        ('apachiy-default-3', 'Apachiy Green',  'avatars/default-green.svg', 'default', 3, '#43A047'),
        ('apachiy-default-4', 'Apachiy Red',    'avatars/default-red.svg',   'default', 4, '#E53935'),
        ('apachiy-default-5', 'Apachiy Purple', 'avatars/default-purple.svg','default', 5, '#8E24AA'),
        ('apachiy-default-6', 'Apachiy Cyan',   'avatars/default-cyan.svg',  'default', 6, '#00ACC1')
    ON CONFLICT (id) DO NOTHING;
    -- Return a friendly marker
    SELECT 'apachiy dev seed applied';
ELSE
    SELECT 'dev_seed not enabled; pass -v dev_seed=true to apply';
END AS result;