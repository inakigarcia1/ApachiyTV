-- =============================================================================
-- 20260818001300_apachiy_linked_devices.sql
-- Devices linked to a user (via the cross-device sync code handshake).
-- Distinct from `user_devices` in the Apachiy .NET API: this table is part
-- of the TV app's own Supabase and tracks "I synced my library to that phone".
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.linked_devices (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_user_id  UUID NOT NULL,
    device_name     TEXT,
    linked_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    UNIQUE (owner_id, device_user_id)
);
CREATE INDEX IF NOT EXISTS idx_linked_devices_owner ON public.linked_devices(owner_id);

-- Sync code issuance
CREATE TABLE IF NOT EXISTS public.sync_codes (
    code            TEXT PRIMARY KEY,
    pin_hash        TEXT NOT NULL,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consumed_at     TIMESTAMPTZ,
    consumed_by     UUID REFERENCES auth.users(id)
);

CREATE OR REPLACE FUNCTION public.generate_sync_code(p_pin TEXT)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_code TEXT;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    v_code := upper(substring(md5(random()::text || clock_timestamp()::text) from 1 for 6));
    INSERT INTO public.sync_codes(code, pin_hash, user_id) VALUES (v_code, crypt(p_pin, gen_salt('bf')), v_uid);
    RETURN v_code;
END;
$$;
GRANT EXECUTE ON FUNCTION public.generate_sync_code(TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_sync_code(p_pin TEXT)
RETURNS TABLE(code TEXT, owner_id UUID, device_name TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_row RECORD;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    SELECT sc.code, sc.user_id AS owner_id INTO v_row
        FROM public.sync_codes sc
        WHERE sc.user_id = v_uid
          AND sc.consumed_at IS NULL
          AND sc.created_at > NOW() - INTERVAL '1 hour'
        ORDER BY sc.created_at DESC LIMIT 1;
    IF NOT FOUND THEN RETURN; END IF;
    IF v_row.code IS NULL THEN RETURN; END IF;
    RETURN QUERY SELECT v_row.code, v_row.owner_id, NULL::TEXT;
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_sync_code(TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.get_sync_owner()
RETURNS UUID
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$ SELECT auth.uid(); $$;
GRANT EXECUTE ON FUNCTION public.get_sync_owner() TO authenticated;

CREATE OR REPLACE FUNCTION public.get_sync_overview()
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
BEGIN
    RETURN jsonb_build_object(
        'linked_devices', (SELECT count(*) FROM public.linked_devices WHERE owner_id = v_uid AND revoked_at IS NULL),
        'pending_codes',  (SELECT count(*) FROM public.sync_codes    WHERE user_id    = v_uid AND consumed_at IS NULL)
    );
END;
$$;
GRANT EXECUTE ON FUNCTION public.get_sync_overview() TO authenticated;

CREATE OR REPLACE FUNCTION public.claim_sync_code(
    p_code        TEXT,
    p_pin         TEXT,
    p_device_name TEXT
) RETURNS TABLE(result_owner_id UUID, success BOOLEAN, message TEXT)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_row RECORD;
BEGIN
    IF v_uid IS NULL THEN
        RETURN QUERY SELECT NULL::UUID, FALSE, 'not authenticated';
        RETURN;
    END IF;
    SELECT sc.user_id AS owner_id, sc.code INTO v_row
        FROM public.sync_codes sc
        WHERE sc.code = upper(p_code)
          AND sc.consumed_at IS NULL
          AND sc.created_at > NOW() - INTERVAL '1 hour';
    IF NOT FOUND THEN
        RETURN QUERY SELECT NULL::UUID, FALSE, 'invalid or expired code';
        RETURN;
    END IF;
    IF v_row.owner_id = v_uid THEN
        RETURN QUERY SELECT NULL::UUID, FALSE, 'cannot claim your own code';
        RETURN;
    END IF;
    INSERT INTO public.linked_devices(owner_id, device_user_id, device_name)
        VALUES (v_row.owner_id, v_uid, p_device_name)
        ON CONFLICT (owner_id, device_user_id) DO UPDATE SET device_name = EXCLUDED.device_name, linked_at = NOW();
    UPDATE public.sync_codes SET consumed_at = NOW(), consumed_by = v_uid WHERE code = v_row.code;
    RETURN QUERY SELECT v_row.owner_id, TRUE, 'linked';
END;
$$;
GRANT EXECUTE ON FUNCTION public.claim_sync_code(TEXT, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.unlink_device(p_device_user_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    UPDATE public.linked_devices
        SET revoked_at = NOW()
        WHERE owner_id = v_uid AND device_user_id = p_device_user_id;
    RETURN FOUND;
END;
$$;
GRANT EXECUTE ON FUNCTION public.unlink_device(UUID) TO authenticated;