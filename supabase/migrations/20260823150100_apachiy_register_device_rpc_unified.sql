-- =============================================================================
-- 20260823150100_apachiy_register_device_rpc_unified.sql
-- Upsert into public.user_devices (DDL in 20260823150000_apachiy_user_devices.sql).
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
