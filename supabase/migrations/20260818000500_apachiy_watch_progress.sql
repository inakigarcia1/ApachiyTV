-- =============================================================================
-- 20260818000500_apachiy_watch_progress.sql
-- Playback resume state per (user, profile, content).
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.watch_progress (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL,
    content_id      TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    video_id        TEXT NOT NULL DEFAULT '',
    season          INTEGER,
    episode         INTEGER,
    position        BIGINT NOT NULL DEFAULT 0,
    duration        BIGINT NOT NULL DEFAULT 0,
    last_watched    BIGINT NOT NULL DEFAULT 0,
    progress_key    TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (progress_key)
);
CREATE INDEX IF NOT EXISTS idx_watch_progress_user_profile ON public.watch_progress(user_id, profile_id);
CREATE INDEX IF NOT EXISTS idx_watch_progress_content ON public.watch_progress(content_id, content_type);

DROP TRIGGER IF EXISTS trg_watch_progress_touch ON public.watch_progress;
CREATE TRIGGER trg_watch_progress_touch
    BEFORE UPDATE ON public.watch_progress
    FOR EACH ROW EXECUTE FUNCTION public._touch_updated_at();

-- Append-only event log used by sync_pull_watch_progress_delta
CREATE TABLE IF NOT EXISTS public.watch_progress_events (
    event_id        BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL,
    operation       TEXT NOT NULL,         -- 'upsert' | 'delete'
    progress_key    TEXT NOT NULL,
    content_id      TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    video_id        TEXT NOT NULL DEFAULT '',
    season          INTEGER,
    episode         INTEGER,
    position        BIGINT NOT NULL DEFAULT 0,
    duration        BIGINT NOT NULL DEFAULT 0,
    last_watched    BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_watch_progress_events_user ON public.watch_progress_events(user_id, profile_id, event_id);
CREATE INDEX IF NOT EXISTS idx_watch_progress_events_key ON public.watch_progress_events(progress_key);