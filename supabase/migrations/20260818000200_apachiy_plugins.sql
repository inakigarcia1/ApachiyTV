-- =============================================================================
-- 20260818000200_apachiy_plugins.sql
-- Lagradost CloudStream-style plugin manifests per user/profile.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.plugins (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL DEFAULT 1,
    url             TEXT NOT NULL,
    name            TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    repo_type       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, profile_id, url)
);
CREATE INDEX IF NOT EXISTS idx_plugins_user_profile ON public.plugins(user_id, profile_id);

DROP TRIGGER IF EXISTS trg_plugins_touch ON public.plugins;
CREATE TRIGGER trg_plugins_touch
    BEFORE UPDATE ON public.plugins
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();