-- =============================================================================
-- 20260818001000_apachiy_provider_creds.sql
-- Encrypted credentials for external providers (Trakt, Simkl, Real-Debrid, ...).
-- The credential_json blob is opaque; encryption-at-rest is enforced at the
-- Supabase Storage / Postgres volume level.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.provider_credentials (
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider        TEXT NOT NULL,                 -- 'trakt' | 'simkl' | 'real_debrid' | ...
    credential_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, provider)
);

DROP TRIGGER IF EXISTS trg_provider_credentials_touch ON public.provider_credentials;
CREATE TRIGGER trg_provider_credentials_touch
    BEFORE UPDATE ON public.provider_credentials
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();