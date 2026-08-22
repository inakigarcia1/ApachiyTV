-- =============================================================================
-- 20260818000600_apachiy_watched_items.sql
-- Per-user watched history. Marks titles as "watched" when finished.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.watched_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL,
    content_id      TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    title           TEXT NOT NULL DEFAULT '',
    season          INTEGER,
    episode         INTEGER,
    watched_at      BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_watched_items_unique
    ON public.watched_items (user_id, profile_id, content_id, content_type, COALESCE(season, -1), COALESCE(episode, -1));
CREATE INDEX IF NOT EXISTS idx_watched_items_user_profile ON public.watched_items(user_id, profile_id);

CREATE TABLE IF NOT EXISTS public.watched_items_events (
    event_id        BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL,
    operation       TEXT NOT NULL,
    content_id      TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    title           TEXT NOT NULL DEFAULT '',
    season          INTEGER,
    episode         INTEGER,
    watched_at      BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_watched_items_events_user ON public.watched_items_events(user_id, profile_id, event_id);