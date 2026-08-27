-- =============================================================================
-- Apachiy TV — Postgres bootstrap (runs once on first container start)
-- =============================================================================
-- This file runs in alphabetical order BEFORE Apachiy schema migrations.
-- Apachiy migrations are applied by scripts/bootstrap-apachiy.sh after the
-- stack is up and GoTrue has created auth.users.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS storage;
CREATE SCHEMA IF NOT EXISTS _realtime;
CREATE SCHEMA IF NOT EXISTS realtime AUTHORIZATION supabase_admin;

-- Realtime migrations GRANT to role "postgres" (expected by supabase/realtime image).
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres') THEN
        CREATE ROLE postgres LOGIN SUPERUSER;
    END IF;
END $$;

-- Required extensions for Supabase + Apachiy schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"      WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "pgcrypto"       WITH SCHEMA extensions;
-- pgjwt is bundled in supabase/postgres but not in postgres:15-alpine; JWT helpers
-- are not required for GoTrue/PostgREST in this self-hosted slice.

-- Roles expected by Supabase Auth + PostgREST + Storage
-- Must exist before any GRANT ... TO anon/authenticated/service_role.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    CREATE ROLE anon NOLOGIN NOINHERIT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    CREATE ROLE authenticated NOLOGIN NOINHERIT;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
    CREATE ROLE service_role NOLOGIN NOINHERIT BYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticator') THEN
    CREATE ROLE authenticator LOGIN NOINHERIT PASSWORD 'supabase_authenticator_password';
  END IF;
END $$;

GRANT USAGE ON SCHEMA realtime TO supabase_admin, anon, authenticated, service_role;
GRANT ALL ON SCHEMA realtime TO supabase_admin;

GRANT anon             TO authenticator;
GRANT authenticated    TO authenticator;
GRANT service_role     TO authenticator WITH ADMIN OPTION;

GRANT USAGE ON SCHEMA public                    TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA extensions                TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA storage                   TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA auth                      TO anon, authenticated, service_role;

-- GoTrue queries auth.users / auth.identities without schema qualifiers; the DB
-- role must include auth in search_path (see gotrue error "relation identities does not exist").
-- Migration versions must live in auth.schema_migrations when search_path starts with auth.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'schema_migrations'
  ) THEN
    INSERT INTO auth.schema_migrations (version)
    SELECT version
    FROM public.schema_migrations
    WHERE version NOT IN (SELECT version FROM auth.schema_migrations);
  END IF;
END $$;

ALTER ROLE supabase_admin SET search_path TO auth, public, extensions;

-- Realtime Postgres Changes (self-hosted slice; requires wal2json in Postgres image).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime'
    ) THEN
        CREATE PUBLICATION supabase_realtime;
    END IF;
END $$;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated, service_role;
