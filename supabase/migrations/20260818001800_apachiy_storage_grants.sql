-- =============================================================================
-- 20260818001800_apachiy_storage_grants.sql
-- Self-hosted Storage API needs table grants + bucket RLS policies.
-- Without these, service_role uploads and public reads return 403/42501.
-- =============================================================================

GRANT ALL ON ALL TABLES IN SCHEMA storage TO service_role;
GRANT SELECT ON ALL TABLES IN SCHEMA storage TO anon, authenticated;
GRANT ALL ON ALL SEQUENCES IN SCHEMA storage TO service_role;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA storage TO anon, authenticated;

ALTER DEFAULT PRIVILEGES IN SCHEMA storage
    GRANT ALL ON TABLES TO service_role;
ALTER DEFAULT PRIVILEGES IN SCHEMA storage
    GRANT SELECT ON TABLES TO anon, authenticated;

DROP POLICY IF EXISTS buckets_public_read ON storage.buckets;
CREATE POLICY buckets_public_read
    ON storage.buckets FOR SELECT
    USING (true);

DROP POLICY IF EXISTS buckets_service_role_all ON storage.buckets;
CREATE POLICY buckets_service_role_all
    ON storage.buckets FOR ALL
    USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

DROP POLICY IF EXISTS "avatars_service_write" ON storage.objects;
CREATE POLICY "avatars_service_write"
    ON storage.objects FOR ALL
    USING (bucket_id = 'avatars' AND auth.role() = 'service_role')
    WITH CHECK (bucket_id = 'avatars' AND auth.role() = 'service_role');
