-- =============================================================================
-- Apachiy TV — Postgres bootstrap (runs once on first container start)
-- =============================================================================
-- This file runs in alphabetical order BEFORE migrations. It must be idempotent.
-- =============================================================================

-- Required extensions for Supabase + Apachiy schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"      WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "pgcrypto"       WITH SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "pgjwt"          WITH SCHEMA extensions;

-- Roles expected by Supabase Auth + PostgREST + Storage
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

GRANT anon             TO authenticator;
GRANT authenticated    TO authenticator;
GRANT service_role     TO authenticator WITH ADMIN OPTION;

GRANT USAGE ON SCHEMA public                    TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA extensions                TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA storage                   TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA auth                      TO anon, authenticated, service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO anon, authenticated, service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO anon, authenticated, service_role;

-- Supabase auth schema stub (GoTrue creates its tables on first start; this
-- keeps the FK from `auth.users(id)` valid during initial migration runs).
CREATE SCHEMA IF NOT EXISTS auth;
CREATE TABLE IF NOT EXISTS auth.users (
  id UUID PRIMARY KEY REFERENCES auth.users NULL,
  email TEXT,
  raw_user_meta_data JSONB,
  raw_app_meta_data JSONB
);

-- Supabase storage schema stub (Storage API creates tables on first start).
CREATE SCHEMA IF NOT EXISTS storage;

-- pgcrypto for gen_random_uuid(); also keep uuid-ossp available
CREATE EXTENSION IF NOT EXISTS "pgcrypto";