# 手机精准找回系统 · Redmi Note 15 Pro（v2.4 免费额度省费）

自建定位追踪系统：**被动模式**（稀疏轨迹，月流量 ~1MB）+ **主动模式**（任意浏览器点按钮，约 0.5~1 分钟出最新位置）。与小米/谷歌官方查找设备（Phase 0 兜底）构成三层防御。

## 角色速览（两个平台各干各的）

- **GitHub（书架）**：存代码 + 手机脚本，只负责"文件让人下载"。**公有仓库**让手机能直接从 `raw.githubusercontent.com` 直链安装，无需构建步骤。
- **Vercel（前台员工）**：系统心脏——接收定位、跑长轮询命令通道、托管地图页。**任何情况都省不掉**。
- **Supabase（保险柜）**：存数据，RLS 全锁。

## 架构

```
Redmi Note 15 Pro (HyperOS)
 └─ agent.sh 唯一常驻守护 (时间制被动 + 短轮询主动, 工作窗口自动睡/醒)
     ├─ 被动: 距上次≥PASSIVE_MIN(60min) → report.sh → POST /api/report
     ├─ 主动: 短轮询挂线5s+间隙25s(占空比~17%) ←─ GET /api/poll ─────┐
     │        收到[获取位置] → 立即 report.sh(带cmd_id)→销单          │
     ├─ 窗口: 仅周一~五 08:00~18:00 运行, 其外每10分钟醒来看表       │
     └─ cron 仅看门狗: watchdog.sh 每15min检查 agent, 死了拉起       │
                                                                    │
VERCEL (serverless, 零依赖 Node)                                    │
 ├─ api/report.js    上报: 校验+时序安全比对+销单+心跳               │
 ├─ api/poll.js      短轮询: 原子领取命令, wait≤5s, maxDuration 15s  │
 ├─ api/command.js   排队[获取位置]/查状态/读心跳 ◀───────────┤
 ├─ api/locations.js 轨迹查询 (ACCESS_TOKEN)                │
 └─ public/map.html  面板: 获取位置/心跳在线/任意刷新间隔/低精度过滤│
        │ service_role (仅存于 Vercel 环境变量)              │
        ▼                                                 │
SUPABASE(可与其他项目共用): phonelocation_locations(轨迹+充电+SSID) + phonelocation_commands(命令) + phonelocation_devices(心跳) + RLS全锁
```

**主动模式时延**：命令到达 0~30 秒（轮询间隙）+ GPS 锁星 5~15 秒 + 上报 1~3 秒 ≈ **室外 0.5~1 分钟**。
**被动上报由活跃循环驱动**，天然不受安卓 Doze 定时器推迟影响（这是 v2.2 的核心加固）。
**免费额度（v2.4 核心）**：Hobby 版函数内存固定 2GB 不可降，靠「短轮询占空比 ~17% + 工作日窗口」把 Fluid 用量压到 ~75 GB-Hrs/月（上限 360）。夜间/周末手机休眠不上报，地图显示"失联"属预期。

## 目录

```
server/        部署到 Vercel 的根目录（Root Directory 设为此）
phone-app/     原生 App 工业版（主推：电脑一条 bat 装配，装完手机零维护）
phone-termux/  Termux 脚本方案（备胎：无电脑装配时用）
docs/          Phase 0 系统级兜底清单（必读）
tests/         44 项逻辑自测
CHANGELOG.md   版本历史
```

## 部署流程（公有 GitHub → Vercel 自动同步，约 20 分钟）

### 1. GitHub

1. 新建**公有**仓库，推送本仓库全部内容
2. `install.sh` 的 `REPO_BASE` 已写死为 `THATSCODEGUY/PHONE-LOCATION-SCRIPT`（换仓库/改名时需同步更新）
3. `session-*.md` 已 gitignore 并移出版本控制（公开仓库卫生）

### 2. Supabase

1. supabase.com 新建项目（区域选香港/新加坡），**或复用现有项目**——本系统所有表一律 `phonelocation_` 前缀，与其他项目共存互不干扰
2. SQL Editor → 粘贴 `server/supabase/schema.sql` 全文 → Run（幂等，老库重跑即升级）
3. Settings → API → 记下 **Project URL** 和 **service_role** 密钥（⚠ 绝密；共用项目时即该项目的钥匙，能读写库里全部表）

### 3. 生成两把密钥

```bash
openssl rand -hex 32   # → DEVICE_KEY   (手机上报+领命令)
openssl rand -hex 32   # → ACCESS_TOKEN (浏览器看地图+点按钮)
```
Windows PowerShell：`-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })`

### 4. Vercel（Git 集成）

