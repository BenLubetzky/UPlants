-- 001: delete behaviour for foreign keys, extra integrity checks, indexes, updated_at triggers.
-- Runs on top of the existing tables (see ../database.sql). Safe to re-run.

-- ---------------------------------------------------------------------------
-- Foreign keys: deleting a user or post cleans up everything that hangs off it.
-- Without this, deleting a post with a single comment fails.
-- ---------------------------------------------------------------------------
alter table public.profiles
  drop constraint if exists profiles_id_fkey,
  add constraint profiles_id_fkey foreign key (id) references auth.users(id) on delete cascade;

alter table public.posts
  drop constraint if exists posts_author_id_fkey,
  add constraint posts_author_id_fkey foreign key (author_id) references public.profiles(id) on delete cascade;
-- posts_category_id_fkey stays RESTRICT: a category in use can't be deleted.

alter table public.comments
  drop constraint if exists comments_post_id_fkey,
  add constraint comments_post_id_fkey foreign key (post_id) references public.posts(id) on delete cascade,
  drop constraint if exists comments_author_id_fkey,
  add constraint comments_author_id_fkey foreign key (author_id) references public.profiles(id) on delete cascade;

alter table public.reactions
  drop constraint if exists reactions_post_id_fkey,
  add constraint reactions_post_id_fkey foreign key (post_id) references public.posts(id) on delete cascade,
  drop constraint if exists reactions_user_id_fkey,
  add constraint reactions_user_id_fkey foreign key (user_id) references public.profiles(id) on delete cascade;

alter table public.ratings
  drop constraint if exists ratings_post_id_fkey,
  add constraint ratings_post_id_fkey foreign key (post_id) references public.posts(id) on delete cascade,
  drop constraint if exists ratings_user_id_fkey,
  add constraint ratings_user_id_fkey foreign key (user_id) references public.profiles(id) on delete cascade;

alter table public.post_views
  drop constraint if exists post_views_post_id_fkey,
  add constraint post_views_post_id_fkey foreign key (post_id) references public.posts(id) on delete cascade,
  drop constraint if exists post_views_viewer_id_fkey,
  add constraint post_views_viewer_id_fkey foreign key (viewer_id) references public.profiles(id) on delete set null;

alter table public.favorite_lists
  drop constraint if exists favorite_lists_owner_id_fkey,
  add constraint favorite_lists_owner_id_fkey foreign key (owner_id) references public.profiles(id) on delete cascade;

alter table public.favorite_list_items
  drop constraint if exists favorite_list_items_list_id_fkey,
  add constraint favorite_list_items_list_id_fkey foreign key (list_id) references public.favorite_lists(id) on delete cascade,
  drop constraint if exists favorite_list_items_post_id_fkey,
  add constraint favorite_list_items_post_id_fkey foreign key (post_id) references public.posts(id) on delete cascade;

alter table public.follows
  drop constraint if exists follows_follower_id_fkey,
  add constraint follows_follower_id_fkey foreign key (follower_id) references public.profiles(id) on delete cascade,
  drop constraint if exists follows_following_id_fkey,
  add constraint follows_following_id_fkey foreign key (following_id) references public.profiles(id) on delete cascade;

alter table public.user_interests
  drop constraint if exists user_interests_user_id_fkey,
  add constraint user_interests_user_id_fkey foreign key (user_id) references public.profiles(id) on delete cascade,
  drop constraint if exists user_interests_category_id_fkey,
  add constraint user_interests_category_id_fkey foreign key (category_id) references public.categories(id) on delete cascade;

alter table public.messages
  drop constraint if exists messages_sender_id_fkey,
  add constraint messages_sender_id_fkey foreign key (sender_id) references public.profiles(id) on delete cascade,
  drop constraint if exists messages_recipient_id_fkey,
  add constraint messages_recipient_id_fkey foreign key (recipient_id) references public.profiles(id) on delete cascade,
  drop constraint if exists messages_shared_post_id_fkey,
  add constraint messages_shared_post_id_fkey foreign key (shared_post_id) references public.posts(id) on delete set null;

