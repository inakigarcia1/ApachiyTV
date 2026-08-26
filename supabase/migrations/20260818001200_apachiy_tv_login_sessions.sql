-- =============================================================================
-- 20260818001200_apachiy_tv_login_sessions.sql
-- TV-login handshake: TV shows a code; user approves on the web; TV polls and
-- gets back an access_token.  Replaces the official Nuvio Edge Function flow.
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.tv_login_sessions (
    code            TEXT PRIMARY KEY,                 -- short human-readable
    device_nonce    TEXT NOT NULL,                    -- unique-per-request
    installation_id TEXT,                             -- optional: link to GUID
    user_id         UUID REFERENCES auth.users(id) ON DELETE CASCADE,  -- set on approval
    status          TEXT NOT NULL DEFAULT 'pending',  -- pending | approved | expired | consumed
    web_url         TEXT NOT NULL,                    -- URL the QR encodes
    poll_interval_seconds INTEGER NOT NULL DEFAULT 3,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at     TIMESTAMPTZ,
    consumed_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_tv_login_status_expires ON public.tv_login_sessions(status, expires_at);

-- Drop first: CREATE OR REPLACE cannot rename input parameters (e.g. after
-- 20260826000000_apachiy_tv_login_qr_align.sql already replaced this RPC).
DROP FUNCTION IF EXISTS public.start_tv_login_session(TEXT, TEXT, TEXT, INTEGER);
DROP FUNCTION IF EXISTS public.poll_tv_login_session(TEXT);
DROP FUNCTION IF EXISTS public.poll_tv_login_session(TEXT, TEXT);
DROP FUNCTION IF EXISTS public.approve_tv_login_session(TEXT);

CREATE OR REPLACE FUNCTION public.start_tv_login_session(
    p_device_nonce    TEXT,
    p_installation_id TEXT,
    p_web_base_url    TEXT,
    p_ttl_seconds     INTEGER DEFAULT 600
) RETURNS TABLE (
    code                TEXT,
    web_url             TEXT,
    expires_at          TIMESTAMPTZ,
    poll_interval_seconds INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_code TEXT;
    v_expires TIMESTAMPTZ := NOW() + (p_ttl_seconds || ' seconds')::interval;
    v_web TEXT;
BEGIN
    v_code := upper(substring(md5(random()::text || clock_timestamp()::text) from 1 for 6));
    v_web  := rtrim(p_web_base_url, '/') || '/?code=' || v_code;
    INSERT INTO public.tv_login_sessions(code, device_nonce, installation_id, web_url, expires_at)
        VALUES (v_code, p_device_nonce, p_installation_id, v_web, v_expires);
    RETURN QUERY SELECT v_code, v_web, v_expires, 3;
END;
$$;
GRANT EXECUTE ON FUNCTION public.start_tv_login_session(TEXT, TEXT, TEXT, INTEGER) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.poll_tv_login_session(
    p_code TEXT
) RETURNS TABLE (
    status         TEXT,
    expires_at     TIMESTAMPTZ,
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
    IF v_row.expires_at < NOW() AND v_row.status NOT IN ('approved', 'consumed') THEN
        UPDATE public.tv_login_sessions SET status = 'expired' WHERE code = p_code;
        RETURN QUERY SELECT 'expired'::TEXT, v_row.expires_at, 3;
        RETURN;
    END IF;
    RETURN QUERY SELECT v_row.status, v_row.expires_at, v_row.poll_interval_seconds;
END;
$$;
GRANT EXECUTE ON FUNCTION public.poll_tv_login_session(TEXT) TO anon, authenticated;

CREATE OR REPLACE FUNCTION public.approve_tv_login_session(
    p_code TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;
    UPDATE public.tv_login_sessions
        SET status = 'approved', user_id = v_uid, approved_at = NOW()
        WHERE code = p_code AND status = 'pending' AND expires_at > NOW();
    RETURN FOUND;
END;
$$;
GRANT EXECUTE ON FUNCTION public.approve_tv_login_session(TEXT) TO authenticated;