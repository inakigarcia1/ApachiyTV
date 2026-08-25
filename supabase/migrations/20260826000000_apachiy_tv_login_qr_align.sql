-- =============================================================================
-- 20260826000000_apachiy_tv_login_qr_align.sql
-- Align TV QR login RPCs with the Android client and add landing lookup.
-- =============================================================================

ALTER TABLE public.tv_login_sessions
    ADD COLUMN IF NOT EXISTS device_name TEXT;

-- Drop legacy signatures (Kotlin client uses different parameter names).
DROP FUNCTION IF EXISTS public.start_tv_login_session(TEXT, TEXT, TEXT, INTEGER);
DROP FUNCTION IF EXISTS public.poll_tv_login_session(TEXT);

CREATE OR REPLACE FUNCTION public.start_tv_login_session(
    p_device_nonce      TEXT,
    p_redirect_base_url TEXT,
    p_device_name       TEXT DEFAULT NULL,
    p_ttl_seconds       INTEGER DEFAULT 600
) RETURNS TABLE (
    code                  TEXT,
    web_url               TEXT,
    expires_at            TIMESTAMPTZ,
    poll_interval_seconds INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_code    TEXT;
    v_expires TIMESTAMPTZ := NOW() + (p_ttl_seconds || ' seconds')::interval;
    v_web     TEXT;
BEGIN
    IF p_device_nonce IS NULL OR btrim(p_device_nonce) = '' THEN
        RAISE EXCEPTION 'device_nonce required';
    END IF;
    IF p_redirect_base_url IS NULL OR btrim(p_redirect_base_url) = '' THEN
        RAISE EXCEPTION 'redirect_base_url required';
    END IF;

    v_code := upper(substring(md5(random()::text || clock_timestamp()::text) from 1 for 6));
    v_web  := rtrim(p_redirect_base_url, '/') || '/?code=' || v_code;

    INSERT INTO public.tv_login_sessions(
        code,
        device_nonce,
        device_name,
        web_url,
        expires_at
    )
    VALUES (
        v_code,
        p_device_nonce,
        NULLIF(btrim(p_device_name), ''),
        v_web,
        v_expires
    );

    RETURN QUERY SELECT v_code, v_web, v_expires, 3;
END;
$$;
GRANT EXECUTE ON FUNCTION public.start_tv_login_session(TEXT, TEXT, TEXT, INTEGER) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.poll_tv_login_session(
    p_code         TEXT,
    p_device_nonce TEXT DEFAULT NULL
) RETURNS TABLE (
    status                TEXT,
    expires_at            TIMESTAMPTZ,
    poll_interval_seconds INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_row public.tv_login_sessions%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM public.tv_login_sessions WHERE code = p_code FOR UPDATE;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'not_found'::TEXT, NOW(), 3;
        RETURN;
    END IF;

    IF p_device_nonce IS NOT NULL AND v_row.device_nonce IS DISTINCT FROM p_device_nonce THEN
        RETURN QUERY SELECT 'not_found'::TEXT, NOW(), 3;
        RETURN;
    END IF;

    IF v_row.expires_at < NOW() AND v_row.status NOT IN ('approved', 'consumed') THEN
        UPDATE public.tv_login_sessions SET status = 'expired' WHERE code = p_code;
        RETURN QUERY SELECT 'expired'::TEXT, v_row.expires_at, v_row.poll_interval_seconds;
        RETURN;
    END IF;

    RETURN QUERY SELECT v_row.status, v_row.expires_at, v_row.poll_interval_seconds;
END;
$$;
GRANT EXECUTE ON FUNCTION public.poll_tv_login_session(TEXT, TEXT) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.lookup_tv_login_session(
    p_code TEXT
) RETURNS TABLE (
    status      TEXT,
    device_name TEXT,
    expires_at  TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_row public.tv_login_sessions%ROWTYPE;
BEGIN
    SELECT * INTO v_row FROM public.tv_login_sessions WHERE code = p_code;
    IF NOT FOUND THEN
        RETURN QUERY SELECT 'not_found'::TEXT, NULL::TEXT, NOW();
        RETURN;
    END IF;

    IF v_row.expires_at < NOW() AND v_row.status NOT IN ('approved', 'consumed') THEN
        UPDATE public.tv_login_sessions
            SET status = 'expired'
            WHERE code = p_code AND status = 'pending';
        RETURN QUERY SELECT 'expired'::TEXT, v_row.device_name, v_row.expires_at;
        RETURN;
    END IF;

    RETURN QUERY SELECT v_row.status, v_row.device_name, v_row.expires_at;
END;
$$;
GRANT EXECUTE ON FUNCTION public.lookup_tv_login_session(TEXT) TO anon, authenticated;

-- Atomic consume for the edge exchange (service role bypasses RLS on the table).
CREATE OR REPLACE FUNCTION public.consume_tv_login_session(
    p_code         TEXT,
    p_device_nonce TEXT
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    UPDATE public.tv_login_sessions
        SET status = 'consumed', consumed_at = NOW()
        WHERE code = p_code
          AND device_nonce = p_device_nonce
          AND status = 'approved'
          AND expires_at > NOW()
        RETURNING user_id INTO v_user_id;

    RETURN v_user_id;
END;
$$;
REVOKE ALL ON FUNCTION public.consume_tv_login_session(TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.consume_tv_login_session(TEXT, TEXT) TO service_role;

NOTIFY pgrst, 'reload schema';
