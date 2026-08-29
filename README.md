# 手机精准找回系统 · Redmi Note 15 Pro（v2 双模式）

自建定位追踪系统：**被动模式**（每 60 分钟稀疏轨迹，月流量 ~1MB）+ **主动模式**（任意浏览器点按钮，8~25 秒出最新位置）。与小米/谷歌官方查找设备（Phase 0 兜底）构成三层防御。

## 架构

```
Redmi Note 15 Pro (HyperOS)
 ├─ 被动: cron 每60分钟 → report.sh → POST /api/report (X-Device-Key)
 └─ 主动: agent.sh 长轮询挂线50s ←─ GET /api/poll ─────┐
        │ 收到"立即定位"命令                            │
        ▼                                              │
     立即 report.sh(带cmd_id) → 上报+销单               │
                                                       │
VERCEL (serverless)                                    │
 ├─ api/report.js    上报: 校验+时序安全密钥比对+销单+心跳 │
 ├─ api/poll.js      长轮询: 原子领取命令, maxDuration 60s│
 ├─ api/command.js   排队[获取位置]/查状态/读心跳 ◀────────┤
 ├─ api/locations.js 轨迹查询 (ACCESS_TOKEN)             │
 └─ public/map.html  面板: 获取位置按钮/心跳在线/任意刷新间隔│
        │ service_role (仅存于 Vercel 环境变量)           │
        ▼                                              │
SUPABASE: locations(轨迹) + commands(命令队列) + devices(心跳) + RLS全锁
```

**主动模式时延拆解**：命令到达手机 0~3 秒（长轮询挂线随到随回）+ GPS 锁星 5~15 秒 + 上报刷新 1~3 秒 ≈ **室外 8~25 秒，室内最坏 ~40 秒**（自动降级网络定位）。

## 目录

```
server/        部署到 Vercel（见下）
phone-termux/  拷入手机的 Termux 脚本（被动 cron + 主动 agent）
phone-app/     Kotlin 工业版（A 方案·B 验证稳定后交付）
docs/          Phase 0 系统级兜底清单（必读）
tests/         36 项逻辑自测
CHANGELOG.md   版本历史
```

## 部署步骤（约 20 分钟）

### 1. Supabase

1. supabase.com 新建项目（区域选香港/新加坡）
2. SQL Editor → 粘贴 `server/supabase/schema.sql` 全文 → Run（幂等，v1 老库重跑即升级）
3. Settings → API → 记下 **Project URL** 和 **service_role** 密钥（⚠ 绝密）

### 2. 生成两把密钥

```bash
openssl rand -hex 32   # → DEVICE_KEY   (手机上报+领命令)
openssl rand -hex 32   # → ACCESS_TOKEN (浏览器看地图+点按钮)
```
Windows PowerShell 替代：`-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })`

### 3. Vercel

```bash
npm i -g vercel
cd server
vercel link
vercel env add SUPABASE_URL          # Project URL
vercel env add SUPABASE_SERVICE_KEY  # service_role
vercel env add DEVICE_KEY
vercel env add ACCESS_TOKEN
vercel --prod
```

### 4. 验证服务端

```bash
# 被动链路: 应返回 {"ok":true,...}
curl -X POST https://xxx.vercel.app/api/report \
  -H "Content-Type: application/json" -H "X-Device-Key: <DEVICE_KEY>" \
  -d '{"lat":39.9042,"lng":116.4074,"provider":"gps","device":"test"}'

# 轨迹: 应返回 JSON 数组
curl "https://xxx.vercel.app/api/locations?token=<ACCESS_TOKEN>&limit=10"
```

### 5. 手机端（Termux）

1. F-Droid 安装 **Termux / Termux:API / Termux:Boot** 三件套，各打开一次（⚠ 不要 Google Play 版）
2. `phone-termux/` 整个文件夹拷入手机 `$HOME/phone-loc/`
3. Termux 中：
   ```bash
   cd ~/phone-loc
   bash setup.sh   # 交互: 装依赖→配置→cron(默认60min)→agent守护→开机自启→试运行
   ```
