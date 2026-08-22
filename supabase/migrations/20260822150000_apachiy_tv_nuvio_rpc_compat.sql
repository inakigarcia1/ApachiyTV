-- =============================================================================
-- TV app (Nuvio fork) RPC signatures — wrappers over Apachiy schema RPCs.
-- PostgREST matches by parameter names/counts; the TV client expects Nuvio shapes.
-- =============================================================================

-- ---------- delta cursors: never return SQL NULL (TV decodes as Long) ----------
CREATE OR REPLACE FUNCTION public.sync_get_watch_progress_delta_cursor(p_profile_id BIGINT)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(
        (
            SELECT last_event_id
            FROM public.sync_state
            WHERE user_id = auth.uid()
              AND profile_id = p_profile_id
              AND resource = 'watch_progress'
        ),
        0
    );
$$;

CREATE OR REPLACE FUNCTION public.sync_get_watched_items_delta_cursor(p_profile_id BIGINT)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT COALESCE(
        (
            SELECT last_event_id
            FROM public.sync_state
            WHERE user_id = auth.uid()
              AND profile_id = p_profile_id
              AND resource = 'watched_items'
        ),
        0
    );
$$;

-- ---------- watch progress: snapshot pull + Nuvio push shape ----------
CREATE OR REPLACE FUNCTION public.sync_pull_watch_progress(p_profile_id BIGINT)
RETURNS SETOF public.watch_progress
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT *
    FROM public.watch_progress
    WHERE user_id = auth.uid()
      AND profile_id = p_profile_id
    ORDER BY last_watched DESC, id ASC;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_watch_progress(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_watch_progress(
    p_entries JSONB,
    p_profile_id BIGINT,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    entry JSONB;
    eid BIGINT;
    v_key TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;

    FOR entry IN SELECT * FROM jsonb_array_elements(COALESCE(p_entries, '[]'::jsonb))
    LOOP
        v_key := COALESCE(entry->>'progress_key', '');
        IF v_key = '' THEN
            CONTINUE;
        END IF;

        INSERT INTO public.watch_progress(
            user_id, profile_id, content_id, content_type, video_id,
            season, episode, position, duration, last_watched, progress_key
        )
        VALUES (
            v_uid,
            p_profile_id,
            entry->>'content_id',
            entry->>'content_type',
            COALESCE(entry->>'video_id', ''),
            (entry->>'season')::int,
            (entry->>'episode')::int,
            COALESCE((entry->>'position')::bigint, 0),
            COALESCE((entry->>'duration')::bigint, 0),
            COALESCE((entry->>'last_watched')::bigint, 0),
            v_key
        )
        ON CONFLICT (progress_key) DO UPDATE
            SET position = EXCLUDED.position,
                duration = EXCLUDED.duration,
                last_watched = EXCLUDED.last_watched,
                updated_at = NOW();

        INSERT INTO public.watch_progress_events(
            user_id, profile_id, operation, progress_key, content_id, content_type,
            video_id, season, episode, position, duration, last_watched
        )
        VALUES (
            v_uid,
            p_profile_id,
            'upsert',
            v_key,
            entry->>'content_id',
            entry->>'content_type',
            COALESCE(entry->>'video_id', ''),
            (entry->>'season')::int,
            (entry->>'episode')::int,
            COALESCE((entry->>'position')::bigint, 0),
            COALESCE((entry->>'duration')::bigint, 0),
            COALESCE((entry->>'last_watched')::bigint, 0)
        )
        RETURNING event_id INTO eid;

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
GRANT EXECUTE ON FUNCTION public.sync_push_watch_progress(JSONB, BIGINT, TEXT) TO authenticated;

-- ---------- watched items: paginated snapshot + Nuvio push shape ----------
CREATE OR REPLACE FUNCTION public.sync_pull_watched_items(
    p_profile_id BIGINT,
    p_page INTEGER,
    p_page_size INTEGER
) RETURNS SETOF public.watched_items
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT *
    FROM public.watched_items
    WHERE user_id = auth.uid()
      AND profile_id = p_profile_id
    ORDER BY watched_at DESC, id ASC
    OFFSET GREATEST(p_page - 1, 0) * GREATEST(p_page_size, 1)
    LIMIT LEAST(GREATEST(p_page_size, 1), 1000);
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_watched_items(BIGINT, INTEGER, INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_watched_items(
    p_items JSONB,
    p_profile_id BIGINT,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    item JSONB;
    eid BIGINT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;

    FOR item IN SELECT * FROM jsonb_array_elements(COALESCE(p_items, '[]'::jsonb))
    LOOP
        INSERT INTO public.watched_items(
            user_id, profile_id, content_id, content_type, title, season, episode, watched_at
        )
        VALUES (
            v_uid,
            p_profile_id,
            item->>'content_id',
            item->>'content_type',
            COALESCE(item->>'title', ''),
            (item->>'season')::int,
            (item->>'episode')::int,
            COALESCE((item->>'watched_at')::bigint, 0)
        )
        ON CONFLICT DO NOTHING;

        INSERT INTO public.watched_items_events(
            user_id, profile_id, operation, content_id, content_type, title, season, episode, watched_at
        )
        VALUES (
            v_uid,
            p_profile_id,
            'upsert',
            item->>'content_id',
            item->>'content_type',
            COALESCE(item->>'title', ''),
            (item->>'season')::int,
            (item->>'episode')::int,
            COALESCE((item->>'watched_at')::bigint, 0)
        )
        RETURNING event_id INTO eid;

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
GRANT EXECUTE ON FUNCTION public.sync_push_watched_items(JSONB, BIGINT, TEXT) TO authenticated;

-- ---------- collections: row shape expected by TV sync ----------
DROP FUNCTION IF EXISTS public.sync_pull_collections(BIGINT);

CREATE OR REPLACE FUNCTION public.sync_pull_collections(p_profile_id BIGINT)
RETURNS TABLE(
    profile_id BIGINT,
    collections_json JSONB,
    updated_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT c.profile_id, c.collections_json, c.updated_at
    FROM public.collections c
    WHERE c.user_id = auth.uid()
      AND c.profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_collections(BIGINT) TO authenticated;

-- ---------- profile settings blobs (platform param ignored — single blob per profile) ----------
CREATE OR REPLACE FUNCTION public.sync_push_profile_settings_blob(
    p_profile_id BIGINT,
    p_settings_json JSONB,
    p_platform TEXT,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.sync_push_profile_settings_blob(p_profile_id, p_settings_json);
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_profile_settings_blob(BIGINT, JSONB, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_profile_settings_blob(
    p_platform TEXT,
    p_profile_id BIGINT
)
RETURNS TABLE(
    profile_id BIGINT,
    settings_json JSONB,
    updated_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT psb.profile_id, psb.settings_json, psb.updated_at
    FROM public.profile_settings_blob psb
    WHERE psb.user_id = auth.uid()
      AND psb.profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_profile_settings_blob(TEXT, BIGINT) TO authenticated;

-- ---------- home catalog settings ----------
CREATE OR REPLACE FUNCTION public.sync_push_home_catalog_settings(
    p_profile_id BIGINT,
    p_settings_json JSONB,
    p_platform TEXT,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.sync_push_home_catalog_settings(p_profile_id, p_settings_json);
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_home_catalog_settings(BIGINT, JSONB, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_home_catalog_settings(
    p_platform TEXT,
    p_profile_id BIGINT
)
RETURNS TABLE(
    profile_id BIGINT,
    settings_json JSONB,
    updated_at TIMESTAMPTZ
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT h.profile_id, h.settings_json, h.updated_at
    FROM public.home_catalog_settings_blob h
    WHERE h.user_id = auth.uid()
      AND h.profile_id = p_profile_id;
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_home_catalog_settings(TEXT, BIGINT) TO authenticated;

-- ---------- provider credentials (profile-scoped in TV client) ----------
CREATE OR REPLACE FUNCTION public.sync_seed_provider_credentials(
    p_profile_id BIGINT,
    p_credentials JSONB,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS INTEGER
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT public.sync_seed_provider_credentials(p_credentials);
$$;
GRANT EXECUTE ON FUNCTION public.sync_seed_provider_credentials(BIGINT, JSONB, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_provider_credentials(
    p_profile_id BIGINT,
    p_credentials JSONB,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    c JSONB;
BEGIN
    FOR c IN SELECT * FROM jsonb_array_elements(COALESCE(p_credentials, '[]'::jsonb))
    LOOP
        PERFORM public.sync_push_provider_credentials(c->>'provider', COALESCE(c->'credential_json', '{}'::jsonb));
    END LOOP;
    RETURN TRUE;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_provider_credentials(BIGINT, JSONB, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_provider_credentials(p_profile_id BIGINT)
RETURNS SETOF public.provider_credentials
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT *
    FROM public.provider_credentials
    WHERE user_id = auth.uid();
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_provider_credentials(BIGINT) TO authenticated;

-- ---------- library: paginated snapshot (Nuvio column shape from payload JSON) ----------
CREATE OR REPLACE FUNCTION public.sync_pull_library(
    p_profile_id BIGINT,
    p_limit INTEGER,
    p_offset INTEGER
)
RETURNS TABLE(
    id BIGINT,
    user_id UUID,
    profile_id BIGINT,
    content_id TEXT,
    content_type TEXT,
    name TEXT,
    poster TEXT,
    poster_shape TEXT,
    background TEXT,
    description TEXT,
    release_info TEXT,
    imdb_rating DOUBLE PRECISION,
    genres JSONB,
    addon_base_url TEXT,
    added_at BIGINT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT
        l.id,
        l.user_id,
        l.profile_id,
        l.content_id,
        l.content_type,
        COALESCE(l.payload->>'name', ''),
        l.payload->>'poster',
        COALESCE(l.payload->>'poster_shape', 'POSTER'),
        l.payload->>'background',
        l.payload->>'description',
        l.payload->>'release_info',
        NULLIF(l.payload->>'imdb_rating', '')::double precision,
        COALESCE(l.payload->'genres', '[]'::jsonb),
        l.payload->>'addon_base_url',
        COALESCE((l.payload->>'added_at')::bigint, 0)
    FROM public.library l
    WHERE l.user_id = auth.uid()
      AND l.profile_id = p_profile_id
    ORDER BY l.id ASC
    OFFSET GREATEST(p_offset, 0)
    LIMIT LEAST(GREATEST(p_limit, 1), 1000);
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_library(BIGINT, INTEGER, INTEGER) TO authenticated;

NOTIFY pgrst, 'reload schema';
