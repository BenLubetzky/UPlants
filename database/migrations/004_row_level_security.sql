-- 004: lock the public Supabase API. Safe to re-run.
--
-- The app never talks to Supabase directly: it goes through our microservices, which connect
-- with the secret (service_role) key or the database connection string. Both bypass RLS.
--
-- Turning RLS on with NO policies means anyone holding only the publishable/anon key
-- (which is not secret) gets nothing from these tables. All access goes through the services.

alter table public.profiles            enable row level security;
alter table public.categories          enable row level security;
alter table public.posts               enable row level security;
alter table public.comments            enable row level security;
alter table public.reactions           enable row level security;
alter table public.ratings             enable row level security;
alter table public.post_views          enable row level security;
alter table public.favorite_lists      enable row level security;
alter table public.favorite_list_items enable row level security;
alter table public.follows             enable row level security;
alter table public.user_interests      enable row level security;
alter table public.messages            enable row level security;
alter table public.notifications       enable row level security;
alter table public.device_tokens       enable row level security;
