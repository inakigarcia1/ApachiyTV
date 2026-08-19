-- =============================================================================
-- 20260818000000_apachiy_profiles.sql
-- Per-user TV profiles. One Supabase auth user can have many profiles.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_index       INTEGER NOT NULL,
    name                TEXT NOT NULL DEFAULT '',
    avatar_color_hex    TEXT NOT NULL DEFAULT '#1E88E5',
    uses_primary_addons BOOLEAN NOT NULL DEFAULT FALSE,
    uses_primary_plugins BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_id           TEXT,
    avatar_url          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, profile_index)
);
CREATE INDEX IF NOT EXISTS idx_profiles_user_id ON public.profiles(user_id);

-- PIN lock state per profile (1 row per profile). Created lazily.
CREATE TABLE IF NOT EXISTS public.profile_locks (
    profile_id          BIGINT NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    pin_enabled         BOOLEAN NOT NULL DEFAULT FALSE,
    pin_hash            TEXT,                       -- bcrypt-ish; not checked here
    pin_locked_until    TIMESTAMPTZ,
    failed_attempts     INTEGER NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (profile_id)
);

-- Bumped automatically by trigger
CREATE OR REPLACE FUNCTION public._touch_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_profiles_touch ON public.profiles;
CREATE TRIGGER trg_profiles_touch
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();