-- =============================================================================
-- 20260828180000_apachiy_library_events_delta.sql
-- Event-log library delta sync (Nuvio TV client shape).
-- Replaces timestamp-based sync_pull_library_delta(p_profile_id, p_since_ms).
-- =============================================================================

CREATE TABLE IF NOT EXISTS public.library_events (
    event_id        BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_id      BIGINT NOT NULL,
    operation       TEXT NOT NULL,
    content_id      TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    name            TEXT NOT NULL DEFAULT '',
    poster          TEXT,
    poster_shape    TEXT NOT NULL DEFAULT 'POSTER',
    background      TEXT,
    description     TEXT,
    release_info    TEXT,
    imdb_rating     DOUBLE PRECISION,
    genres          JSONB NOT NULL DEFAULT '[]'::jsonb,
    addon_base_url  TEXT,
    added_at        BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_library_events_user_profile
    ON public.library_events(user_id, profile_id, event_id);

-- Remove legacy signatures so PostgREST schema cache exposes only the TV shapes.
DROP FUNCTION IF EXISTS public.sync_pull_library_delta(BIGINT, BIGINT);
DROP FUNCTION IF EXISTS public.sync_push_library_items(BIGINT, JSONB);
DROP FUNCTION IF EXISTS public.sync_delete_library_items(BIGINT, TEXT, TEXT, TEXT);

CREATE OR REPLACE FUNCTION public.sync_get_library_delta_cursor(p_profile_id BIGINT)
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
              AND resource = 'library'
        ),
        0
    );
$$;
GRANT EXECUTE ON FUNCTION public.sync_get_library_delta_cursor(BIGINT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_pull_library_delta(
    p_profile_id      BIGINT,
    p_since_event_id  BIGINT,
    p_limit           INTEGER DEFAULT 900
)
RETURNS TABLE(
    event_id        BIGINT,
    operation       TEXT,
    content_id      TEXT,
    content_type    TEXT,
    name            TEXT,
    poster          TEXT,
    poster_shape    TEXT,
    background      TEXT,
    description     TEXT,
    release_info    TEXT,
    imdb_rating     DOUBLE PRECISION,
    genres          JSONB,
    addon_base_url  TEXT,
    added_at        BIGINT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT
        e.event_id,
        e.operation,
        e.content_id,
        e.content_type,
        e.name,
        e.poster,
        e.poster_shape,
        e.background,
        e.description,
        e.release_info,
        e.imdb_rating,
        e.genres,
        e.addon_base_url,
        e.added_at
    FROM public.library_events e
    WHERE e.user_id = auth.uid()
      AND e.profile_id = p_profile_id
      AND e.event_id > p_since_event_id
    ORDER BY e.event_id ASC
    LIMIT LEAST(GREATEST(p_limit, 1), 1000);
$$;
GRANT EXECUTE ON FUNCTION public.sync_pull_library_delta(BIGINT, BIGINT, INTEGER) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_push_library_items(
    p_items             JSONB,
    p_profile_id        BIGINT,
    p_origin_client_id  TEXT DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    it JSONB;
    eid BIGINT;
    v_payload JSONB;
    v_kind TEXT := 'watchlist';
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;

    FOR it IN SELECT * FROM jsonb_array_elements(COALESCE(p_items, '[]'::jsonb))
    LOOP
        v_payload := jsonb_build_object(
            'name', COALESCE(it->>'name', ''),
            'poster', it->>'poster',
            'poster_shape', COALESCE(it->>'poster_shape', 'POSTER'),
            'background', it->>'background',
            'description', it->>'description',
            'release_info', it->>'release_info',
            'imdb_rating', it->>'imdb_rating',
            'genres', COALESCE(it->'genres', '[]'::jsonb),
            'addon_base_url', it->>'addon_base_url',
            'added_at', COALESCE((it->>'added_at')::bigint, 0)
        );

        INSERT INTO public.library(user_id, profile_id, content_id, content_type, kind, payload, last_modified)
        VALUES (
            v_uid,
            p_profile_id,
            it->>'content_id',
            it->>'content_type',
            v_kind,
            v_payload,
            NOW()
        )
        ON CONFLICT (user_id, profile_id, content_id, content_type, kind) DO UPDATE
            SET payload = EXCLUDED.payload,
                last_modified = NOW();

        INSERT INTO public.library_events(
            user_id, profile_id, operation, content_id, content_type,
            name, poster, poster_shape, background, description,
            release_info, imdb_rating, genres, addon_base_url, added_at
        )
        VALUES (
            v_uid,
            p_profile_id,
            'upsert',
            it->>'content_id',
            it->>'content_type',
            COALESCE(it->>'name', ''),
            it->>'poster',
            COALESCE(it->>'poster_shape', 'POSTER'),
            it->>'background',
            it->>'description',
            it->>'release_info',
            NULLIF(it->>'imdb_rating', '')::double precision,
            COALESCE(it->'genres', '[]'::jsonb),
            it->>'addon_base_url',
            COALESCE((it->>'added_at')::bigint, 0)
        )
        RETURNING event_id INTO eid;

        v_max_id := GREATEST(v_max_id, eid);
    END LOOP;

    IF v_max_id > 0 THEN
        INSERT INTO public.sync_state(user_id, profile_id, resource, last_event_id, last_successful_push_ms)
            VALUES (v_uid, p_profile_id, 'library', v_max_id, (extract(epoch from NOW()) * 1000)::bigint)
            ON CONFLICT (user_id, profile_id, resource) DO UPDATE
                SET last_event_id = GREATEST(sync_state.last_event_id, EXCLUDED.last_event_id),
                    last_successful_push_ms = EXCLUDED.last_successful_push_ms,
                    updated_at = NOW();
    END IF;

    RETURN v_max_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_push_library_items(JSONB, BIGINT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.sync_delete_library_items(
    p_profile_id        BIGINT,
    p_keys              JSONB,
    p_origin_client_id  TEXT DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    key JSONB;
    eid BIGINT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;

    FOR key IN SELECT * FROM jsonb_array_elements(COALESCE(p_keys, '[]'::jsonb))
    LOOP
        DELETE FROM public.library
        WHERE user_id = v_uid
          AND profile_id = p_profile_id
          AND content_id = key->>'content_id'
          AND content_type = key->>'content_type';

        INSERT INTO public.library_events(
            user_id, profile_id, operation, content_id, content_type
        )
        VALUES (
            v_uid,
            p_profile_id,
            'delete',
            key->>'content_id',
            key->>'content_type'
        )
        RETURNING event_id INTO eid;

        v_max_id := GREATEST(v_max_id, eid);
    END LOOP;

    IF v_max_id > 0 THEN
        INSERT INTO public.sync_state(user_id, profile_id, resource, last_event_id, last_successful_push_ms)
            VALUES (v_uid, p_profile_id, 'library', v_max_id, (extract(epoch from NOW()) * 1000)::bigint)
            ON CONFLICT (user_id, profile_id, resource) DO UPDATE
                SET last_event_id = GREATEST(sync_state.last_event_id, EXCLUDED.last_event_id),
                    last_successful_push_ms = EXCLUDED.last_successful_push_ms,
                    updated_at = NOW();
    END IF;

    RETURN v_max_id;
END;
$$;
GRANT EXECUTE ON FUNCTION public.sync_delete_library_items(BIGINT, JSONB, TEXT) TO authenticated;

NOTIFY pgrst, 'reload schema';
