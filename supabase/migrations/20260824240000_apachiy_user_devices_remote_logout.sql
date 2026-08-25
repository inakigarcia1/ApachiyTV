-- =============================================================================
-- 20260824240000_apachiy_user_devices_remote_logout.sql
-- Remote logout: auth_session_id, Realtime publication, update-only RPC.
-- =============================================================================

ALTER TABLE public.user_devices
    ADD COLUMN IF NOT EXISTS auth_session_id UUID;

ALTER TABLE public.user_devices REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime'
    ) THEN
        CREATE PUBLICATION supabase_realtime;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'user_devices'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.user_devices;
    END IF;
END $$;

-- Heartbeat RPC: only refresh existing rows (registration inserts via Apachiy API).
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
    v_last TIMESTAMPTZ := NOW();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;

    SELECT id, revoked_at INTO v_existing_id, v_existing_revoked
        FROM public.user_devices
        WHERE user_id = v_uid AND installation_id = p_installation_id;

    IF v_existing_id IS NULL THEN
        RETURN QUERY SELECT NULL::BIGINT, false, NULL::TIMESTAMPTZ, false;
        RETURN;
    END IF;

    UPDATE public.user_devices
        SET client_name    = COALESCE(p_client_name,    client_name),
            client_version = COALESCE(p_client_version, client_version),
            platform       = COALESCE(p_platform,       platform),
            device_name    = COALESCE(p_device_name,    device_name),
            last_seen_at   = NOW(),
            last_login_at  = v_last
        WHERE id = v_existing_id;

    RETURN QUERY SELECT v_existing_id, false, v_last, v_existing_revoked IS NOT NULL;
END;
$$;

GRANT EXECUTE ON FUNCTION public.register_current_device(TEXT, TEXT, TEXT, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.close_device_session(p_device_id BIGINT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM public.user_devices WHERE id = p_device_id;
END;
$$;

REVOKE ALL ON FUNCTION public.close_device_session(BIGINT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.close_device_session(BIGINT) TO service_role;
