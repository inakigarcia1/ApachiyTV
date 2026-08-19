-- =============================================================================
-- 20260818001400_apachiy_register_device_rpc.sql
-- The RPC the TV app calls from DeviceSessionRegistration.kt after login.
-- This persists the device against auth.uid() in this Supabase DB, parallel
-- to (but independent of) the Apachiy .NET API's /v1/devices/register.
-- =============================================================================
CREATE OR REPLACE FUNCTION public.register_current_device(
    p_installation_id TEXT,
    p_client_name     TEXT,
    p_client_version  TEXT,
    p_platform        TEXT,
    p_device_name     TEXT
) RETURNS TABLE (
    device_id        BIGINT,
    created          BOOLEAN,
    last_login_at    TIMESTAMPTZ,
    revoked          BOOLEAN
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_existing_id BIGINT;
    v_existing_revoked TIMESTAMPTZ;
    v_created BOOLEAN := false;
    v_last TIMESTAMPTZ := NOW();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;
    -- self-hosted mirror of user_devices (the canonical record is in the
    -- Apachiy .NET API; this lets the TV app keep working even if the .NET
    -- API is down for maintenance).
    CREATE TABLE IF NOT EXISTS public.user_devices (
        id              BIGSERIAL PRIMARY KEY,
        user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
        installation_id TEXT NOT NULL,
        client_name     TEXT,
        client_version  TEXT,
        platform        TEXT,
        device_name     TEXT,
        first_seen_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        last_login_at   TIMESTAMPTZ,
        revoked_at      TIMESTAMPTZ,
        UNIQUE (user_id, installation_id)
    );
    SELECT id, revoked_at INTO v_existing_id, v_existing_revoked
        FROM public.user_devices
        WHERE user_id = v_uid AND installation_id = p_installation_id;
    IF v_existing_id IS NULL THEN
        INSERT INTO public.user_devices
            (user_id, installation_id, client_name, client_version, platform, device_name, last_login_at)
        VALUES
            (v_uid, p_installation_id, p_client_name, p_client_version, p_platform, p_device_name, v_last)
        RETURNING id INTO v_existing_id;
        v_created := true;
    ELSE
        UPDATE public.user_devices
            SET client_name    = COALESCE(p_client_name,    client_name),
                client_version = COALESCE(p_client_version, client_version),
                platform       = COALESCE(p_platform,       platform),
                device_name    = COALESCE(p_device_name,    device_name),
                last_seen_at   = NOW(),
                last_login_at  = v_last
            WHERE id = v_existing_id;
    END IF;
    RETURN QUERY SELECT v_existing_id, v_created, v_last, v_existing_revoked IS NOT NULL;
END;
$$;
GRANT EXECUTE ON FUNCTION public.register_current_device(TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;