alter table public.notifications
  drop constraint if exists notifications_user_id_fkey,
  add constraint notifications_user_id_fkey foreign key (user_id) references public.profiles(id) on delete cascade,
  drop constraint if exists notifications_actor_id_fkey,
  add constraint notifications_actor_id_fkey foreign key (actor_id) references public.profiles(id) on delete set null,
  drop constraint if exists notifications_post_id_fkey,
  add constraint notifications_post_id_fkey foreign key (post_id) references public.posts(id) on delete cascade,
  drop constraint if exists notifications_message_id_fkey,
  add constraint notifications_message_id_fkey foreign key (message_id) references public.messages(id) on delete cascade;

alter table public.device_tokens
  drop constraint if exists device_tokens_user_id_fkey,
  add constraint device_tokens_user_id_fkey foreign key (user_id) references public.profiles(id) on delete cascade;

-- ---------------------------------------------------------------------------
-- Integrity checks
-- ---------------------------------------------------------------------------
alter table public.follows
  drop constraint if exists follows_not_self,
  add constraint follows_not_self check (follower_id <> following_id);

alter table public.messages
  drop constraint if exists messages_not_self,
  add constraint messages_not_self check (sender_id <> recipient_id);
-- No "body or shared_post_id" check: deleting a shared post nulls shared_post_id, and the
-- message should survive (shown as "post unavailable"). The messaging service rejects empty messages.

-- "Bob" and "bob" are the same username.
create unique index if not exists profiles_username_lower_key on public.profiles (lower(username));

-- ---------------------------------------------------------------------------
-- Indexes for the app's main queries
-- ---------------------------------------------------------------------------
create extension if not exists pg_trgm with schema extensions;

-- User search (by username / display name, partial matches)
create index if not exists profiles_username_trgm_idx on public.profiles using gin (username extensions.gin_trgm_ops);
create index if not exists profiles_display_name_trgm_idx on public.profiles using gin (display_name extensions.gin_trgm_ops);

-- Feeds, profile pages, search, sorting
create index if not exists posts_author_created_idx on public.posts (author_id, created_at desc);
create index if not exists posts_category_created_idx on public.posts (category_id, created_at desc);
create index if not exists posts_created_idx on public.posts (created_at desc);
create index if not exists posts_rating_idx on public.posts (rating_avg desc);
create index if not exists posts_views_idx on public.posts (view_count desc);
create index if not exists posts_interactions_idx on public.posts (interaction_count desc);
create index if not exists posts_search_idx on public.posts using gin (search_vector);
create index if not exists posts_tags_idx on public.posts using gin (tags);

create index if not exists comments_post_created_idx on public.comments (post_id, created_at);
create index if not exists comments_author_idx on public.comments (author_id);
create index if not exists reactions_user_idx on public.reactions (user_id);
create index if not exists ratings_user_idx on public.ratings (user_id);
create index if not exists post_views_post_idx on public.post_views (post_id, viewed_at);
create index if not exists post_views_viewer_idx on public.post_views (viewer_id);
create index if not exists favorite_lists_owner_idx on public.favorite_lists (owner_id);
create index if not exists favorite_list_items_post_idx on public.favorite_list_items (post_id);
create index if not exists follows_following_idx on public.follows (following_id);
create index if not exists user_interests_category_idx on public.user_interests (category_id);
create index if not exists messages_conversation_idx on public.messages (sender_id, recipient_id, created_at desc);
create index if not exists messages_recipient_idx on public.messages (recipient_id, created_at desc);
create index if not exists messages_shared_post_idx on public.messages (shared_post_id);
create index if not exists notifications_user_idx on public.notifications (user_id, is_read, created_at desc);
create index if not exists notifications_actor_idx on public.notifications (actor_id);
create index if not exists notifications_post_idx on public.notifications (post_id);
create index if not exists notifications_message_idx on public.notifications (message_id);
create index if not exists device_tokens_user_idx on public.device_tokens (user_id);

-- ---------------------------------------------------------------------------
-- updated_at is maintained by the database, not by each service
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists set_updated_at on public.profiles;
create trigger set_updated_at before update on public.profiles
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.posts;
create trigger set_updated_at before update on public.posts
  for each row execute function public.set_updated_at();

drop trigger if exists set_updated_at on public.ratings;
create trigger set_updated_at before update on public.ratings
  for each row execute function public.set_updated_at();
