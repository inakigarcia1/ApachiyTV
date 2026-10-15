-- Watch progress is one row per profile. progress_key stays the content id;
-- the previous UNIQUE (progress_key) made every profile share that row.

DO $$
DECLARE
    v_name text;
BEGIN
    SELECT c.conname INTO v_name
    FROM pg_constraint c
    WHERE c.conrelid = 'public.watch_progress'::regclass
      AND c.contype = 'u'
      AND pg_get_constraintdef(c.oid) NOT LIKE '%profile_id%'
      AND pg_get_constraintdef(c.oid) LIKE '%progress_key%';
    IF v_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.watch_progress DROP CONSTRAINT %I', v_name);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_watch_progress_user_profile_key
    ON public.watch_progress (user_id, profile_id, progress_key);

WITH latest AS (
    SELECT DISTINCT ON (user_id, profile_id, progress_key)
        user_id,
        profile_id,
        progress_key,
        operation,
        content_id,
        content_type,
        video_id,
        season,
        episode,
        position,
        duration,
        last_watched
    FROM public.watch_progress_events
    ORDER BY user_id, profile_id, progress_key, event_id DESC
),
removed AS (
    DELETE FROM public.watch_progress wp
    USING latest l
    WHERE l.operation = 'delete'
      AND wp.user_id = l.user_id
      AND wp.profile_id = l.profile_id
      AND wp.progress_key = l.progress_key
    RETURNING wp.id
)
INSERT INTO public.watch_progress (
    user_id, profile_id, content_id, content_type, video_id,
    season, episode, position, duration, last_watched, progress_key
)
SELECT
    l.user_id,
    l.profile_id,
    COALESCE(l.content_id, ''),
    COALESCE(l.content_type, ''),
    COALESCE(l.video_id, ''),
    l.season,
    l.episode,
    COALESCE(l.position, 0),
    COALESCE(l.duration, 0),
    COALESCE(l.last_watched, 0),
    l.progress_key
FROM latest l
WHERE l.operation IS DISTINCT FROM 'delete'
  AND COALESCE(l.progress_key, '') <> ''
  AND (SELECT COUNT(*) FROM removed) >= 0
ON CONFLICT (user_id, profile_id, progress_key) DO UPDATE
    SET content_id = EXCLUDED.content_id,
        content_type = EXCLUDED.content_type,
        video_id = EXCLUDED.video_id,
        season = EXCLUDED.season,
        episode = EXCLUDED.episode,
        position = EXCLUDED.position,
        duration = EXCLUDED.duration,
        last_watched = EXCLUDED.last_watched,
        updated_at = NOW();

-- COUNT(removed) >= 0 is always true. It keeps the DELETE CTE referenced.

CREATE OR REPLACE FUNCTION public.sync_push_watch_progress(
    p_profile_id BIGINT,
    p_events JSONB
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
            DELETE FROM public.watch_progress
                WHERE user_id = v_uid
                  AND profile_id = p_profile_id
                  AND progress_key = ev->>'progress_key';
            INSERT INTO public.watch_progress_events(
                user_id, profile_id, operation, progress_key, content_id, content_type,
                video_id, season, episode, position, duration, last_watched
            )
            VALUES (
                v_uid, p_profile_id, 'delete', ev->>'progress_key', ev->>'content_id',
                ev->>'content_type', COALESCE(ev->>'video_id', ''),
                (ev->>'season')::int, (ev->>'episode')::int, 0, 0, 0
            )
            RETURNING event_id INTO eid;
        ELSE
            INSERT INTO public.watch_progress(
                user_id, profile_id, content_id, content_type, video_id,
                season, episode, position, duration, last_watched, progress_key
            )
            VALUES (
                v_uid, p_profile_id, ev->>'content_id', ev->>'content_type',
                COALESCE(ev->>'video_id', ''), (ev->>'season')::int, (ev->>'episode')::int,
                COALESCE((ev->>'position')::bigint, 0), COALESCE((ev->>'duration')::bigint, 0),
                COALESCE((ev->>'last_watched')::bigint, 0), ev->>'progress_key'
            )
            ON CONFLICT (user_id, profile_id, progress_key) DO UPDATE
                SET position = EXCLUDED.position,
                    duration = EXCLUDED.duration,
                    last_watched = EXCLUDED.last_watched,
                    updated_at = NOW();
            INSERT INTO public.watch_progress_events(
                user_id, profile_id, operation, progress_key, content_id, content_type,
                video_id, season, episode, position, duration, last_watched
            )
            VALUES (
                v_uid, p_profile_id, 'upsert', ev->>'progress_key', ev->>'content_id',
                ev->>'content_type', COALESCE(ev->>'video_id', ''),
                (ev->>'season')::int, (ev->>'episode')::int,
                COALESCE((ev->>'position')::bigint, 0), COALESCE((ev->>'duration')::bigint, 0),
                COALESCE((ev->>'last_watched')::bigint, 0)
            )
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
        ON CONFLICT (user_id, profile_id, progress_key) DO UPDATE
            SET position = EXCLUDED.position,
                duration = EXCLUDED.duration,
                last_watched = EXCLUDED.last_watched,
                content_id = EXCLUDED.content_id,
                content_type = EXCLUDED.content_type,
                video_id = EXCLUDED.video_id,
                season = EXCLUDED.season,
                episode = EXCLUDED.episode,
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

DROP FUNCTION IF EXISTS public.sync_delete_watch_progress(TEXT);

CREATE OR REPLACE FUNCTION public.sync_delete_watch_progress(
    p_profile_id BIGINT,
    p_keys JSONB,
    p_origin_client_id TEXT DEFAULT NULL
) RETURNS BIGINT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_max_id BIGINT := 0;
    v_key TEXT;
    eid BIGINT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'not authenticated';
    END IF;

    FOR v_key IN
        SELECT DISTINCT trimmed
        FROM (
            SELECT btrim(value) AS trimmed
            FROM jsonb_array_elements_text(COALESCE(p_keys, '[]'::jsonb)) AS keys(value)
        ) parsed
        WHERE trimmed <> ''
    LOOP
        DELETE FROM public.watch_progress
            WHERE user_id = v_uid
              AND profile_id = p_profile_id
              AND progress_key = v_key;

        INSERT INTO public.watch_progress_events(
            user_id, profile_id, operation, progress_key, content_id, content_type,
            video_id, season, episode, position, duration, last_watched
        )
        VALUES (v_uid, p_profile_id, 'delete', v_key, '', '', '', NULL, NULL, 0, 0, 0)
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

GRANT EXECUTE ON FUNCTION public.sync_delete_watch_progress(BIGINT, JSONB, TEXT) TO authenticated;
