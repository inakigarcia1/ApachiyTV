-- =============================================================================
-- 20260818001500_apachiy_avatars_bucket.sql
-- Storage bucket `avatars` (public read; only service_role writes).
-- Avatars are seeded by the Apachiy admin (dev_seed.sql) and referenced by
-- `public.avatar_catalog.storage_path`.
-- =============================================================================
INSERT INTO storage.buckets (id, name, public)
    VALUES ('avatars', 'avatars', TRUE)
    ON CONFLICT (id) DO NOTHING;

-- Public read
DROP POLICY IF EXISTS "avatars_public_read" ON storage.objects;
CREATE POLICY "avatars_public_read"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'avatars');

-- Only the service role can write (admin tooling uses service role key)
DROP POLICY IF EXISTS "avatars_service_write" ON storage.objects;
CREATE POLICY "avatars_service_write"
    ON storage.objects FOR ALL
    USING (bucket_id = 'avatars' AND auth.role() = 'service_role')
    WITH CHECK (bucket_id = 'avatars' AND auth.role() = 'service_role');