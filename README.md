# 手机精准找回系统 · Redmi Note 15 Pro

自建定位追踪系统：手机定时上报 GPS 位置 → Vercel 接收存储 → **任意浏览器**打开地图查看精确位置与轨迹。与小米/谷歌官方查找设备（Phase 0 兜底）构成三层防御。

## 架构

```
Redmi Note 15 Pro (HyperOS)
 └─ Termux + cron 每5分钟: termux-location(gps→network降级)
     + 电量 → POST /api/report (X-Device-Key 鉴权)
        │ HTTPS
        ▼
VERCEL (serverless, 零依赖 Node)
 ├─ api/report.js     上报入口: 严格校验+时序安全密钥比对
 ├─ api/locations.js  轨迹查询: 访问令牌鉴权(密钥不落浏览器)
 └─ public/map.html   /map 面板: Leaflet+OSM 实时位置/精度圈/轨迹/电量/在线状态
        │ service_role (仅存于 Vercel 环境变量)
        ▼
SUPABASE (Postgres)
 └─ locations 表: RLS开启+零公开策略, anon key泄露也读不到数据
```

## 目录

```
server/        部署到 Vercel（见下）
phone-termux/  拷入手机的 Termux 脚本（B 方案·先用）
phone-app/     Kotlin 工业版（A 方案·B 验证稳定后交付）
docs/          Phase 0 系统级兜底清单（必读）
```

## 部署步骤（约 20 分钟）

### 1. Supabase

1. supabase.com 新建项目（区域选香港/新加坡，离大陆近）
2. SQL Editor → 粘贴 `server/supabase/schema.sql` 全文 → Run
3. Settings → API → 记下 **Project URL** 和 **service_role** 密钥（⚠ 绝密，等同数据库全权）

### 2. 生成两把密钥

```bash
openssl rand -hex 32   # → DEVICE_KEY   (手机上报用)
openssl rand -hex 32   # → ACCESS_TOKEN (浏览器看地图用)
```
Windows PowerShell 替代：
```powershell
-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })
```

### 3. Vercel

```bash
npm i -g vercel
cd server
vercel link                     # 关联/创建项目
vercel env add SUPABASE_URL          # 粘贴 Project URL
vercel env add SUPABASE_SERVICE_KEY  # 粘贴 service_role
vercel env add DEVICE_KEY            # 第1把密钥
vercel env add ACCESS_TOKEN          # 第2把密钥
vercel --prod
```
部署完成后拿到 `https://xxx.vercel.app`。

### 4. 验证服务端

```bash
# 应返回 {"ok":true,...}
curl -X POST https://xxx.vercel.app/api/report \
  -H "Content-Type: application/json" \
  -H "X-Device-Key: <DEVICE_KEY>" \
  -d '{"lat":39.9042,"lng":116.4074,"accuracy":10,"battery":80,"provider":"gps","device":"test"}'

# 应返回刚才的 JSON 数组
curl "https://xxx.vercel.app/api/locations?token=<ACCESS_TOKEN>&limit=10"
```

### 5. 手机端（Termux）

1. F-Droid 安装 **Termux / Termux:API / Termux:Boot** 三件套，各打开一次
   （https://f-droid.org，⚠ 不要用 Google Play 版 Termux，已停更）
2. 把 `phone-termux/` 整个文件夹拷入手机（git clone 或 `termux-setup-storage` 后从 Download 拷贝），放到 `$HOME/phone-loc/`
3. Termux 中：
   ```bash
   cd ~/phone-loc
   bash setup.sh     # 交互式: 装依赖→写配置→注册cron→开机自启→试运行
   ```
4. 逐项完成 `HYPEROS-保活清单.md`（决定生死的一步）

### 6. 打开地图

```
https://xxx.vercel.app/map?token=<ACCESS_TOKEN>
```
建议存为浏览器书签 + 手机主页快捷方式。15 秒自动刷新，显示实时位置、精度圈、轨迹、电量、在线/失联状态。

## 丢机应急 SOP

1. **立即**：任意浏览器打开 `/map` 书签 → 看最后位置与轨迹方向（自建，最新鲜）
2. **同时**：登录 **i.mi.com** → 查找设备 → 响铃（可能就在附近）→ 锁定 → 必要时开启"丢失模式"留言
3. 手机失联（被关机/断网）：地图看最后轨迹 + i.mi.com 等设备重新上线（小米通道走短信会唤醒）
4. 确认被盗：i.mi.com 远程擦除 → 运营商挂失 SIM → 带 IMEI 报警
5. 事后：轮换 DEVICE_KEY 与 ACCESS_TOKEN（vercel env 更新 + 手机 config.env 更新）

## 安全模型

- **双密钥分离**：上报密钥与查看令牌独立，泄露一把不伤另一把
- **时序安全比对**：防计时侧信道猜测密钥
- **RLS 全锁**：Supabase 表无任何公开 policy，浏览器只接触 ACCESS_TOKEN，service_key 永不出服务端
- **输入校验**：坐标/电量/时间戳范围校验，历史补传时间窗限 30 天
- **轮换预案**：密钥改环境变量即全局生效，手机端改一行 config.env

## 调优

- 上报频率：`bash setup.sh` 重跑改 cron 间隔（1–59 分钟）；频繁定位耗电增加，5 分钟是精度/续航平衡点
- 数据膨胀：Supabase SQL Editor 定期执行 schema.sql 末尾的 90 天清理语句
- 高精度需求时段（旅途）：临时改 1 分钟间隔

## 边界（必须清楚）

| 场景 | 本系统 | 应对 |
|---|---|---|
| 手机有电有网 | ✅ 5分钟级位置+完整轨迹 | /map |
| 手机关机/被断网 | ❌ | 看最后轨迹 + i.mi.com 等上线 |
| 被刷机/恢复出厂 | ❌ 自建失效 | 小米账号锁 + IMEI 报警 |
| 电池耗尽前 | 最后位置+电量趋势可推断关机时间 | — |

Phase A（Kotlin App 工业版）将在 B 方案稳定运行后交付，见 `phone-app/README.md`。
