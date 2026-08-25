-- =============================================================================
-- apachiy_provision_addons: service-role RPC to seed ApachiyTV addon URLs.
-- =============================================================================
CREATE OR REPLACE FUNCTION public.apachiy_provision_addons(
    p_user_id UUID,
    p_addons JSONB
)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    a JSONB;
    n INTEGER := 0;
BEGIN
    IF p_user_id IS NULL THEN
        RAISE EXCEPTION 'p_user_id is required';
    END IF;

    DELETE FROM public.addons
    WHERE user_id = p_user_id
      AND profile_id = 1;

    FOR a IN SELECT * FROM jsonb_array_elements(COALESCE(p_addons, '[]'::jsonb))
    LOOP
        INSERT INTO public.addons(user_id, profile_id, url, name, enabled, sort_order)
        VALUES (
            p_user_id,
            1,
            a->>'url',
            a->>'name',
            COALESCE((a->>'enabled')::bool, true),
            COALESCE((a->>'sort_order')::int, 0)
        )
        ON CONFLICT (user_id, profile_id, url) DO UPDATE
            SET name = EXCLUDED.name,
                enabled = EXCLUDED.enabled,
                sort_order = EXCLUDED.sort_order,
                updated_at = NOW();
        n := n + 1;
    END LOOP;

    RETURN n;
END;
$$;

REVOKE ALL ON FUNCTION public.apachiy_provision_addons(UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.apachiy_provision_addons(UUID, JSONB) TO service_role;
