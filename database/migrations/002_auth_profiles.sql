-- 002: link Supabase Auth to public.profiles. Safe to re-run.

-- ---------------------------------------------------------------------------
-- Every new Auth user gets a profile automatically.
-- The users-service must send the username in the sign-up metadata:
--   POST /auth/v1/signup  { "email", "password", "data": { "username": "...", "display_name": "..." } }
-- If the username is missing, invalid or taken, the whole sign-up is rolled back
-- (Supabase answers "Database error saving new user"), so check availability first.
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, username, display_name)
  values (
    new.id,
    new.raw_user_meta_data ->> 'username',
    coalesce(new.raw_user_meta_data ->> 'display_name', new.raw_user_meta_data ->> 'username')
  );
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- The app logs in with a username, but Supabase Auth logs in with an email.
-- The users-service calls this (with the secret key) to find the email first.
-- Only the backend may call it: otherwise anyone could look up users' emails.
-- ---------------------------------------------------------------------------
create or replace function public.email_for_username(p_username text)
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select u.email
  from auth.users u
  join public.profiles p on p.id = u.id
  where lower(p.username) = lower(p_username);
$$;

revoke execute on function public.email_for_username(text) from public, anon, authenticated;
grant execute on function public.email_for_username(text) to service_role;
