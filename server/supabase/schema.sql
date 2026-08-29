-- ============================================================
-- 手机定位系统 · Supabase Schema
-- 执行位置: Supabase Dashboard → SQL Editor → 粘贴全文 → Run
-- 安全模型: RLS 开启 + 零公开策略
--           anon/authenticated 均无任何读写权限
--           只有服务端 service_role key(Vercel 环境变量)可读写
-- ============================================================

create table if not exists public.locations (
  id        bigint generated always as identity primary key,
  ts        timestamptz not null default now(),
  device    text not null default 'primary',
  provider  text,
  lat       double precision not null check (lat  between -90  and 90),
  lng       double precision not null check (lng  between -180 and 180),
  accuracy  double precision check (accuracy >= 0),
  speed     double precision,
  bearing   double precision,
  altitude  double precision,
  battery   double precision check (battery between 0 and 100)
);

alter table public.locations enable row level security;

-- 故意不创建任何 policy: anon key 即使泄露也读不到一个字节

create index if not exists locations_ts_desc_idx  on public.locations (ts desc);
create index if not exists locations_device_ts_idx on public.locations (device, ts desc);

-- 最新位置视图(security_invoker 确保视图不绕过 RLS)
create or replace view public.v_latest_location
with (security_invoker = on) as
  select * from public.locations
  order by ts desc
  limit 1;

-- 可选: 数据保留 90 天, 手动或用 pg_cron 定期执行
-- delete from public.locations where ts < now() - interval '90 days';
