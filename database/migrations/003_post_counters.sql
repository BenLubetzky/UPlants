-- 003: keep the counters on public.posts in sync with the rows they count.
-- (interaction_count and search_vector are generated columns, so they follow automatically.)
-- Safe to re-run.

-- Likes / dislikes ----------------------------------------------------------
create or replace function public.sync_reaction_counts()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op in ('UPDATE', 'DELETE') then
    update public.posts
    set like_count    = like_count    - (old.value = 1)::int,
        dislike_count = dislike_count - (old.value = -1)::int
    where id = old.post_id;
  end if;
  if tg_op in ('INSERT', 'UPDATE') then
    update public.posts
    set like_count    = like_count    + (new.value = 1)::int,
        dislike_count = dislike_count + (new.value = -1)::int
    where id = new.post_id;
  end if;
  return null;
end;
$$;

drop trigger if exists sync_reaction_counts on public.reactions;
create trigger sync_reaction_counts after insert or update or delete on public.reactions
  for each row execute function public.sync_reaction_counts();

-- Star ratings: recomputed from the table so the average never drifts -------
create or replace function public.refresh_post_rating(p_post_id uuid)
returns void
language sql
security definer
set search_path = ''
as $$
  update public.posts
  set rating_count = s.n,
      rating_avg   = s.avg
  from (
    select count(*)::int as n, coalesce(round(avg(stars), 2), 0) as avg
    from public.ratings
    where post_id = p_post_id
  ) s
  where id = p_post_id;
$$;

create or replace function public.sync_rating_stats()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op in ('UPDATE', 'DELETE') then
    perform public.refresh_post_rating(old.post_id);
  end if;
  if tg_op = 'INSERT' or (tg_op = 'UPDATE' and new.post_id <> old.post_id) then
    perform public.refresh_post_rating(new.post_id);
  end if;
  return null;
end;
$$;

drop trigger if exists sync_rating_stats on public.ratings;
create trigger sync_rating_stats after insert or update or delete on public.ratings
  for each row execute function public.sync_rating_stats();

-- Comments ------------------------------------------------------------------
create or replace function public.sync_comment_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set comment_count = comment_count + 1 where id = new.post_id;
  else
    update public.posts set comment_count = comment_count - 1 where id = old.post_id;
  end if;
  return null;
end;
$$;

drop trigger if exists sync_comment_count on public.comments;
create trigger sync_comment_count after insert or delete on public.comments
  for each row execute function public.sync_comment_count();

-- Views ---------------------------------------------------------------------
create or replace function public.sync_view_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' then
    update public.posts set view_count = view_count + 1 where id = new.post_id;
  else
    update public.posts set view_count = view_count - 1 where id = old.post_id;
  end if;
  return null;
end;
$$;

drop trigger if exists sync_view_count on public.post_views;
create trigger sync_view_count after insert or delete on public.post_views
  for each row execute function public.sync_view_count();

-- Shares: sharing a post = sending a message with shared_post_id ------------
create or replace function public.sync_share_count()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if tg_op = 'INSERT' and new.shared_post_id is not null then
    update public.posts set share_count = share_count + 1 where id = new.shared_post_id;
  elsif tg_op = 'DELETE' and old.shared_post_id is not null then
    update public.posts set share_count = share_count - 1 where id = old.shared_post_id;
  end if;
  return null;
end;
$$;

drop trigger if exists sync_share_count on public.messages;
create trigger sync_share_count after insert or delete on public.messages
  for each row execute function public.sync_share_count();

-- These are trigger helpers, not an API.
revoke execute on function public.refresh_post_rating(uuid) from public, anon, authenticated;

-- One-off: bring any existing rows in line with the data -------------------
update public.posts p
set like_count    = (select count(*) from public.reactions r where r.post_id = p.id and r.value = 1),
    dislike_count = (select count(*) from public.reactions r where r.post_id = p.id and r.value = -1),
    comment_count = (select count(*) from public.comments c where c.post_id = p.id),
    view_count    = (select count(*) from public.post_views v where v.post_id = p.id),
    share_count   = (select count(*) from public.messages m where m.shared_post_id = p.id),
    rating_count  = (select count(*) from public.ratings r where r.post_id = p.id),
    rating_avg    = coalesce((select round(avg(stars), 2) from public.ratings r where r.post_id = p.id), 0);
