-- ============================================================
-- 手机定位系统 · Supabase Schema (v2.2 双模式: 被动轨迹 + 主动唤起)
-- 执行位置: Supabase Dashboard → SQL Editor → 粘贴全文 → Run
-- 全部幂等, 重跑无副作用, 不会动历史数据
-- ⚠ 共用数据库约定: 本系统所有表/视图/RPC 一律 phonelocation_ 小写前缀,
--   与同一 Supabase 项目里的其他项目隔离, 互不影响
-- 安全模型: 所有表 RLS 开启 + 零公开策略
--           只有服务端 service_role key(Vercel 环境变量)可读写
-- ============================================================

-- ---------- 1. 位置表 (v1) ----------
create table if not exists public.phonelocation_locations (
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

alter table public.phonelocation_locations enable row level security;

-- v2.2: 老库幂等补列 (充电状态 + Wi-Fi SSID)
alter table public.phonelocation_locations add column if not exists charging boolean;
alter table public.phonelocation_locations add column if not exists ssid text;

create index if not exists phonelocation_locations_ts_desc_idx   on public.phonelocation_locations (ts desc);
create index if not exists phonelocation_locations_device_ts_idx on public.phonelocation_locations (device, ts desc);

-- ---------- 2. 命令表 (v2 新增: 主动模式) ----------
create table if not exists public.phonelocation_commands (
  id          bigint generated always as identity primary key,
  created_at  timestamptz not null default now(),
  device      text not null default 'primary',
  type        text not null default 'locate' check (type in ('locate')),
  status      text not null default 'pending'
              check (status in ('pending','claimed','done','expired')),
  claimed_at  timestamptz,
  done_at     timestamptz
);

alter table public.phonelocation_commands enable row level security;

create index if not exists phonelocation_commands_claim_idx on public.phonelocation_commands (device, status, created_at);

-- ---------- 3. 设备心跳表 (v2 新增: 在线判定与被动间隔解耦) ----------
create table if not exists public.phonelocation_devices (
  device       text primary key,
  last_seen    timestamptz not null default now(),
  last_report  timestamptz
);

alter table public.phonelocation_devices enable row level security;

-- ---------- 4. 原子领取 RPC (长轮询核心, FOR UPDATE SKIP LOCKED 防双领) ----------
-- 只领取 10 分钟内创建的 pending 命令, 过期命令由 API 懒清理为 expired
create or replace function public.phonelocation_claim_next_command(p_device text)
returns setof public.phonelocation_commands
language sql
as $$
  update public.phonelocation_commands c
     set status = 'claimed', claimed_at = now()
   where c.id = (
         select id
           from public.phonelocation_commands
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
create or replace view public.phonelocation_v_latest_location
with (security_invoker = on) as
  select * from public.phonelocation_locations
  order by ts desc
  limit 1;

-- ---------- 6. 远程配置表 (v2.4.2: 地图端改窗口, poll 应答捎带下发) ----------
-- enabled=false 时手机只保留 10 分钟探活(收指令), 不定位不上报
create table if not exists public.phonelocation_config (
  device     text primary key default 'primary',
  enabled    boolean not null default true,
  work_start int not null default 480  check (work_start between 0 and 1424),  -- 分钟, 08:00
  work_end   int not null default 1200 check (work_end between 1 and 1440),    -- 分钟, 20:00
  work_days  text not null default '1-5',                                       -- 1=周一..7=周日, 支持 1-5/1,3,5
  version    bigint not null default 1,                                         -- 每次修改+1, 客户端版本比对
  updated_at timestamptz not null default now(),
  constraint phonelocation_config_window_chk check (work_start < work_end)
);

alter table public.phonelocation_config enable row level security;

-- ---------- 7. 维护 ----------
-- 数据保留 90 天, 手动或 pg_cron 定期执行:
-- delete from public.phonelocation_locations where ts < now() - interval '90 days';
-- delete from public.phonelocation_commands where created_at < now() - interval '30 days';
