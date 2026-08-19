-- =============================================================================
-- 20260818000700_apachiy_profile_settings.sql
-- Per-profile key-value bag of synced UI settings (theme, layout, etc).
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.profile_settings_blob (
    profile_id      BIGINT NOT NULL,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    settings_json   JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, profile_id)
);

DROP TRIGGER IF EXISTS trg_profile_settings_touch ON public.profile_settings_blob;
CREATE TRIGGER trg_profile_settings_touch
    BEFORE UPDATE ON public.profile_settings_blob
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();