-- =============================================================================
-- 20260818000400_apachiy_library.sql
-- Per-user library entries (items the user has on their list).
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.library (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL,
    content_id      TEXT NOT NULL,
    content_type    TEXT NOT NULL,         -- 'movie' | 'series'
    kind            TEXT NOT NULL,         -- 'watchlist' | 'favorites' | custom
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_modified   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, profile_id, content_id, content_type, kind)
);
CREATE INDEX IF NOT EXISTS idx_library_user_profile ON public.library(user_id, profile_id);
CREATE INDEX IF NOT EXISTS idx_library_content ON public.library(content_id, content_type);
CREATE INDEX IF NOT EXISTS idx_library_last_modified ON public.library(last_modified);