-- Fix GoTrue signup when auth tables live in the auth schema but the DB role's
-- search_path does not include auth (ERROR: relation "identities" does not exist).
--
-- Self-hosted stacks may have recorded GoTrue migrations in public.schema_migrations
-- while auth.schema_migrations is stale; sync before setting search_path so startup
-- does not re-run incompatible backfill migrations.
INSERT INTO auth.schema_migrations (version)
SELECT version
FROM public.schema_migrations
WHERE version NOT IN (SELECT version FROM auth.schema_migrations);

ALTER ROLE supabase_admin SET search_path TO auth, public, extensions;