1. Vercel → Add New Project → Import 你的 GitHub 仓库
2. **项目设置 → Root Directory = `server`**（关键！）
3. Settings → Environment Variables 填 4 个：
   - `SUPABASE_URL`（Project URL）、`SUPABASE_SERVICE_KEY`（service_role）
   - `DEVICE_KEY`、`ACCESS_TOKEN`
4. Deploy，成功拿到 `https://xxx.vercel.app`
5. 以后每次 push 到 GitHub，Vercel 自动重新部署

### 5. 验证服务端

```bash
curl -X POST https://xxx.vercel.app/api/report \
  -H "Content-Type: application/json" -H "X-Device-Key: <DEVICE_KEY>" \
  -d '{"lat":39.9042,"lng":116.4074,"provider":"gps","device":"test","charging":false,"ssid":"Home"}'
curl "https://xxx.vercel.app/api/locations?token=<ACCESS_TOKEN>&limit=10"
```

### 6. 手机端（二选一）

**方案 A · 原生 App（主推，零维护）**：电脑连一次数据线，运行 `phone-app/setup-phone.bat` 一键完成 安装 → 授权(免弹窗) → 配置 → 启动 → 防卸载，此后手机端永不再操作。详见 `phone-app/README.md`。

**方案 B · Termux 脚本（备胎，无电脑时）**：F-Droid 装 Termux / Termux:API / Termux:Boot 三件套后粘一条命令：
   ```bash
   bash -c "$(curl -sSL https://raw.githubusercontent.com/THATSCODEGUY/PHONE-LOCATION-SCRIPT/main/phone-termux/install.sh)"
   ```
   装完必须逐项完成 `HYPEROS-保活清单.md`（用户空间保活，稳定性弱于方案 A）

### 7. 打开地图

```
https://xxx.vercel.app/map?token=<ACCESS_TOKEN>[&interval=30]
```

- **[获取位置]**：主动模式，8~25 秒出新点并居中
- **[隐藏低精度]**：精度 >150m 的轨迹段弱化/隐藏，轨迹清晰不毛刺
- 刷新间隔：右上角下拉自选/自定义，URL 参数可指定，浏览器记忆
- 状态卡：在线判定看心跳（~50s 粒度）；轨迹点/电量/充电/Wi-Fi SSID/精度独立显示

## 丢机应急 SOP

1. **立即**：浏览器打开 `/map` → 看"心跳"确认在线 → 点 **[获取位置]** → 8~25 秒拿到最新位置
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

- 双密钥分离 + 时序安全比对；**仓库（即使公有）不含任何密钥与数据**——钥匙只在 Vercel/Supabase 后台，数据只在 Supabase
- RLS 全锁 + 零公开策略；service_key 永不出服务端
- 共用库靠 `phonelocation_` 表前缀隔离；命令队列原子领取（`FOR UPDATE SKIP LOCKED`），10 分钟 TTL 自动过期

## 调优

| 旋钮 | 位置 | 默认 | 说明 |
|---|---|---|---|
| 被动间隔 | 手机 config.env `PASSIVE_MIN` | 60 分钟 | agent 时间制驱动，只影响轨迹密度 |
| 长轮询挂线 | 手机 config.env `AGENT_INTERVAL` | 50 秒 | 越大越省流量，主动响应上限≈此值 |
| 看门狗间隔 | `setup.sh` 注册的 cron | 15 分钟 | agent 死了自动拉起 |
| 地图刷新 | 网页下拉/URL `&interval=` | 15 秒 | 浏览器侧，与手机无关 |
| 数据清理 | schema.sql 末尾语句 | — | 轨迹留 90 天，命令留 30 天 |

流量：心跳 ~3–5MB/月 + 被动 ~1MB/月 + 主动忽略 ≈ **5MB 以内**。
电量：唤醒锁常驻为 Termux 方案平台税（约 1~3%/天），Phase A 原生 App 可消除。

## 边界（必须清楚）

| 场景 | 本系统 | 应对 |
|---|---|---|
| 手机有电有网 | ✅ 主动 8~25s + 被动轨迹 | /map |
| 手机关机/被断网 | ❌ 主动被动均失效 | 最后轨迹 + i.mi.com 等上线 |
| 被刷机/恢复出厂 | ❌ | 小米账号锁 + IMEI 报警 |

## 从旧版升级

1. Supabase SQL Editor 重跑 `schema.sql`（幂等，自动补 `charging`/`ssid` 列）
2. push GitHub → Vercel 自动部署
3. 手机重跑 `install.sh`（或手动 `bash setup.sh`）：agent 变为统一守护，cron 自动切为看门狗
4. 按 `HYPEROS-保活清单.md` §8 验收

Phase A（Kotlin App 工业版）见 `phone-app/README.md`。
