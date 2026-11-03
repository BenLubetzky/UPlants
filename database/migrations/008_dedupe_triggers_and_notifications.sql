-- 008: remove duplicate triggers, keep notification triggers in the repo, fix the counters. Safe to re-run.
--
-- The database had an older set of triggers, created in the dashboard before these migrations,
-- that did the same job as 001/003. Both sets ran, so counters were wrong
-- (1 comment counted as 2, likes going negative, ...).
-- 003 is the version we keep; the older duplicates are dropped here.

-- Counters: older duplicates of 003 --------------------------------------------
drop trigger if exists comments_count       on public.comments;
drop trigger if exists reactions_count      on public.reactions;
drop trigger if exists ratings_stats        on public.ratings;
drop trigger if exists post_views_count     on public.post_views;
drop trigger if exists messages_share_count on public.messages;

drop function if exists public.refresh_comment_count();
drop function if exists public.refresh_reaction_counts();
drop function if exists public.refresh_rating_stats();
drop function if exists public.increment_view_count();
drop function if exists public.increment_share_count();

-- updated_at: older duplicates of 001 ------------------------------------------
drop trigger if exists profiles_updated_at on public.profiles;
drop trigger if exists ratings_updated_at  on public.ratings;
drop trigger if exists posts_updated_at    on public.posts;

-- On posts, only edits to the content change updated_at, not likes/views/comments
-- (those update the counter columns many times a minute).
drop trigger if exists set_updated_at on public.posts;
create trigger set_updated_at
  before update of category_id, content_type, title, description, plant_species, tags,
                   media_url, thumbnail_url, location_name
  on public.posts
  for each row execute function public.set_updated_at();

-- Notifications ------------------------------------------------------------------
-- These existed only in the dashboard. Kept here so the repo matches the database.
-- notifications-service only reads and marks them; the database creates them.

-- Comment, like (not dislike) or rating on your post. No notification for your own actions.
create or replace function public.notify_post_author()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $$
declare
  v_actor  uuid;
  v_type   public.notification_type;
  v_author uuid;
begin
  if tg_table_name = 'comments' then
    v_actor := new.author_id; v_type := 'comment';
  elsif tg_table_name = 'reactions' then
    if new.value <> 1 then return null; end if;            -- only notify likes
    if tg_op = 'UPDATE' and old.value = 1 then return null; end if;
    v_actor := new.user_id; v_type := 'like';
  elsif tg_table_name = 'ratings' then
    v_actor := new.user_id; v_type := 'rating';
  end if;

  select author_id into v_author from public.posts where id = new.post_id;
  if v_author is not null and v_author <> v_actor then
    insert into public.notifications (user_id, actor_id, type, post_id)
    values (v_author, v_actor, v_type, new.post_id);
  end if;
  return null;
end $$;

-- New post: notify everyone who follows the author.
create or replace function public.notify_followers_new_post()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $$
begin
  insert into public.notifications (user_id, actor_id, type, post_id)
  select f.follower_id, new.author_id, 'new_post', new.id
    from public.follows f
   where f.following_id = new.author_id;
  return null;
end $$;

-- Someone followed you.
create or replace function public.notify_follow()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $$
begin
  insert into public.notifications (user_id, actor_id, type)
  values (new.following_id, new.follower_id, 'follow');
  return null;
end $$;

-- New message, or a post shared with you.
create or replace function public.notify_message()
returns trigger
language plpgsql
security definer
set search_path = 'public'
as $$
begin
  insert into public.notifications (user_id, actor_id, type, post_id, message_id)
  values (new.recipient_id, new.sender_id,
          case when new.shared_post_id is not null then 'share' else 'message' end::public.notification_type,
          new.shared_post_id, new.id);
  return null;
end $$;

drop trigger if exists posts_notify on public.posts;
create trigger posts_notify after insert on public.posts
  for each row execute function public.notify_followers_new_post();

drop trigger if exists comments_notify on public.comments;
create trigger comments_notify after insert on public.comments
  for each row execute function public.notify_post_author();

drop trigger if exists reactions_notify on public.reactions;
create trigger reactions_notify after insert or update on public.reactions
  for each row execute function public.notify_post_author();

drop trigger if exists ratings_notify on public.ratings;
create trigger ratings_notify after insert on public.ratings
  for each row execute function public.notify_post_author();

drop trigger if exists follows_notify on public.follows;
create trigger follows_notify after insert on public.follows
  for each row execute function public.notify_follow();

drop trigger if exists messages_notify on public.messages;
create trigger messages_notify after insert on public.messages
  for each row execute function public.notify_message();

revoke execute on function public.notify_post_author(), public.notify_followers_new_post(),
  public.notify_follow(), public.notify_message() from public, anon, authenticated;

-- One-off: recount everything from the rows ------------------------------------
update public.posts p
set like_count    = (select count(*) from public.reactions r where r.post_id = p.id and r.value = 1),
    dislike_count = (select count(*) from public.reactions r where r.post_id = p.id and r.value = -1),
    comment_count = (select count(*) from public.comments c where c.post_id = p.id),
    view_count    = (select count(*) from public.post_views v where v.post_id = p.id),
    share_count   = (select count(*) from public.messages m where m.shared_post_id = p.id),
    rating_count  = (select count(*) from public.ratings r where r.post_id = p.id),
    rating_avg    = coalesce((select round(avg(stars), 2) from public.ratings r where r.post_id = p.id), 0);
