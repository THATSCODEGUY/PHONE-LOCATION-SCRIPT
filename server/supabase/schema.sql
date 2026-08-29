-- ============================================================
-- 手机定位系统 · Supabase Schema (v2 双模式: 被动轨迹 + 主动唤起)
-- 执行位置: Supabase Dashboard → SQL Editor → 粘贴全文 → Run
-- 全部幂等, v1 老库直接重跑即可升级, 不会动历史数据
-- 安全模型: 所有表 RLS 开启 + 零公开策略
--           只有服务端 service_role key(Vercel 环境变量)可读写
-- ============================================================

-- ---------- 1. 位置表 (v1) ----------
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
  battery   double precision check (battery between 0 and 100),
  charging  boolean,
  ssid      text
);

alter table public.locations enable row level security;

-- v2.2: 老库幂等补列 (充电状态 + Wi-Fi SSID)
alter table public.locations add column if not exists charging boolean;
alter table public.locations add column if not exists ssid text;

create index if not exists locations_ts_desc_idx   on public.locations (ts desc);
create index if not exists locations_device_ts_idx on public.locations (device, ts desc);

-- ---------- 2. 命令表 (v2 新增: 主动模式) ----------
create table if not exists public.commands (
  id          bigint generated always as identity primary key,
  created_at  timestamptz not null default now(),
  device      text not null default 'primary',
  type        text not null default 'locate' check (type in ('locate')),
  status      text not null default 'pending'
              check (status in ('pending','claimed','done','expired')),
  claimed_at  timestamptz,
  done_at     timestamptz
);

alter table public.commands enable row level security;

create index if not exists commands_claim_idx on public.commands (device, status, created_at);

-- ---------- 3. 设备心跳表 (v2 新增: 在线判定与被动间隔解耦) ----------
create table if not exists public.devices (
  device       text primary key,
  last_seen    timestamptz not null default now(),
  last_report  timestamptz
);

alter table public.devices enable row level security;

-- ---------- 4. 原子领取 RPC (长轮询核心, FOR UPDATE SKIP LOCKED 防双领) ----------
-- 只领取 10 分钟内创建的 pending 命令, 过期命令由 API 懒清理为 expired
create or replace function public.claim_next_command(p_device text)
returns setof public.commands
language sql
as $$
  update public.commands c
     set status = 'claimed', claimed_at = now()
   where c.id = (
         select id
           from public.commands
          where status = 'pending'
            and device = p_device
            and created_at > now() - interval '10 minutes'
          order by id asc
            for update skip locked
          limit 1
       )
  returning c.*;
$$;

-- ---------- 5. 视图 ----------
create or replace view public.v_latest_location
with (security_invoker = on) as
  select * from public.locations
  order by ts desc
  limit 1;

-- ---------- 6. 维护 ----------
-- 数据保留 90 天, 手动或 pg_cron 定期执行:
-- delete from public.locations where ts < now() - interval '90 days';
-- delete from public.commands where created_at < now() - interval '30 days';
