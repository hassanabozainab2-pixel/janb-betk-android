-- Janb Betk production schema
-- Apply only to a NEW, dedicated Supabase project after review.
-- Admin authorization is controlled by auth.users.raw_app_meta_data.role = 'admin'.
-- Never put a service-role/secret key in the Android app.

create extension if not exists pgcrypto;

create or replace function public.is_janb_admin()
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin', false);
$$;

create table if not exists public.businesses (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 2 and 100),
  business_type text not null check (business_type in ('shop','food','craft','maintenance','delivery','home_service','other')),
  category text not null,
  phone text not null,
  whatsapp text,
  address text,
  description text,
  media_path text,
  status text not null default 'pending' check (status in ('pending','approved','rejected','hidden')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  owner_id uuid not null references auth.users(id) on delete cascade,
  plan text not null check (plan in ('weekly','monthly','yearly')),
  amount_egp integer not null check (amount_egp in (50,200,2000)),
  starts_at timestamptz,
  ends_at timestamptz,
  status text not null default 'pending' check (status in ('pending','active','expired','cancelled')),
  created_at timestamptz not null default now(),
  check ((starts_at is null and ends_at is null) or (starts_at is not null and ends_at > starts_at))
);

create table if not exists public.payment_requests (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  business_id uuid references public.businesses(id) on delete set null,
  subscription_id uuid references public.subscriptions(id) on delete set null,
  featured_ad_id uuid,
  payment_type text not null check (payment_type in ('subscription','featured_ad')),
  amount_egp integer not null check (amount_egp > 0),
  receipt_path text not null,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  admin_note text,
  reviewed_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.featured_ads (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  business_id uuid references public.businesses(id) on delete set null,
  slot smallint not null check (slot between 1 and 3),
  title text not null check (char_length(title) between 2 and 100),
  description text,
  phone text,
  whatsapp text,
  media_path text not null,
  media_type text not null check (media_type in ('image','video')),
  amount_egp integer not null default 100 check (amount_egp = 100),
  starts_at timestamptz,
  ends_at timestamptz,
  status text not null default 'pending' check (status in ('pending','active','expired','rejected')),
  created_at timestamptz not null default now(),
  check ((starts_at is null and ends_at is null) or (starts_at is not null and ends_at > starts_at))
);

alter table public.payment_requests
  drop constraint if exists payment_requests_featured_ad_id_fkey;
alter table public.payment_requests
  add constraint payment_requests_featured_ad_id_fkey
  foreign key (featured_ad_id) references public.featured_ads(id) on delete set null;

create unique index if not exists featured_ads_one_active_per_slot
  on public.featured_ads(slot) where status = 'active';

create table if not exists public.app_settings (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

insert into public.app_settings(key,value) values
 ('etisalat_cash_number', '{"value":""}'::jsonb),
 ('featured_ad_weekly_price_egp', '{"value":100}'::jsonb),
 ('grace_period_hours', '{"value":24}'::jsonb)
on conflict (key) do nothing;

create table if not exists public.push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  token text not null unique,
  platform text not null default 'android',
  created_at timestamptz not null default now()
);

create table if not exists public.user_notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  title text not null,
  body text not null,
  data jsonb not null default '{}'::jsonb,
  sent_at timestamptz,
  read_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists businesses_owner_idx on public.businesses(owner_id);
create index if not exists businesses_public_idx on public.businesses(status, category);
create index if not exists subscriptions_owner_idx on public.subscriptions(owner_id, status, ends_at);
create index if not exists payment_requests_owner_idx on public.payment_requests(owner_id, created_at desc);
create index if not exists featured_ads_live_idx on public.featured_ads(status, starts_at, ends_at);
create index if not exists notifications_user_idx on public.user_notifications(user_id, created_at desc);

alter table public.businesses enable row level security;
alter table public.subscriptions enable row level security;
alter table public.payment_requests enable row level security;
alter table public.featured_ads enable row level security;
alter table public.app_settings enable row level security;
alter table public.push_tokens enable row level security;
alter table public.user_notifications enable row level security;

-- Explicit grants: newly-created public tables are not automatically exposed on current Supabase projects.
grant select on public.businesses, public.featured_ads, public.app_settings to anon, authenticated;
grant insert on public.businesses, public.featured_ads, public.payment_requests, public.push_tokens to authenticated;
grant select on public.subscriptions, public.payment_requests, public.push_tokens, public.user_notifications to authenticated;
grant update, delete, insert, select on public.businesses, public.subscriptions, public.payment_requests,
  public.featured_ads, public.app_settings, public.user_notifications to authenticated;
grant update, delete, select on public.push_tokens to authenticated;

-- Public visitors only see approved businesses with an active subscription,
-- including the agreed 24-hour grace period.
drop policy if exists "public sees currently paid businesses" on public.businesses;
create policy "public sees currently paid businesses"
on public.businesses for select to anon, authenticated
using (
  status = 'approved' and exists (
    select 1 from public.subscriptions s
    where s.business_id = businesses.id
      and s.status = 'active'
      and s.starts_at <= now()
      and s.ends_at + interval '24 hours' > now()
  )
);

drop policy if exists "owners create pending businesses" on public.businesses;
create policy "owners create pending businesses"
on public.businesses for insert to authenticated
with check (owner_id = (select auth.uid()) and status = 'pending');

drop policy if exists "admin manages businesses" on public.businesses;
create policy "admin manages businesses"
on public.businesses for all to authenticated
using ((select public.is_janb_admin()))
with check ((select public.is_janb_admin()));

drop policy if exists "owners read own subscriptions" on public.subscriptions;
create policy "owners read own subscriptions"
on public.subscriptions for select to authenticated
using (owner_id = (select auth.uid()));

drop policy if exists "admin manages subscriptions" on public.subscriptions;
create policy "admin manages subscriptions"
on public.subscriptions for all to authenticated
using ((select public.is_janb_admin()))
with check ((select public.is_janb_admin()));

drop policy if exists "owners create pending payments" on public.payment_requests;
create policy "owners create pending payments"
on public.payment_requests for insert to authenticated
with check (owner_id = (select auth.uid()) and status = 'pending');

drop policy if exists "owners read own payments" on public.payment_requests;
create policy "owners read own payments"
on public.payment_requests for select to authenticated
using (owner_id = (select auth.uid()));

drop policy if exists "admin manages payments" on public.payment_requests;
create policy "admin manages payments"
on public.payment_requests for all to authenticated
using ((select public.is_janb_admin()))
with check ((select public.is_janb_admin()));

drop policy if exists "public sees active featured ads" on public.featured_ads;
create policy "public sees active featured ads"
on public.featured_ads for select to anon, authenticated
using (status = 'active' and starts_at <= now() and ends_at > now());

drop policy if exists "owners create pending featured ads" on public.featured_ads;
create policy "owners create pending featured ads"
on public.featured_ads for insert to authenticated
with check (owner_id = (select auth.uid()) and status = 'pending');

drop policy if exists "admin manages featured ads" on public.featured_ads;
create policy "admin manages featured ads"
on public.featured_ads for all to authenticated
using ((select public.is_janb_admin()))
with check ((select public.is_janb_admin()));

drop policy if exists "public reads app settings" on public.app_settings;
create policy "public reads app settings"
on public.app_settings for select to anon, authenticated using (true);

drop policy if exists "admin manages app settings" on public.app_settings;
create policy "admin manages app settings"
on public.app_settings for all to authenticated
using ((select public.is_janb_admin()))
with check ((select public.is_janb_admin()));

drop policy if exists "users manage own push tokens" on public.push_tokens;
create policy "users manage own push tokens"
on public.push_tokens for all to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists "users read own notifications" on public.user_notifications;
create policy "users read own notifications"
on public.user_notifications for select to authenticated
using (user_id = (select auth.uid()));

drop policy if exists "users mark own notifications read" on public.user_notifications;
create policy "users mark own notifications read"
on public.user_notifications for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));

drop policy if exists "admin manages notifications" on public.user_notifications;
create policy "admin manages notifications"
on public.user_notifications for all to authenticated
using ((select public.is_janb_admin()))
with check ((select public.is_janb_admin()));

-- Public media bucket. Uploads are scoped to the authenticated owner's folder.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('janb-media','janb-media',true,52428800,
  array['image/jpeg','image/png','image/webp','video/mp4','video/quicktime'])
on conflict (id) do nothing;

drop policy if exists "public reads Janb media" on storage.objects;
create policy "public reads Janb media"
on storage.objects for select to anon, authenticated
using (bucket_id = 'janb-media');

drop policy if exists "users upload to own Janb folder" on storage.objects;
create policy "users upload to own Janb folder"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'janb-media'
  and (storage.foldername(name))[1] = (select auth.uid())::text
);

drop policy if exists "admins manage Janb media" on storage.objects;
create policy "admins manage Janb media"
on storage.objects for all to authenticated
using (bucket_id = 'janb-media' and (select public.is_janb_admin()))
with check (bucket_id = 'janb-media' and (select public.is_janb_admin()));
