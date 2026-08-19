-- =============================================================================
-- 20260818000100_apachiy_addons.sql
-- Stremio-style addon manifests per user/profile.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.addons (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL DEFAULT 1,
    url             TEXT NOT NULL,
    name            TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, profile_id, url)
);
CREATE INDEX IF NOT EXISTS idx_addons_user_profile ON public.addons(user_id, profile_id);

DROP TRIGGER IF EXISTS trg_addons_touch ON public.addons;
CREATE TRIGGER trg_addons_touch
    BEFORE UPDATE ON public.addons
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();