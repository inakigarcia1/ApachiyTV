-- =============================================================================
-- 20260818000300_apachiy_collections.sql
-- User-defined collections of items (e.g. "Best of Nolan", "Watch later").
-- Stored as JSONB blob per profile — the TV app owns the schema.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.collections (
    user_id          UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id       BIGINT NOT NULL,
    collections_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, profile_id)
);

DROP TRIGGER IF EXISTS trg_collections_touch ON public.collections;
CREATE TRIGGER trg_collections_touch
    BEFORE UPDATE ON public.collections
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();