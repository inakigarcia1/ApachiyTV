-- =============================================================================
-- 20260825000000_apachiy_realtime_schema.sql
-- Realtime v2 expects a dedicated `realtime` schema for tenant migrations.
-- Without it, WebSocket postgres_changes subscriptions hang/fail silently.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS realtime AUTHORIZATION supabase_admin;

GRANT USAGE ON SCHEMA realtime TO supabase_admin, anon, authenticated, service_role;
GRANT ALL ON SCHEMA realtime TO supabase_admin;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
        CREATE ROLE postgres LOGIN SUPERUSER;
    END IF;
END $$;
