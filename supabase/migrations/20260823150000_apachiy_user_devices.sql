-- =============================================================================
-- 20260823150000_apachiy_user_devices.sql
-- Canonical user_devices table (shared by TV RPC and Apachiy .NET API).
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.user_devices (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    installation_id TEXT NOT NULL,
    client_name     TEXT,
    client_version  TEXT,
    platform        TEXT,
    device_name     TEXT,
    device_model    TEXT,
    os_version      TEXT,
    app_version     TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    revoked_reason  TEXT,
    UNIQUE (user_id, installation_id)
);

CREATE INDEX IF NOT EXISTS idx_user_devices_user_id_last_seen
    ON public.user_devices(user_id, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_devices_active
    ON public.user_devices(user_id)
    WHERE revoked_at IS NULL;

ALTER TABLE public.user_devices ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_devices_owner_all ON public.user_devices;
CREATE POLICY user_devices_owner_all
    ON public.user_devices FOR ALL
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());
