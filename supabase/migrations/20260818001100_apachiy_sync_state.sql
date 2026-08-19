-- =============================================================================
-- 20260818001100_apachiy_sync_state.sql
-- Per-(user, profile) delta-sync cursor pointers. The TV app reads these to
-- know "what's the last event_id I've already pulled for this resource?".
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.sync_state (
    user_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id          BIGINT NOT NULL,
    resource            TEXT NOT NULL,                 -- 'watch_progress' | 'watched_items' | ...
    last_event_id       BIGINT NOT NULL DEFAULT 0,
    last_successful_push_ms BIGINT NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, profile_id, resource)
);