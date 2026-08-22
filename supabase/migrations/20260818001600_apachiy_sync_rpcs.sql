-- =============================================================================
-- 20260818001600_apachiy_sync_rpcs.sql
-- Delta-sync RPCs: push (write a batch of events), pull (read delta since
-- cursor).  Implemented in SQL for security and idempotency; SECURITY DEFINER
-- with auth.uid() as the user context.
-- =============================================================================

-- ---------- watch_progress ----------
CREATE OR REPLACE FUNCTION public.sync_get_watch_progress_delta_cursor(p_profile_id BIGINT)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(last_event_id, 0) FROM public.sync_state
        WHERE user_id = auth.uid() AND profile_id = p_profile_id AND resource = 'watch_progress';
$$;
GRANT EXECUTE ON FUNCTION public.sync_get_watch_progress_delta_cursor(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_watch_progress_delta(
    p_profile_id      BIGINT,
    p_since_event_id  BIGINT,
    p_limit           INTEGER DEFAULT 900
) RETURNS SETOF public.watch_progress_events
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.watch_progress_events
        WHERE user_id = auth.uid()
          AND profile_id = p_profile_id
          AND event_id > p_since_event_id
        ORDER BY event_id ASC
        LIMIT LEAST(p_limit, 1000);
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_watch_progress_delta(BIGINT, BIGINT, INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_watch_progress(
    p_profile_id BIGINT,
    p_events     JSONB
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    ev JSONB;
    eid BIGINT;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR ev IN SELECT * FROM jsonb_array_elements(p_events)
    LOOP
        IF ev->>'operation' = 'delete' THEN
            DELETE FROM public.watch_progress WHERE user_id = v_uid AND progress_key = ev->>'progress_key';
            INSERT INTO public.watch_progress_events(user_id, profile_id, operation, progress_key, content_id, content_type, video_id, season, episode, position, duration, last_watched)
                VALUES (v_uid, p_profile_id, 'delete', ev->>'progress_key', ev->>'content_id', ev->>'content_type', COALESCE(ev->>'video_id',''), (ev->>'season')::int, (ev->>'episode')::int, 0, 0, 0)
                RETURNING event_id INTO eid;
        ELSE
            INSERT INTO public.watch_progress(user_id, profile_id, content_id, content_type, video_id, season, episode, position, duration, last_watched, progress_key)
                VALUES (v_uid, p_profile_id, ev->>'content_id', ev->>'content_type', COALESCE(ev->>'video_id',''), (ev->>'season')::int, (ev->>'episode')::int, COALESCE((ev->>'position')::bigint, 0), COALESCE((ev->>'duration')::bigint, 0), COALESCE((ev->>'last_watched')::bigint, 0), ev->>'progress_key')
                ON CONFLICT (progress_key) DO UPDATE
                    SET position = EXCLUDED.position, duration = EXCLUDED.duration, last_watched = EXCLUDED.last_watched, updated_at = NOW();
            INSERT INTO public.watch_progress_events(user_id, profile_id, operation, progress_key, content_id, content_type, video_id, season, episode, position, duration, last_watched)
                VALUES (v_uid, p_profile_id, 'upsert', ev->>'progress_key', ev->>'content_id', ev->>'content_type', COALESCE(ev->>'video_id',''), (ev->>'season')::int, (ev->>'episode')::int, COALESCE((ev->>'position')::bigint, 0), COALESCE((ev->>'duration')::bigint, 0), COALESCE((ev->>'last_watched')::bigint, 0))
                RETURNING event_id INTO eid;
        END IF;
        v_max_id := GREATEST(v_max_id, eid);
    END LOOP;
    IF v_max_id > 0 THEN
        INSERT INTO public.sync_state(user_id, profile_id, resource, last_event_id, last_successful_push_ms)
            VALUES (v_uid, p_profile_id, 'watch_progress', v_max_id, (extract(epoch from NOW()) * 1000)::bigint)
            ON CONFLICT (user_id, profile_id, resource) DO UPDATE
                SET last_event_id = GREATEST(sync_state.last_event_id, EXCLUDED.last_event_id),
                    last_successful_push_ms = EXCLUDED.last_successful_push_ms,
                    updated_at = NOW();
    END IF;
    RETURN v_max_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_watch_progress(BIGINT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_delete_watch_progress(p_progress_key TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM public.watch_progress
        WHERE user_id = auth.uid() AND progress_key = p_progress_key;
    RETURN FOUND;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_delete_watch_progress(TEXT) TO authenticated;

-- ---------- watched_items ----------
CREATE OR REPLACE FUNCTION public.sync_get_watched_items_delta_cursor(p_profile_id BIGINT)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(last_event_id, 0) FROM public.sync_state
        WHERE user_id = auth.uid() AND profile_id = p_profile_id AND resource = 'watched_items';
$$;
GRANT EXECUTE ON FUNCTION public.sync_get_watched_items_delta_cursor(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_watched_items(
    p_profile_id      BIGINT,
    p_since_event_id  BIGINT,
    p_limit           INTEGER DEFAULT 900
) RETURNS SETOF public.watched_items_events
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.watched_items_events
        WHERE user_id = auth.uid()
          AND profile_id = p_profile_id
          AND event_id > p_since_event_id
        ORDER BY event_id ASC
        LIMIT LEAST(p_limit, 1000);
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_watched_items(BIGINT, BIGINT, INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_watched_items(p_profile_id BIGINT, p_events JSONB)
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    ev JSONB;
    eid BIGINT;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR ev IN SELECT * FROM jsonb_array_elements(p_events)
    LOOP
        IF ev->>'operation' = 'delete' THEN
            DELETE FROM public.watched_items WHERE user_id = v_uid
                AND content_id = ev->>'content_id' AND content_type = ev->>'content_type'
                AND COALESCE(season, -1) = COALESCE((ev->>'season')::int, -1)
                AND COALESCE(episode, -1) = COALESCE((ev->>'episode')::int, -1);
            INSERT INTO public.watched_items_events(user_id, profile_id, operation, content_id, content_type, title, season, episode, watched_at)
                VALUES (v_uid, p_profile_id, 'delete', ev->>'content_id', ev->>'content_type', COALESCE(ev->>'title',''), (ev->>'season')::int, (ev->>'episode')::int, COALESCE((ev->>'watched_at')::bigint, 0))
                RETURNING event_id INTO eid;
        ELSE
            INSERT INTO public.watched_items(user_id, profile_id, content_id, content_type, title, season, episode, watched_at)
                VALUES (v_uid, p_profile_id, ev->>'content_id', ev->>'content_type', COALESCE(ev->>'title',''), (ev->>'season')::int, (ev->>'episode')::int, COALESCE((ev->>'watched_at')::bigint, 0))
                ON CONFLICT DO NOTHING;
            INSERT INTO public.watched_items_events(user_id, profile_id, operation, content_id, content_type, title, season, episode, watched_at)
                VALUES (v_uid, p_profile_id, 'upsert', ev->>'content_id', ev->>'content_type', COALESCE(ev->>'title',''), (ev->>'season')::int, (ev->>'episode')::int, COALESCE((ev->>'watched_at')::bigint, 0))
                RETURNING event_id INTO eid;
        END IF;
        v_max_id := GREATEST(v_max_id, eid);
    END LOOP;
    IF v_max_id > 0 THEN
        INSERT INTO public.sync_state(user_id, profile_id, resource, last_event_id, last_successful_push_ms)
            VALUES (v_uid, p_profile_id, 'watched_items', v_max_id, (extract(epoch from NOW()) * 1000)::bigint)
            ON CONFLICT (user_id, profile_id, resource) DO UPDATE
                SET last_event_id = GREATEST(sync_state.last_event_id, EXCLUDED.last_event_id),
                    last_successful_push_ms = EXCLUDED.last_successful_push_ms,
                    updated_at = NOW();
    END IF;
    RETURN v_max_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_watched_items(BIGINT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_delete_watched_items(
    p_content_id   TEXT,
    p_content_type TEXT,
    p_season       INTEGER DEFAULT NULL,
    p_episode      INTEGER DEFAULT NULL
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM public.watched_items
        WHERE user_id = auth.uid()
          AND content_id = p_content_id
          AND content_type = p_content_type
          AND COALESCE(season, -1) = COALESCE(p_season, -1)
          AND COALESCE(episode, -1) = COALESCE(p_episode, -1);
    RETURN FOUND;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_delete_watched_items(TEXT, TEXT, INTEGER, INTEGER) TO authenticated;

-- ---------- library ----------
CREATE OR REPLACE FUNCTION public.sync_pull_library(p_profile_id BIGINT)
RETURNS SETOF public.library
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.library WHERE user_id = auth.uid() AND profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_library(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_get_library_delta_cursor(p_profile_id BIGINT)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(MAX(extract(epoch from last_modified) * 1000)::bigint, 0) FROM public.library
        WHERE user_id = auth.uid() AND profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_get_library_delta_cursor(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_library_delta(p_profile_id BIGINT, p_since_ms BIGINT)
RETURNS SETOF public.library
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.library
        WHERE user_id = auth.uid()
          AND profile_id = p_profile_id
          AND (extract(epoch from last_modified) * 1000)::bigint > p_since_ms
        ORDER BY last_modified ASC;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_library_delta(BIGINT, BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_library_items(p_profile_id BIGINT, p_items JSONB)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    it JSONB;
    n INTEGER := 0;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR it IN SELECT * FROM jsonb_array_elements(p_items)
    LOOP
        INSERT INTO public.library(user_id, profile_id, content_id, content_type, kind, payload, last_modified)
            VALUES (v_uid, p_profile_id, it->>'content_id', it->>'content_type', it->>'kind', COALESCE(it->'payload', '{}'::jsonb), NOW())
            ON CONFLICT (user_id, profile_id, content_id, content_type, kind) DO UPDATE
                SET payload = EXCLUDED.payload, last_modified = NOW();
        n := n + 1;
    END LOOP;
    RETURN n;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_library_items(BIGINT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_delete_library_items(
    p_profile_id   BIGINT,
    p_content_id   TEXT,
    p_content_type TEXT,
    p_kind         TEXT
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM public.library
        WHERE user_id = auth.uid()
          AND profile_id = p_profile_id
          AND content_id = p_content_id
          AND content_type = p_content_type
          AND kind = p_kind;
    RETURN FOUND;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_delete_library_items(BIGINT, TEXT, TEXT, TEXT) TO authenticated;

-- ---------- addons ----------
CREATE OR REPLACE FUNCTION public.sync_push_addons(p_addons JSONB)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    a JSONB;
    n INTEGER := 0;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR a IN SELECT * FROM jsonb_array_elements(p_addons)
    LOOP
        INSERT INTO public.addons(user_id, profile_id, url, name, enabled, sort_order)
            VALUES (v_uid, COALESCE((a->>'profile_id')::bigint, 1), a->>'url', a->>'name', COALESCE((a->>'enabled')::bool, true), COALESCE((a->>'sort_order')::int, 0))
            ON CONFLICT (user_id, profile_id, url) DO UPDATE
                SET name = EXCLUDED.name, enabled = EXCLUDED.enabled, sort_order = EXCLUDED.sort_order, updated_at = NOW();
        n := n + 1;
    END LOOP;
    RETURN n;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_addons(JSONB) TO authenticated;

-- ---------- plugins ----------
CREATE OR REPLACE FUNCTION public.sync_push_plugins(p_plugins JSONB)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    p JSONB;
    n INTEGER := 0;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR p IN SELECT * FROM jsonb_array_elements(p_plugins)
    LOOP
        INSERT INTO public.plugins(user_id, profile_id, url, name, enabled, sort_order, repo_type)
            VALUES (v_uid, COALESCE((p->>'profile_id')::bigint, 1), p->>'url', p->>'name', COALESCE((p->>'enabled')::bool, true), COALESCE((p->>'sort_order')::int, 0), p->>'repo_type')
            ON CONFLICT (user_id, profile_id, url) DO UPDATE
                SET name = EXCLUDED.name, enabled = EXCLUDED.enabled, sort_order = EXCLUDED.sort_order, repo_type = EXCLUDED.repo_type, updated_at = NOW();
        n := n + 1;
    END LOOP;
    RETURN n;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_plugins(JSONB) TO authenticated;

-- ---------- collections ----------
CREATE OR REPLACE FUNCTION public.sync_push_collections(p_profile_id BIGINT, p_collections JSONB)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    INSERT INTO public.collections(user_id, profile_id, collections_json, updated_at)
        VALUES (auth.uid(), p_profile_id, p_collections, NOW())
        ON CONFLICT (user_id, profile_id) DO UPDATE
            SET collections_json = EXCLUDED.collections_json, updated_at = NOW();
    SELECT true;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_collections(BIGINT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_collections(p_profile_id BIGINT)
RETURNS JSONB
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT collections_json FROM public.collections
        WHERE user_id = auth.uid() AND profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_collections(BIGINT) TO authenticated;

-- ---------- profile_settings / home_catalog_settings ----------
CREATE OR REPLACE FUNCTION public.sync_push_profile_settings_blob(p_profile_id BIGINT, p_blob JSONB)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    INSERT INTO public.profile_settings_blob(user_id, profile_id, settings_json, updated_at)
        VALUES (auth.uid(), p_profile_id, p_blob, NOW())
        ON CONFLICT (user_id, profile_id) DO UPDATE
            SET settings_json = EXCLUDED.settings_json, updated_at = NOW();
    SELECT true;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_profile_settings_blob(BIGINT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_profile_settings_blob(p_profile_id BIGINT)
RETURNS JSONB
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT settings_json FROM public.profile_settings_blob
        WHERE user_id = auth.uid() AND profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_profile_settings_blob(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_home_catalog_settings(p_profile_id BIGINT, p_blob JSONB)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    INSERT INTO public.home_catalog_settings_blob(user_id, profile_id, settings_json, updated_at)
        VALUES (auth.uid(), p_profile_id, p_blob, NOW())
        ON CONFLICT (user_id, profile_id) DO UPDATE
            SET settings_json = EXCLUDED.settings_json, updated_at = NOW();
    SELECT true;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_home_catalog_settings(BIGINT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_home_catalog_settings(p_profile_id BIGINT)
RETURNS JSONB
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT settings_json FROM public.home_catalog_settings_blob
        WHERE user_id = auth.uid() AND profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_home_catalog_settings(BIGINT) TO authenticated;

-- ---------- profiles ----------
CREATE OR REPLACE FUNCTION public.sync_push_profiles(p_profiles JSONB)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    p JSONB;
    n INTEGER := 0;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR p IN SELECT * FROM jsonb_array_elements(p_profiles)
    LOOP
        INSERT INTO public.profiles(user_id, profile_index, name, avatar_color_hex, uses_primary_addons, uses_primary_plugins, avatar_id, avatar_url)
            VALUES (v_uid, (p->>'profile_index')::int, COALESCE(p->>'name', ''), COALESCE(p->>'avatar_color_hex', '#1E88E5'), COALESCE((p->>'uses_primary_addons')::bool, false), COALESCE((p->>'uses_primary_plugins')::bool, false), p->>'avatar_id', p->>'avatar_url')
            ON CONFLICT (user_id, profile_index) DO UPDATE
                SET name = EXCLUDED.name, avatar_color_hex = EXCLUDED.avatar_color_hex, uses_primary_addons = EXCLUDED.uses_primary_addons, uses_primary_plugins = EXCLUDED.uses_primary_plugins, avatar_id = EXCLUDED.avatar_id, avatar_url = EXCLUDED.avatar_url, updated_at = NOW();
        n := n + 1;
    END LOOP;
    RETURN n;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_profiles(JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_profiles()
RETURNS SETOF public.profiles
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.profiles WHERE user_id = auth.uid() ORDER BY profile_index;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_profiles() TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_delete_profile_data(p_profile_id BIGINT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    DELETE FROM public.profiles WHERE user_id = auth.uid() AND id = p_profile_id;
    RETURN FOUND;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_delete_profile_data(BIGINT) TO authenticated;

-- ---------- provider_credentials ----------
CREATE OR REPLACE FUNCTION public.sync_seed_provider_credentials(p_credentials JSONB)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    c JSONB;
    n INTEGER := 0;
BEGIN
    IF v_uid IS NULL THEN RAISE EXCEPTION 'not authenticated'; END IF;
    FOR c IN SELECT * FROM jsonb_array_elements(p_credentials)
    LOOP
        INSERT INTO public.provider_credentials(user_id, provider, credential_json, updated_at)
            VALUES (v_uid, c->>'provider', COALESCE(c->'credential_json', '{}'::jsonb), NOW())
            ON CONFLICT (user_id, provider) DO UPDATE
                SET credential_json = EXCLUDED.credential_json, updated_at = NOW();
        n := n + 1;
    END LOOP;
    RETURN n;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_seed_provider_credentials(JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_provider_credentials(p_provider TEXT, p_credential JSONB)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    INSERT INTO public.provider_credentials(user_id, provider, credential_json, updated_at)
        VALUES (auth.uid(), p_provider, p_credential, NOW())
        ON CONFLICT (user_id, provider) DO UPDATE
            SET credential_json = EXCLUDED.credential_json, updated_at = NOW();
    SELECT true;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_provider_credentials(TEXT, JSONB) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_provider_credentials()
RETURNS SETOF public.provider_credentials
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT * FROM public.provider_credentials WHERE user_id = auth.uid();
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_provider_credentials() TO authenticated;

-- ---------- profile PIN ----------
CREATE OR REPLACE FUNCTION public.set_profile_pin(p_profile_id BIGINT, p_pin TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profile_locks(profile_id, pin_enabled, pin_hash)
        VALUES (p_profile_id, TRUE, crypt(p_pin, gen_salt('bf')))
        ON CONFLICT (profile_id) DO UPDATE
            SET pin_enabled = TRUE, pin_hash = crypt(p_pin, gen_salt('bf')), failed_attempts = 0, pin_locked_until = NULL, updated_at = NOW();
    RETURN TRUE;
END;
$$;
GRANT EXECUTE ON FUNCTION public.set_profile_pin(BIGINT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.clear_profile_pin(p_profile_id BIGINT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE public.profile_locks
        SET pin_enabled = FALSE, pin_hash = NULL, failed_attempts = 0, pin_locked_until = NULL, updated_at = NOW()
        WHERE profile_id = p_profile_id;
    RETURN TRUE;
END;
$$;
GRANT EXECUTE ON FUNCTION public.clear_profile_pin(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.verify_profile_pin(p_profile_id BIGINT, p_pin TEXT)
RETURNS TABLE(unlocked BOOLEAN, retry_after_seconds INTEGER)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_locked_until TIMESTAMPTZ;
    v_hash TEXT;
    v_enabled BOOLEAN;
BEGIN
    SELECT pin_enabled, pin_hash, pin_locked_until INTO v_enabled, v_hash, v_locked_until
        FROM public.profile_locks WHERE profile_id = p_profile_id;
    IF NOT FOUND OR NOT v_enabled THEN
        RETURN QUERY SELECT TRUE, 0;
        RETURN;
    END IF;
    IF v_locked_until IS NOT NULL AND v_locked_until > NOW() THEN
        RETURN QUERY SELECT FALSE, EXTRACT(EPOCH FROM (v_locked_until - NOW()))::int;
        RETURN;
    END IF;
    IF v_hash = crypt(p_pin, v_hash) THEN
        UPDATE public.profile_locks SET failed_attempts = 0, pin_locked_until = NULL, updated_at = NOW() WHERE profile_id = p_profile_id;
        RETURN QUERY SELECT TRUE, 0;
    ELSE
        UPDATE public.profile_locks
            SET failed_attempts = failed_attempts + 1,
                pin_locked_until = CASE WHEN failed_attempts + 1 >= 5 THEN NOW() + INTERVAL '5 minutes' ELSE pin_locked_until END,
                updated_at = NOW()
            WHERE profile_id = p_profile_id;
        RETURN QUERY SELECT FALSE, 0;
    END IF;
END;
$$;
GRANT EXECUTE ON FUNCTION public.verify_profile_pin(BIGINT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_profile_locks()
RETURNS TABLE(profile_id BIGINT, pin_enabled BOOLEAN, pin_locked_until TIMESTAMPTZ)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT pl.profile_id, pl.pin_enabled, pl.pin_locked_until
        FROM public.profile_locks pl
        JOIN public.profiles p ON p.id = pl.profile_id
        WHERE p.user_id = auth.uid();
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_profile_locks() TO authenticated;