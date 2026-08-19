-- =============================================================================
-- 20260818000900_apachiy_avatar_catalog.sql
-- Catalog of available avatars shown in the profile picker.
-- Storage path is relative to the `avatars` bucket.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.avatar_catalog (
    id              TEXT PRIMARY KEY,
    display_name    TEXT NOT NULL,
    storage_path    TEXT NOT NULL,
    category        TEXT NOT NULL DEFAULT 'default',
    sort_order      INTEGER NOT NULL DEFAULT 0,
    bg_color        TEXT
);

-- Helper: list avatars, ordered.
CREATE OR REPLACE FUNCTION public.get_avatar_catalog()
RETURNS SETOF public.avatar_catalog
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.avatar_catalog ORDER BY sort_order, display_name;
$$;
GRANT EXECUTE ON FUNCTION public.get_avatar_catalog() TO anon, authenticated;