4. **逐项完成 `HYPEROS-保活清单.md`**（含主动模式专项检查 §8，决定生死）

### 6. 打开地图

```
https://xxx.vercel.app/map?token=<ACCESS_TOKEN>[&interval=30]
```

- **[获取位置]**：主动模式，8~25 秒出新点并居中
- 刷新间隔：右上角下拉自选/自定义，URL 参数可指定，浏览器记忆
- 状态卡：在线判定看心跳（~50s 粒度），轨迹点/电量/精度独立显示

## 丢机应急 SOP

1. **立即**：浏览器打开 `/map` → 看"心跳"确认手机在线 → 点 **[获取位置]** → 8~25 秒拿到最新位置
2. **同时**：登录 **i.mi.com** → 查找设备 → 响铃 → 锁定 → 丢失模式留言
3. 心跳失联（关机/断网）：回看最后轨迹定范围，等小米通道唤醒上线
4. 确认被盗：i.mi.com 远程擦除 → 运营商挂失 SIM → 带 IMEI 报警
5. 事后：轮换两把密钥（Vercel env + 手机 config.env）

## 安全模型

| 端点 | 鉴权 | 用途 |
|---|---|---|
| POST /api/report | DEVICE_KEY | 手机上报位置（可携带 cmd_id 销单） |
| GET /api/poll | DEVICE_KEY | 手机长轮询领命令 + 刷心跳 |
| POST/GET /api/command | ACCESS_TOKEN | 浏览器排队命令/查状态/读心跳 |
| GET /api/locations | ACCESS_TOKEN | 浏览器拉轨迹 |

- 双密钥分离：泄露一把不伤另一把；时序安全比对防计时侧信道
- RLS 全锁 + 零公开策略：anon key 泄露也读不到一个字节；service_key 永不出服务端
- 命令队列原子领取（`FOR UPDATE SKIP LOCKED`），10 分钟 TTL 自动过期

## 调优

| 旋钮 | 位置 | 默认 | 说明 |
|---|---|---|---|
| 被动间隔 | 手机 config.env `PASSIVE_MIN` | 60 分钟 | 只影响轨迹密度，不影响在线判定 |
| 长轮询挂线 | 手机 config.env `AGENT_INTERVAL` | 50 秒 | 越大越省流量，主动响应上限≈此值 |
| 地图刷新 | 网页下拉/URL `&interval=` | 15 秒 | 浏览器侧，与手机无关 |
| 数据清理 | schema.sql 末尾语句 | — | 轨迹留 90 天，命令留 30 天 |

流量估算：心跳长轮询 ~3–5MB/月 + 被动 60 分钟 ~1MB/月 + 主动忽略不计 ≈ **5MB 以内**。
电量：唤醒锁常驻为 Termux 方案平台税（约 1~3%/天），Phase A 原生 App 可消除。

## 边界（必须清楚）

| 场景 | 本系统 | 应对 |
|---|---|---|
| 手机有电有网 | ✅ 主动 8~25s + 被动轨迹 | /map |
| 手机关机/被断网 | ❌ 主动被动均失效 | 最后轨迹 + i.mi.com 等上线 |
| 被刷机/恢复出厂 | ❌ | 小米账号锁 + IMEI 报警 |

## 从 v1 升级

1. Supabase SQL Editor 重跑 `schema.sql`（幂等，不动历史数据）
2. `cd server && vercel --prod`
3. 手机重跑 `bash setup.sh`（自动启动 agent；旧 config.env 保留，新增 PASSIVE_MIN/AGENT_INTERVAL 交互补录）
4. 按 `HYPEROS-保活清单.md` §8 验收主动模式

Phase A（Kotlin App 工业版）见 `phone-app/README.md`。
