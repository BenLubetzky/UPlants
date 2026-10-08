-- 005: Storage buckets for uploaded files. Safe to re-run.
--
-- The services upload with the secret key. The buckets are public, so the app can
-- show a file straight from the URL saved in posts.media_url / profiles.avatar_url:
--   https://<project-ref>.supabase.co/storage/v1/object/public/<bucket>/<path>
-- (The free plan caps uploads at 50 MB per file.)

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('avatars', 'avatars', true, 5 * 1024 * 1024,
    array['image/jpeg', 'image/png', 'image/webp']),
  ('post-media', 'post-media', true, 50 * 1024 * 1024,
    array['image/*', 'video/*', 'audio/*'])
on conflict (id) do nothing;
