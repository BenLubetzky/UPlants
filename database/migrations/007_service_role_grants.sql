-- 007: let the services (secret key = service_role) read and write our tables. Safe to re-run.
--
-- New Supabase projects no longer grant table privileges to the API roles automatically,
-- so the secret key got "permission denied for table profiles" (HTTP 403).
-- Only service_role gets access: anon/authenticated (the publishable key) stay locked out,
-- and RLS from 004 still blocks them.

grant usage on schema public to service_role;

grant select, insert, update, delete on all tables    in schema public to service_role;
grant usage, select                  on all sequences in schema public to service_role;
grant execute                        on all functions in schema public to service_role;

-- Tables, sequences and functions created later get the same access.
alter default privileges in schema public grant select, insert, update, delete on tables    to service_role;
alter default privileges in schema public grant usage, select                  on sequences to service_role;
alter default privileges in schema public grant execute                        on functions to service_role;
