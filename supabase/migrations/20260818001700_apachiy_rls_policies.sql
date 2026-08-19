-- =============================================================================
-- 20260818001700_apachiy_rls_policies.sql
-- ENABLE RLS on every user-data table; a single per-user policy per table.
-- Storage bucket policies are in 01500_apachiy_avatars_bucket.sql.
-- =============================================================================

-- Helper: standard "owner can do anything" policy
CREATE OR REPLACE FUNCTION public._apachiy_enable_owner_rls(tbl TEXT) RETURNS VOID AS $$
BEGIN
    EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', tbl || '_owner_all', tbl);
    EXECUTE format(
        'CREATE POLICY %I ON public.%I FOR ALL '
        'USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid())',
        tbl || '_owner_all', tbl
    );
END;
$$ LANGUAGE plpgsql;

-- Tables with user_id column
SELECT public._apachiy_enable_owner_rls('profiles');
SELECT public._apachiy_enable_owner_rls('addons');
SELECT public._apachiy_enable_owner_rls('plugins');
SELECT public._apachiy_enable_owner_rls('collections');
SELECT public._apachiy_enable_owner_rls('library');
SELECT public._apachiy_enable_owner_rls('watch_progress');
SELECT public._apachiy_enable_owner_rls('watch_progress_events');
SELECT public._apachiy_enable_owner_rls('watched_items');
SELECT public._apachiy_enable_owner_rls('watched_items_events');
SELECT public._apachiy_enable_owner_rls('profile_settings_blob');
SELECT public._apachiy_enable_owner_rls('home_catalog_settings_blob');
SELECT public._apachiy_enable_owner_rls('provider_credentials');
SELECT public._apachiy_enable_owner_rls('user_devices');
SELECT public._apachiy_enable_owner_rls('sync_codes');
SELECT public._apachiy_enable_owner_rls('sync_state');
SELECT public._apachiy_enable_owner_rls('linked_devices');
SELECT public._apachiy_enable_owner_rls('tv_login_sessions');

-- profile_locks: owner via join on profiles
ALTER TABLE public.profile_locks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS profile_locks_owner_all ON public.profile_locks;
CREATE POLICY profile_locks_owner_all
    ON public.profile_locks FOR ALL
    USING (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = profile_locks.profile_id AND p.user_id = auth.uid()))
    WITH CHECK (EXISTS (SELECT 1 FROM public.profiles p WHERE p.id = profile_locks.profile_id AND p.user_id = auth.uid()));

-- avatar_catalog: world-readable; only service_role writes
ALTER TABLE public.avatar_catalog ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS avatar_catalog_read ON public.avatar_catalog;
CREATE POLICY avatar_catalog_read ON public.avatar_catalog FOR SELECT USING (true);
DROP POLICY IF EXISTS avatar_catalog_service_write ON public.avatar_catalog;
CREATE POLICY avatar_catalog_service_write ON public.avatar_catalog FOR ALL
    USING (auth.role() = 'service_role') WITH CHECK (auth.role() = 'service_role');

-- Cleanup function
DROP FUNCTION public._apachiy_enable_owner_rls(TEXT);

-- Migration tracking (used by scripts/bootstrap-apachiy.sh)
CREATE TABLE IF NOT EXISTS public._apachiy_migrations (
    name        TEXT PRIMARY KEY,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);