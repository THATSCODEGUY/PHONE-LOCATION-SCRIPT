# Redmi Note 15 Pro 手机精准定位找回方案

**Session ID:** ses_fcebf22d8ffemGfaVxwf4BoqhZ
**Created:** 2026/8/23 20:52:09
**Updated:** 2026/8/28 21:56:02

---

## User

给手机一个脚本，可以精准找回手机，可以在任何其他浏览器登录就可以看到丢失手机的精确位置的方法
手机是 REDMI NOTE 15 PRO 
作为本项目的落地型首席架构师、资深实干工程师兼代码与安全主审官 (Hands-on Chief Architect & Staff Auditor)的工业化方案

---

## Assistant (Plan · glm-5.3 · 22.3s)

_Thinking:_

The user wants a script to precisely locate a lost Redmi Note 15 Pro phone, viewable from any browser. This is essentially a legitimate "find my phone" use case — the user wants to recover their own lost device.

Let me think about what's appropriate here. There are legitimate approaches:
1. Built-in services: Xiaomi's Find Device (i.mi.com), Google Find My Device — these already do exactly this and work on Redmi Note 15 Pro.
2. A self-built solution: a small app/script on the phone that reports GPS location to a server, which serves a web dashboard.

I should NOT help with stalkerware — covert tracking of someone else's phone. But the framing "精准找回手机" (precisely recover my phone) is the standard anti-theft/lost-phone use case. The safe, ethical approach is to design a solution where the phone owner installs the tracking component themselves, with visibility — like a "Find My Phone" tool.

The directory is named "PHONE LOCATION SCRIPT" and it's empty (no directories/files listed). So this is a greenfield project.

I'm in Plan Mode — read-only. I need to present a plan, ask clarifying questions. The user addresses me as "hands-on chief architect" and wants an industrial solution.

Key technical reality check I should give the user:
- A pure "script" cannot silently track a phone. Android (Redmi Note 15 Pro runs Android, HyperOS) restricts background location, battery optimization, etc. Real options:
  - Option A (zero code, most reliable): Use built-in Xiaomi Find Device + Google Find My Device. Recommend as first line.
  - Option B (self-hosted): Kivy/Beacon? Actually the realistic self-built approach:
    - Phone side: an Android app (Kotlin) or a lightweight solution like Tasker/MacroDroid/Automate + HTTP request on schedule, or a Termux script (termux-location + curl) — but Termux scripts get killed by battery saver; needs termux:boot, battery optimization exemption, and is fragile.
    - Server side: a small web service (FastAPI/Node) receiving POSTs with lat/lng, storing them, serving a web page with a map (Leaflet + OpenStreetMap, no API key needed) at a URL the user can open from any browser.
  - Option C: Existing open-source apps: OwnTracks (location sharing, self-hosted), KTraker? OwnTracks is the mature answer — HTTP/MQTT mode, plus a simple web dashboard (OwnTracks Recorder). This is the industrial, low-effort path.

Accuracy: GPS (1-10m), network (50-500m). "Precise" means GPS-level and high-frequency reporting, which costs battery.

Constraints to flag honestly:
- Cannot track a phone that's powered off, factory reset, or without network. Offline finding via Bluetooth network (Google Find My Network) is only available through Google's system — a script can't replicate that.
- If the phone is already lost NOW, the fastest path is i.mi.com / google.com/android/find — no script needed.
- Covert tracking of another person's phone is not something I'll design for; the design assumes the owner sets it up on their own device beforehand.

Let me check the working directory to confirm it's empty — I'm allowed read-only commands. The directories list is empty, so probably nothing there. I can do a quick read of the directory to confirm.

I should ask clarifying questions:
1. Is the phone already lost right now (urgent recovery) or preparing for future loss (preventive setup)?
2. Self-built (Termux script + own server) vs existing open-source (OwnTracks) vs built-in services?
3. Where to host the server — do they have a server/NAS/cloud, or need a free tier (e.g., Railway/Render), or run locally?
4. Map preference: need Google Maps or is OpenStreetMap/Leaflet fine (no API key)?

Let me structure the plan:

**Phase 0: Immediate/urgent (if phone is lost now)**
- i.mi.com (Mi account must be logged in on phone), Google Find My Device, ring/lock/erase, IMEI report to police/carrier.

**Phase 1: Architecture (preventive, self-built)**
```
[Redmi Note 15 Pro]
  Termux + Termux:Boot (or minimal Kotlin app)
    termux-location -p network (fast coarse) / -p gps (precise)
    → POST /report?id=...&lat=...&lng=...&acc=...&batt=...
    → every N minutes + on boot
[Server: FastAPI + SQLite]
  POST /report  (auth via device key)
  GET /map?token=... → Leaflet map + position history + accuracy circle + battery
[Viewer: any browser]
```

Hardening: HTTPS (Caddy), token auth, rate limiting, no public write without key.

Realistic caveats: HyperOS kills background apps aggressively — need to lock Termux in recents, disable battery optimization, allow autostart. Termux approach is fragile; the app approach (small Kotlin foreground service with FusedLocationProvider) is the "industrial" answer.

Recommendation as architect: 
- Line 1 (now): built-in services.
- Line 2 (industrial custom): Kotlin foreground-service app + FastAPI server + Leaflet dashboard, or OwnTracks HTTP + OwnTracks Recorder if they want zero code on the server side.

I'll present this concisely and ask the clarifying questions via the question tool. I'm in plan mode, so questions are appropriate. Let me first quickly verify the directory is empty, then ask.

I'll keep the response in Chinese since the user wrote in Chinese.

先确认一下现状，再给方案。

**Tool: read**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT"
}
```

**Output:**
```
<path>H:\tools projects collections\PHONE LOCATION SCRIPT</path>
<type>directory</type>
<entries>


(0 entries)
</entries>
```

---

## Assistant (Plan · glm-5.3 · 196.4s)

_Thinking:_

Empty directory, greenfield project. Now let me present the architect-level plan and ask clarifying questions.

Key questions:
1. Is the phone lost RIGHT NOW or preparing for the future? This completely changes the plan (urgent: use built-in services immediately; preventive: build the system).
2. Phone side: Termux script (fragile but "script") vs proper Kotlin app (industrial) vs OwnTracks (existing open source)?
3. Server hosting: where? Local PC, free cloud (Render/Railway), VPS, or none (use built-in)?

Let me present the honest technical reality + architecture, then ask via the question tool. I should be concise but this warrants some detail as a plan. I'll write in Chinese.

目录是空的，属于从零开始的项目。在出方案前，先给你三个必须说清的架构事实（这是实干派的前提，不是免责声明）：

**技术现实（决定方案边界）**
1. **没有"纯脚本"能远程定位一部没装过任何东西的手机**。定位必须有"手机端上报"或"系统级找回服务"两条路之一，别信网上那些"输入号码就能定位"的脚本，全是骗局/木马。
2. **Redmi Note 15 Pro（HyperOS/安卓）出厂自带两条免费精准通道**：小米查找设备（i.mi.com）+ 谷歌查找设备（google.com/android/find），GPS 级精度、任意浏览器可看、可响铃/锁定/擦除。这是"现在就丢"的最优解。
3. 自建系统的瓶颈在**手机端驻留**：HyperOS 杀后台很凶，普通脚本会被掐死。工业级自建 = 手机端前台服务 App + 自有服务器 + 网页地图，或者直接用开源成熟的 OwnTracks。

**自建方案架构（预防性，配合自带服务做双保险）**
```
手机端: 定位采集(GPS/融合定位) ──HTTPS──▶ 服务端: FastAPI+SQLite
   │  Redmi Note 15 Pro                    │  POST /report (设备密钥鉴权)
   │  · 方案A: 轻量Kotlin App(前台服务)      │  GET  /map (访问令牌)
   │  · 方案B: Termux+Boot 脚本(免开发但脆)  ▼
   └─ 定时+开机自启+电池优化白名单      任意浏览器: Leaflet地图
                                         实时位置+精度圈+轨迹+电量
```

在动手前，有三个关键分叉需要你拍板：

**Tool: question**

**Input:**
```json
{
  "questions": [
    {
      "question": "手机现在的状态是什么？这决定方案走哪条路线。",
      "header": "当前状况",
      "options": [
        {
          "label": "已经丢了，急着找回",
          "description": "立即走 i.mi.com + Google 查找设备，我给你紧急找回操作清单，自建系统来不及"
        },
        {
          "label": "还没丢，想提前布防",
          "description": "构建完整自建定位系统作为双保险，方案A/B任选"
        },
        {
          "label": "想给别人手机装",
          "description": "不做隐蔽追踪他人手机（那属于监控软件），仅限自己的设备且本人知情"
        }
      ]
    },
    {
      "question": "手机端采集方案选哪个？（工业级 vs 零开发）",
      "header": "手机端方案",
      "options": [
        {
          "label": "Kotlin 前台服务 App（推荐）",
          "description": "工业级： fused GPS 持续上报、开机自启、绕不过杀后台，我写完整可编译项目"
        },
        {
          "label": "Termux 脚本（纯脚本）",
          "description": "零 App 开发： termux-location + curl 定时上报，但需锁后台+关电池优化，偶尔会被系统杀掉，胜在简单透明"
        },
        {
          "label": "OwnTracks 开源现成",
          "description": "手机装 OwnTracks App，服务端跑 OwnTracks Recorder，成熟稳定但定制性低"
        }
      ]
    },
    {
      "question": "服务端（接收位置+网页地图）部署在哪？",
      "header": "服务端部署",
      "options": [
        {
          "label": "免费云平台（推荐）",
          "description": "Render/Railway 免费档 + 自动 HTTPS，任意浏览器直接访问，零成本"
        },
        {
          "label": "自己的 VPS/NAS",
          "description": "已有服务器，Docker 一键部署，完全自主可控"
        },
        {
          "label": "家里电脑本地跑",
          "description": "仅同一局域网可看，或配合内网穿透，适合纯实验"
        }
      ]
    }
  ]
}
```

**Output:**
```
User has answered your questions: "手机现在的状态是什么？这决定方案走哪条路线。"="还没丢，想提前布防", "手机端采集方案选哪个？（工业级 vs 零开发）"="先对比1 2 3的优缺点", "服务端（接收位置+网页地图）部署在哪？"="有VERCEL + SUPABASE". You can now continue with the user's answers in mind.
```

---

## Assistant (Plan · glm-5.3 · 266.9s)

_Thinking:_

The user's answers:
1. Phone is NOT lost yet — preventive setup (提前布防). Good, we build the full system as double insurance.
2. Wants a comparison of pros/cons of options 1 (Kotlin app), 2 (Termux script), 3 (OwnTracks) before choosing.
3. Server side: they have Vercel + Supabase.

Now with Vercel + Supabase, the architecture changes nicely:
- Vercel: API routes (serverless functions) to receive location reports + serve the map page
- Supabase: Postgres table for location history + Row Level Security + auth
- Phone → HTTPS POST → Vercel API → Supabase → web dashboard on Vercel (Leaflet map)

Constraints of Vercel serverless: fine for this use case (low frequency reports).

Now I need to present the comparison of the three phone-side options, then ask which one they pick. Let me build an honest comparison table:

**方案A: Kotlin 前台服务 App（自研工业级）**
优点:
- 可靠性最高: 前台服务+WORKMANAGER/ALARMMANAGER 持续调度, fused location provider GPS 级精度 (1-10m)
- 完全掌控: 频率、触发条件（低电量降频）、断网缓存补传
- 可加反卸载（device admin）、SIM 变更告警、拍照等扩展
- 无第三方依赖
缺点:
- 需要编译 APK（Android Studio / 我给出完整项目，用户编译一次安装）
- 开发工作量最大
- 首次配置稍复杂

**方案B: Termux 脚本（纯脚本流）**
优点:
- 零 App 开发, 全部脚本化透明可见, 与"PHONE LOCATION SCRIPT"项目名最契合
- termux-location -p gps 可达 GPS 精度
- 改逻辑秒改脚本
缺点:
- HyperOS 杀后台凶: 需要 Termux 锁后台 + 关电池优化 + termux-boot 自启, 即便这样也可能被杀
- Google Play 版 Termux 已停更, 需从 F-Droid/GitHub 装, 且 Android 14+ 上 termux-boot 兼容性一般
- termux-location 拿 GPS 锁有时很慢（尤其在息屏时）, 精度和可靠性都低于 App
- 无断网补传, 被杀就断流
- 门槛: 反而要手动折腾一堆系统设置

**方案C: OwnTracks 现成 App**
优点:
- 成熟稳定, HTTP/MQTT 上报, 内置后台驻留优化
- 自带快速模式（移动时高频上报）
缺点:
- 上报格式固定 (JSON to /pub or MQTT), 与自定义 dashboard 对接要适配
- 定制性低: 网页端要么用 OwnTracks Recorder (需要常驻进程, Vercel serverless 跑不了), 要么自己写适配层
- 界面英文, 配置略绕

Recommendation given their Vercel+Supabase stack: 
- A: best reliability, most work (but I write the whole project, they just build the APK once)
- B: simplest conceptually, fits "script" theme, acceptable if user accepts fragility; could be combined: B for quick start, A as industrial upgrade
- Hybrid: 先 B 快速跑通链路 → 验证后升级 A

Actually the smart industrial recommendation: B (Termux) to validate the pipeline in 1 hour, then A as the hardened permanent solution. Or directly A.

Vercel+Supabase architecture:
```
Phone (A/B/C) --HTTPS POST--> Vercel API Route /api/report
  auth: X-Device-Key (env var, compare hash)
  → Supabase insert (locations table: ts, lat, lng, acc, batt, provider)
Dashboard: Vercel page /map
  → Supabase query last N points (anon key + RLS read policy on public map? or Supabase Auth)
  → Leaflet + OpenStreetMap tiles (no API key), polyline trail, accuracy circle, battery badge
Security: RLS, device key via env, rate limit (Vercel WAF or simple in-route)
```

Dashboard auth: simplest = Supabase Auth (email magic link) or a long random token in URL. For personal use, a signed URL token is fine. I'll propose token-in-URL (random 32+ chars) stored in Vercel env — simple and works from any browser.

I should present the comparison table, the Vercel+Supabase architecture, the phased plan (Phase 0: built-in services hardening checklist; Phase 1: infra; Phase 2: phone side; Phase 3: dashboard; Phase 4: hardening/testing incl. anti-kill checklist for HyperOS), then ask final decision on A/B/C (recommend B→A hybrid or direct A).

Keep it structured and concise. Since plan mode is on, no file edits. End with the question of which phone-side option to go with.

三个关键信息都齐了：**预防性布防 + Vercel/Supabase 基础设施**。下面是完整对比和架构方案。

## 一、手机端三方案对比（实测量级评估）

| 维度 | A. Kotlin 自研 App | B. Termux 脚本 | C. OwnTracks 现成 |
|---|---|---|---|
| **精度** | GPS/融合定位，1–10m | `termux-location -p gps`，可达 GPS 级但息屏时难锁星 | GPS 级，成熟 |
| **抗杀后台（HyperOS）** | ★★★★★ 前台服务+WorkManager+开机自启，系统级豁免 | ★★☆ 需手动锁后台+关电池优化，仍可能被杀 | ★★★★ 有驻留优化 |
| **断网补传** | ✅ Room 缓存，联网补传 | ❌ 断网即丢数据 | ✅ |
| **开发/部署成本** | 我写完整项目，你 Android Studio 编译一次装 APK（约 30 分钟） | 零开发，纯脚本，1 小时全链路跑通 | 装个 App 配 URL |
| **可定制/扩展** | ★★★★★ 换 SIM 告警、低电量降频、响铃指令全可加 | ★★★ 改脚本即改逻辑 | ★☆ 格式固定 |
| **与 Vercel/Supabase 对接** | 原生 HTTP，完全按我们的 API 设计 | 原生 curl，完全适配 | HTTP 模式可适配，Recorder 官方面板在 serverless 上**跑不了** |
| **风险** | 无（自家用） | Play 版 Termux 已停更，需 F-Droid 版；`termux:boot` 在新安卓上偶发失灵 | 英文界面，黑盒 |

**首席架构师意见**：C 排除（面阵在 Vercel 上不可用，定制受限）。**最优路径是 B→A 混合**：先用 B 一小时打通全链路验证，跑通后我把 A 的完整 Kotlin 项目给你，编译安装后作为永久驻防。两者共用同一套 API，无缝切换。

## 二、总体架构（Vercel + Supabase）

```
Redmi Note 15 Pro
 └─ 采集端(A/B) 定时(可调1~15min) POST:
    /api/report {lat,lng,acc,batt,provider} + Header: X-Device-Key
        │ HTTPS
        ▼
VERCEL (serverless)
 ├─ POST /api/report → 校验设备密钥(ENV) → 写入 Supabase
 └─ GET  /map?token=xxxx → 任意浏览器打开
        │
        ▼
SUPABASE (Postgres + RLS)
 └─ locations 表: ts, lat, lng, accuracy, battery, provider
        │
        ▼
/map 网页面板: Leaflet + OpenStreetMap(免API key)
   实时位置+精度圈+历史轨迹+电量+最后上报时间(判断手机是否还活着)
```

## 三、执行计划（待你确认手机端方案后逐项实施）

- **Phase 0 · 双保险兜底清单**：小米查找设备 + Google 查找设备的开启与验证步骤（自建系统断电/被刷机时的最后防线）
- **Phase 1 · 基础设施**：Supabase 建表 + RLS 策略；Vercel 项目 + 环境变量（设备密钥/访问令牌）+ `/api/report`
- **Phase 2 · 手机端**：B：Termux 上报脚本 + 自启动 + HyperOS 保活设置清单 →（可选升级）A：完整 Kotlin 项目
- **Phase 3 · 网页面板**：`/map` 页面，Leaflet 地图 + 轨迹 + 电量 + 设备状态
- **Phase 4 · 加固与演练**：频率/耗电调优、密钥轮换、真实"丢手机"演练（关 WiFi 走流量定位）

**Tool: question**

**Input:**
```json
{
  "questions": [
    {
      "header": "手机端路线",
      "options": [
        {
          "description": "先 Termux 脚本 1 小时跑通验证，后续我再给 Kotlin App 工业化版，两套共存共用同一 API",
          "label": "B→A 混合（推荐）"
        },
        {
          "description": "直接上工业级，一次性到位，需要你用 Android Studio 编译一次 APK",
          "label": "只要 A（Kotlin App）"
        },
        {
          "description": "只要纯脚本，接受偶尔断流的可靠性风险，后续自己再决定要不要升级",
          "label": "只要 B（Termux）"
        }
      ],
      "multiple": false,
      "question": "手机端采集方案最终拍板？"
    }
  ]
}
```

**Output:**
```
User has answered your questions: "手机端采集方案最终拍板？"="B→A 混合（推荐）". You can now continue with the user's answers in mind.
```

---

## Assistant (Plan · glm-5.3 · 19.0s)

_Thinking:_

User confirmed: B→A hybrid. Now I need to finalize the plan. In plan mode, I should present the final consolidated plan and then... actually the workflow: I present the plan, and wait for approval to implement. Let me write out the final plan concisely — the complete execution plan with file structure, deliverables, and the Phase 0 checklist. Then ask if approved to start implementation (plan mode means I stop and wait for user to approve switching to execution).

Let me define the concrete deliverables/file tree:

```
PHONE LOCATION SCRIPT/
├── README.md                    # 总览：架构图、部署手册、应急找回SOP
├── phase0-checklist.md          # 小米/谷歌查找设备开启与验证清单
├── server/                      # Vercel 项目
│   ├── package.json / vercel.json
│   ├── api/report.js            # POST 接收上报（X-Device-Key 校验→Supabase）
│   ├── map.html (或 pages)      # /map 面板（token 校验、Leaflet+OSM）
│   └── supabase/schema.sql      # 建表+RLS 策略
├── phone-termux/                # 方案 B
│   ├── report.sh                # termux-location → curl 上报
│   ├── setup.sh                 # 一键安装(cron+boot)
│   └── HYPEROS-SURVIVAL.md      # Redmi 保活设置清单
└── phone-app/                   # 方案 A（Phase 2 升级时交付）
    └── (Kotlin 项目：前台服务+WorkManager+fused location+Room缓存补传)
```

Key technical details:
- Termux: `termux-location -p gps` with timeout fallback to `-p network`, then curl POST. Cron via `crond` (termux-services) + termux:boot script.
- API: Vercel serverless function in Node.js. Rate limit basic. Device key in env.
- Map: token via URL query, validated against env; page pulls from Supabase via REST with service key server-side... actually simpler: page fetches `/api/locations?token=...` (another serverless route reading Supabase) — keeps anon keys out of browser. Good security posture.
- Supabase schema: locations(id, ts, lat, lng, accuracy, battery, provider). RLS: no public policies; all access via service role key in serverless env. Dashboard reads via `/api/locations`.
- Battery: report interval configurable, e.g., 5 min default.

Security notes: device key + map token both random 32 bytes, stored in Vercel env; HTTPS automatic.

The plan ends here — I present final plan and ask to confirm start of execution. Per plan mode, I must wait for user's go-ahead before writing files. Let me present it compactly.

拍板：**B→A 混合路线**。最终执行计划如下，确认后我按顺序落地。

## 最终方案 · 交付物清单

```
PHONE LOCATION SCRIPT/
├── README.md                  # 架构图 + 部署手册 + 丢机应急 SOP
├── docs/phase0-兜底清单.md     # 小米/谷歌查找设备开启验证步骤（自建失效时的最后防线）
├── server/                    # Vercel 项目（Node.js）
│   ├── api/report.js          # POST 上报：校验 X-Device-Key → 写 Supabase
│   ├── api/locations.js       # GET 轨迹：校验访问令牌 → 返回历史点位（密钥不落浏览器）
│   ├── map.html               # /map 面板：Leaflet+OSM 免费地图、实时位置、精度圈、轨迹、电量、最后在线时间
│   └── supabase/schema.sql    # locations 表 + RLS（无公开策略，全走 service key）
├── phone-termux/              # 方案 B · 1小时跑通
│   ├── report.sh              # termux-location(gps→network 降级) → curl 上报，含重试
│   ├── setup.sh               # 一键安装：cron 定时 + termux:boot 开机自启
│   └── HYPEROS-保活清单.md     # Redmi Note 15 Pro 锁后台/关电池优化/自启白名单（按图操作）
└── phone-app/                 # 方案 A · 验证后交付
    └── Kotlin 工程：前台服务 + WorkManager + 融合定位 + Room 断网缓存补传 + 换SIM告警
```

## 关键设计决策

- **鉴权**：设备密钥（上报）+ 独立访问令牌（看地图）双密钥，均为 32 字节随机值，存 Vercel 环境变量；浏览器端零密钥泄露面
- **降级策略**：GPS 10 秒锁不到星自动降网络定位，`provider` 字段标记，地图上区分显示精度圈
- **默认频率**：5 分钟/次（可调 1–15 分钟），平衡精度与耗电
- **在线判定**：最后上报时间 > 3 个周期即在面板标红"设备失联"

## 执行顺序

1. `server/` 全套 + Supabase schema（先有接收端）
2. `phone-termux/` 全套 + 保活清单（手机装 F-Droid 版 Termux 即跑）
3. 真机联调：手机上报 → 浏览器 `https://你的域名/map?token=...` 看到位置
4. `docs/phase0` + README 应急 SOP
5. 验证稳定后交付 `phone-app/` Kotlin 工业版

计划就绪，回复"开始"即进入实施（先从 `server/` 写起）。

---

## User

开始

---

## Assistant (Build · glm-5.3 · 121.9s)

_Thinking:_

User said "开始" (start). Time to build. Per the plan:

1. `server/` — Vercel project (Node.js): api/report.js, api/locations.js, map.html, supabase/schema.sql
2. `phone-termux/` — report.sh, setup.sh, HYPEROS-保活清单.md
3. docs/phase0 + README
4. phone-app/ Kotlin (later, after verification)

Let me create the todo list and start with the server side.

Key design decisions to implement:
- Dual auth: DEVICE_KEY (for reporting via X-Device-Key header) + ACCESS_TOKEN (for viewing map via ?token= query param)
- 32-byte random keys stored in Vercel env vars
- Supabase `locations` table with RLS enabled, no public policies — all access via service role key server-side
- Vercel serverless functions in Node.js
- map.html served at /map — with Vercel, static files in `public/` folder... Actually for Vercel, api/ folder contains serverless functions, and static files can be in the root or public/. To serve map.html at /map, I can put it in public/map.html — Vercel serves public/ files at root path, so /map.html works. For clean /map, I could add a rewrite in vercel.json. Let me include vercel.json with rewrites.

Design details:

**supabase/schema.sql**:
```sql
create table if not exists public.locations (
  id bigint generated always as identity primary key,
  ts timestamptz not null default now(),
  lat double precision not null,
  lng double precision not null,
  accuracy double precision,
  battery double precision,
  provider text,
  speed double precision,
  bearing double precision,
  altitude double precision,
  device text not null default 'primary'
);

alter table public.locations enable row level security;
-- no policies: only service_role can access

create index on public.locations (ts desc);

-- optional: retention cleanup function / view for latest
create or replace view public.latest_location as
select * from public.locations order by ts desc limit 1;
```

Views inherit RLS from base table in Postgres when using security_invoker... Actually by default views in Postgres run with the owner's rights (security definer semantics). Since the view owner is postgres (which bypasses RLS), a view could leak data if queried via anon key. But we never expose the anon key to the browser anyway — all queries go through server-side with service role. To be safe, set `security_invoker = on` on the view. That's the clean audit-grade approach.

**api/report.js**:
- POST only
- Check X-Device-Key header against env DEVICE_KEY (timing-safe compare)
- Validate lat/lng ranges, accuracy numeric, battery 0-100, provider string whitelist (gps/network/fused/passive)
- Insert into Supabase via REST (fetch to https://PROJECT.supabase.co/rest/v1/locations) with service role key
- Return 204 or {ok:true}
- Handle errors properly, don't leak internals

Use plain fetch (Node 18+ on Vercel has global fetch) — zero dependencies, no package needed. That's the staff-engineer move: no supply chain.

**api/locations.js**:
- GET with ?token= & ?limit= (default 200, max 2000) & ?since= (ISO timestamp)
- Timing-safe compare token vs ACCESS_TOKEN
- Query Supabase REST with select, order ts desc, limit — then reverse for chronological polyline
- Return JSON array

**map.html**:
- Leaflet from unpkg CDN, OSM tiles (no API key)
- Reads token from query param, polls /api/locations?token=...&limit=500 every 15s (or 30s)
- Shows latest marker with accuracy circle, battery badge, provider, time ago
- Polyline for history, auto-fit bounds on first load, "recenter" button
- Red "失联" status if last point older than 15 min (3x default 5-min period)
- All Chinese UI
- No keys embedded: only access token in URL (by design, that's the viewing credential — acceptable; mention in README that token is the secret)

**vercel.json**:
```json
{
  "rewrites": [
    { "source": "/map", "destination": "/map.html" }
  ]
}
```
And map.html in public/ folder. On Vercel, files in public/ are served statically at /. So public/map.html → /map.html, rewrite /map → /map.html works.

**phone-termux/report.sh**:
```bash
#!/data/data/com.termux/files/usr/bin/bash
# config via env file
API_BASE=...
DEVICE_KEY=...
LOC=$(termux-location -p gps -t 15) or fallback network
parse with jq (termux has jq? Need pkg install jq — setup.sh installs it)
curl POST with X-Device-Key, retry 3 times exponential backoff, cache to disk on failure
```
Actually let me write it robustly:
- get location gps timeout 15s; if lat null/empty → try network
- build JSON via jq
- curl with --retry but also on final failure append to outbox.jsonl; next run flushes outbox first
- battery via termux-battery-status

**phone-termux/setup.sh**:
- pkg install termux-api jq curl
- prompts for API_BASE and DEVICE_KEY → writes ../config or ./config.env with chmod 600
- creates cron via crontab: */5 * * * * bash $HOME/phone-loc/report.sh >> $HOME/phone-loc/report.log 2>&1
- installs termux-boot script if termux-boot installed: ~/.termux/boot/phone-loc.sh starts cron... Actually Termux:Boot runs scripts on boot; typical pattern: start crond. Termux has cronie via pkg install cronie. crond needs to be started at boot. So:
  - pkg install cronie
  - boot script: #!/data/data/com.termux/files/usr/bin/sh; termux-wake-lock; crond -n ... Actually crond runs as daemon: `crond` 
- termux-wake-lock to keep CPU awake — important for HyperOS
- Instruct to install Termux:Boot, Termux:API from F-Droid

Simpler alternative to cron: a long-running loop script with sleep, started by boot. But cron via cronie is more industrial. Hmm, but each cron invocation spawns termux-location which needs termux-api. Fine.

Actually simpler and more reliable on Termux: use cronie. Let me go with that.

**HYPEROS-保活清单.md**: step-by-step Redmi Note 15 Pro settings: 锁后台 (recent apps lock), 省电策略-无限制, 自启动, 通知使用权, 位置权限-始终允许 for Termux, termux-wake-lock, disable MIUI optimization warnings etc. Written as checklist with exact menu paths in Chinese MIUI/HyperOS naming.

**README.md**: architecture, deploy steps (supabase sql, vercel deploy, env vars, generate keys with openssl), usage, emergency SOP (phone lost: what to do — check map, then i.mi.com, google find, SIM carrier, police).

**docs/phase0-兜底清单.md**: Xiaomi Find Device + Google Find My Device enable/verify steps.

Also generate keys: instruct `openssl rand -hex 32`.

Let me also include .gitignore? Not a git repo. Keep minimal but professional.

Vercel functions: default runtime Node 18+. Use export default async function handler(req, res) with ESM. Vercel supports both; with no package.json "type", .js files are treated as... Vercel Node builder uses esm by default in modern runtime (Node 18+ supports ESM in .js? Actually Node requires .mjs or type:module). Safest: use CommonJS module.exports = async (req, res) => {} — works everywhere on Vercel. Or add package.json with type: module. I'll write package.json with {"type":"module"} — clean ESM.

Wait — if package.json with type module exists at root, api/*.js as ESM — Vercel supports that fine.

Env vars needed: SUPABASE_URL, SUPABASE_SERVICE_KEY, DEVICE_KEY, ACCESS_TOKEN.

Timing-safe compare: crypto.timingSafeEqual on buffers of hashes to avoid length leak.

Report endpoint validation:
- lat: -90..90, lng: -180..180
- accuracy: >=0 optional
- battery: 0..100 optional
- provider: one of gps/network/fused/passive
- device: string max 64, default 'primary'
- ts: if client provides (for backfill from outbox), accept ISO; validate not future >5min, not older than 30 days. Actually for outbox backfill, client ts is essential. Use header or field ts. I'll accept optional ts field.

locations endpoint:
- token check
- limit: int 1..2000 default 500
- since: ISO → filter ts > since (Supabase REST: ts=gt.<iso>)

map.html polling: every 15s fetch with since=lastTs to be light, plus full refresh button. Keep simple: fetch limit=500 initially, then poll with since. Draw polyline, marker, circle. Update status line: 在线/失联, 电量, provider, 更新于 x 分钟前. Use Chinese.

Battery in Termux: termux-battery-status returns {"percentage":.., "status":"charging"..}.

report.sh flow:
```bash
#!/data/data/com.termux/files/usr/bin/bash
set -uo pipefail
BASEDIR=$(dirname "$0")
source "$BASEDIR/config.env"  # API_BASE, DEVICE_KEY, DEVICE_NAME, INTERVAL handled by cron

log() { echo "[$(date '+%F %T')] $*"; }

# flush outbox first
OUTBOX="$BASEDIR/outbox.jsonl"
flush_outbox() {
  [ -s "$OUTBOX" ] || return 0
  local remaining=""
  while IFS= read -r line; do
    if send_json "$line"; then :; else remaining+="$line"$'\n'; fi
  done < "$OUTBOX"
  printf '%s' "$remaining" > "$OUTBOX"
}

send_json() {
  local body="$1"
  local code
  code=$(curl -sS -m 30 -w '%{http_code}' -o /dev/null \
    -X POST "$API_BASE/api/report" \
    -H "Content-Type: application/json" \
    -H "X-Device-Key: $DEVICE_KEY" \
    -d "$body")
  [ "$code" = "200" ] || [ "$code" = "204" ]
}

get_loc() {
  local provider=$1 timeout=$2
  termux-location -p "$provider" -t "$timeout" 2>/dev/null
}

loc=$(get_loc gps 15)
if ! echo "$loc" | jq -e '.latitude != null and .longitude != null and (.latitude|type)=="number"' >/dev/null 2>&1; then
  loc=$(get_loc network 10)
fi
if ! echo "$loc" | jq -e '...' ; then log "定位失败"; exit 1; fi

batt=$(termux-battery-status 2>/dev/null | jq -r '.percentage // empty')

payload=$(jq -n --argjson lat "$(echo $loc|jq .latitude)" ... {lat:..., ts: (now|todate)} ...)
```

provider map: gps→gps, network→network. speed/accuracy from termux-location: it returns latitude, longitude, altitude, accuracy, bearing, speed, elapsedRealtimeNanos, provider... Actually termux-location JSON fields: {"latitude":..,"longitude":..,"altitude":..,"accuracy":..,"bearing":..,"speed":..,"elapsedRealtimeNanos":..,"provider":"gps"}. Good.

payload build with jq:
```bash
payload=$(jq -nc --slurpfile l <(echo "$loc") --arg provider gps --arg batt "$batt" --arg device "$DEVICE_NAME" '
  ($l[0]) as $loc |
  {lat: $loc.latitude, lng: $loc.longitude,
   accuracy: ($loc.accuracy // null), speed: ($loc.speed // null),
   bearing: ($loc.bearing // null), provider: $provider, device: $device,
   battery: (if $batt == "" then null else ($batt|tonumber) end),
   ts: (now|todate)}')
```

then send; on failure append to outbox.

setup.sh:
```bash
#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
- check termux-info platform
- pkg update -y && pkg install -y termux-api jq curl cronie
- prompt read API_BASE, DEVICE_KEY, DEVICE_NAME default redmi-note15pro
- write config.env chmod 600
- mkdir -p $PREFIX/var/spool/cron/crontabs (cronie default in termux: crontab uses $PREFIX... cronie in termux: `crontab` command works, spool at $PREFIX/var/spool/cron/crontabs)
- write crontab entry */5: 
  (crontab -l 2>/dev/null | grep -v phone-loc/report.sh; echo "*/5 * * * * bash $HOME/phone-loc/report.sh >> $HOME/phone-loc/report.log 2>&1") | crontab -
- start crond: pkill crond || true; crond
- boot script if termux-boot installed: detect [ -d $HOME/.termux/boot ]
  write $HOME/.termux/boot/phone-loc.sh:
    #!/data/data/com.termux/files/usr/bin/sh
    termux-wake-lock
    crond
  chmod +x
- termux-wake-lock now
- test run report.sh once
- print next steps (hyperos checklist)
```

Note: script assumes it lives in $HOME/phone-loc. setup.sh should cd to its own dir and optionally copy itself there? Simpler: instruct user to clone/copy folder to $HOME/phone-loc. setup.sh resolves BASEDIR.

HyperOS checklist file — detailed steps:
1. 设置→应用管理→权限→Termux 位置权限: 始终允许 (设置 → 隐私保护 → 权限管理 → Termux → 位置 → 始终允许)
2. 最近任务卡片下拉锁定 Termux
3. 省电策略: 设置→应用设置→应用管理→Termux→省电策略→无限制
4. 自启动: 应用管理→Termux→自启动 开
5. 电池优化白名单 & "锁屏不清理"... Also 开发者选项 battery optimization. Also 设置→电量和性能→应用智能省电 关闭对 Termux
6. Termux:Boot 安装后打开一次 (magically needed to register)
7. termux-wake-lock (setup.sh does it) + notification 通知常驻
8. 通知权限开启 for Termux & Termux:API
9. 测试: 锁屏15分钟, 检查 /map 有无新点

phase0 doc:
- Xiaomi: 设置→小米账号→云服务→查找设备 开启; 验证: 浏览器 i.mi.com → 查找设备; SIM 保持流量, 设置关机需要锁屏密码
- Google: google.com/android/find; settings → google → security → find my device; verify. Note China ROM may lack Google services — Redmi Note 15 Pro China HyperOS: Google services may not be preinstalled. Mention fallback: 小米查找设备 is primary in China.
- Also SIM PIN set, lockscreen password, IMEI record (*#06#), carrier emergency services.

README: overview, architecture diagram (ASCII), quick start order, deploy supabase (SQL editor run schema.sql), create project, get URL + service key, generate keys `openssl rand -hex 32`, vercel deploy (needs vercel CLI: npm i -g vercel; vercel; set env; vercel deploy --prod), test with curl sample, phone setup, map URL, emergency SOP, threat model table (what system covers: phone online w/ data; what it doesn't: powered off/factory reset/boxed — then phase0 fallback), tuning interval, security notes (keys rotation).

Also api/_common? Keep two files self-contained but DRY-ish: small shared helper inline in each (fine for 2 functions). Staff move: keep it simple, no framework.

Also add api/test? No.

Vercel: functions default duration 10s fine.

map.html details:
```html
<!doctype html><html lang="zh-CN"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>设备位置 · Redmi Note 15 Pro</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style> dark UI, fullscreen map, top status bar card, bottom-right buttons </style>
</head><body>
<div id="status">…</div><div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const qs=new URLSearchParams(location.search); const token=qs.get('token')||'';
if(!token){document.body.innerHTML='<p style="padding:2em">缺少 token 参数。用法：/map?token=访问令牌</p>';throw ...}
let lastTs=null; let firstLoad=true;
const map=L.map('map').setView([35,105],4);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
let marker=null,circle=null,line=null;
async function refresh(){
  let url='/api/locations?token='+encodeURIComponent(token)+'&limit=500';
  if(lastTs) url+='&since='+encodeURIComponent(lastTs);
  const r=await fetch(url); if(!r.ok){setStatus('加载失败 HTTP '+r.status,true);return;}
  const pts=await r.json();
  if(pts.length){ if(lastTs===null&&pts.length) fitBounds; lastTs=pts[pts.length-1].ts; render(pts) } 
  else if(lastTs===null){ setStatus('暂无任何上报记录', true) }
  updateStatus(); // uses last point
}
render: build cumulative array `track` (module-level), append new pts.
```
Keep module-level `track=[]`; append; polyline setLatLngs; marker setLatLng; circle setRadius(accuracy||0).
Status: 在线 if now-lastTs < 15min else 失联(红). Show 电量%, provider, 精度±m, 更新 x 分钟前, 点数.
setInterval(refresh, 15000); refresh();
Recenter button; full reload button (track=[]; lastTs=null).
Escape: token only used server-side query — fine.

Battery display: from latest point battery if not null.

Edge: since filter — after appending, dedupe by ts? points may repeat if same ts returned (since is >, ok strictly greater, no dupes).

locations.js: order=ts.desc.limit then reverse to ascending before returning. with since: `ts=gt.${since}`.

Supabase REST insert: POST /rest/v1/locations with Prefer: return=minimal. Select query: GET /rest/v1/locations?select=ts,lat,lng,accuracy,battery,provider,speed,bearing,device&order=ts.desc&limit=500&ts=gt.ISO.

service key header: apikey + Authorization Bearer.

Also add a small `api/latest.js`? Not needed; map computes latest client-side. Skip — YAGNI.

package.json: {"name":"phone-location-server","private":true,"type":"module"} — hmm if type module, api js must use ESM import. Use `import crypto from 'node:crypto'`. Fine.

Vercel static: put map.html in public/. vercel.json rewrites /map → /map.html. Also add /api routes are functions automatically.

Security: report returns 401 invalid key; 400 bad payload with generic messages; 500 on supabase error (log server-side console.error). Rate limiting: none at this scale; mention. Add basic size limit check body length < 4KB.

Timing safe compare:
```js
function safeEq(a,b){const ha=crypto.createHash('sha256').update(String(a)).digest();const hb=crypto.createHash('sha256').update(String(b)).digest();return crypto.timingSafeEqual(ha,hb);}
```

ts validation: if provided, Date.parse ok, not more than 2min future, not older than 30d; else server now().

Write files now. Also README emergency SOP and architecture. And docs/phase0. phone-app/ — later phase per plan; create folder with a README placeholder? Plan said deliver after verification. I'll note it and skip creating now to avoid placeholder bloat... Actually a brief phone-app/README.md noting "Phase A 交付物在 Phase B 验证稳定后生成" keeps structure honest. I'll add it with the spec (not code yet). Keep short.

Let me create todos and write all files in parallel-ish sequence. Directories: server/api, server/public, server/supabase, phone-termux, docs, phone-app. mkdir -p via bash once, then write files.

Line endings: bash scripts need LF! On Windows the Write tool might write CRLF? I'll write with \n; also add note in setup to run `sed -i 's/\r$//' *.sh` if needed — actually better: termux can handle if transferred... To be safe, mention in README/phone-termux/README: 如果在 Windows 编辑过脚本，先执行 `pkg install dos2unix && dos2unix *.sh`。Good practical touch.

Cronie in Termux: crontab file location $PREFIX/var/spool/cron/crontabs/<user>. `crontab` command included with cronie. Boot: start `crond` (no systemd). In cron entry use full path bash and absolute script path ($HOME expanded at install time).

termux-location battery: termux-battery-status needs Termux:API app installed. setup.sh checks: `command -v termux-location` after pkg install termux-api; and test termux-api client with app. Add check in setup: if `termux-location -p network -t 5` fails with "API app not installed" warn user to install Termux:API from F-Droid. Keep test soft (don't abort).

Interval config: cron */5 hardcoded in setup; make setup prompt for interval minutes default 5, validate 1-15... any positive int; cron supports */N for values dividing 60 nicely; if user types e.g. 7, */7 runs at minutes 0,7,14... fine actually cron */7 = minutes divisible by 7 within hour (0,7,...56) slight drift at hour boundary — acceptable. Default 5.

Device name default: redmi-note15pro.

Now schema.sql with security_invoker view:

```sql
create table if not exists public.locations (
  id          bigint generated always as identity primary key,
  ts          timestamptz not null default now(),
  device      text not null default 'primary',
  provider    text,
  lat         double precision not null check (lat between -90 and 90),
  lng         double precision not null check (lng between -180 and 180),
  accuracy    double precision check (accuracy >= 0),
  speed       double precision,
  bearing     double precision,
  altitude    double precision,
  battery     double precision check (battery between 0 and 100)
);

alter table public.locations enable row level security;

create index if not exists locations_ts_desc_idx on public.locations (ts desc);
create index if not exists locations_device_ts_idx on public.locations (device, ts desc);

create or replace view public.v_latest_location
with (security_invoker = on) as
  select * from public.locations
  order by ts desc
  limit 1;
```

Note: no insert policy → anon can't write; all writes via service key (bypasses RLS). 

report.js full code:

```js
import crypto from 'node:crypto';

const MAX_BODY = 4096;

function safeEq(a, b) {
  const ha = crypto.createHash('sha256').update(String(a)).digest();
  const hb = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(ha, hb);
}

const PROVIDERS = new Set(['gps', 'network', 'fused', 'passive']);

function num(v) {
  if (v === null || v === undefined || v === '') return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
}

export default async function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    return res.status(405).json({ error: 'method not allowed' });
  }
  const key = process.env.DEVICE_KEY || '';
  const provided = req.headers['x-device-key'] || '';
  if (!key || !provided || !safeEq(key, provided)) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  const body = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? {});
  if (body.length > MAX_BODY) return res.status(413).json({ error: 'payload too large' });
  let p; try { p = JSON.parse(body); } catch { return res.status(400).json({ error: 'invalid json' }); }
  const lat = num(p.lat), lng = num(p.lng);
  if (lat === null || lng === null || lat < -90 || lat > 90 || lng < -180 || lng > 180)
    return res.status(400).json({ error: 'invalid coordinates' });
  const accuracy = num(p.accuracy); if (accuracy !== null && accuracy < 0) ...
  battery 0..100, speed >=0, bearing 0..360, provider whitelist (default 'gps' if absent), device: String(p.device||'primary').slice(0,64)
  let ts = new Date(); if (p.ts) { const t = Date.parse(p.ts); if (!Number.isFinite(t)) 400; ts=new Date(t); if (ts.getTime() > Date.now()+120000) 400 future; if older than 30d 400 }
  const row = { ts: ts.toISOString(), device, provider, lat, lng, accuracy, speed, bearing, altitude: num(p.altitude), battery };
  strip nulls? Supabase accepts null fine.
  const sbUrl = process.env.SUPABASE_URL; const sbKey = process.env.SUPABASE_SERVICE_KEY;
  if (!sbUrl || !sbKey) { console.error('missing env'); return res.status(500).json({ error: 'server misconfigured' }); }
  const r = await fetch(`${sbUrl}/rest/v1/locations`, { method:'POST', headers:{ 'apikey': sbKey, 'Authorization': `Bearer ${sbKey}`, 'Content-Type':'application/json', 'Prefer':'return=minimal' }, body: JSON.stringify(row) });
  if (!r.ok) { const t = await r.text(); console.error('supabase insert failed', r.status, t); return res.status(502).json({ error: 'upstream failed' }); }
  return res.status(200).json({ ok: true });
}
```
Vercel parses JSON body automatically into req.body (object) — good, handle both.

locations.js:

```js
export default async function handler(req, res) {
  no-store; method GET;
  token check vs ACCESS_TOKEN (query token) timing safe;
  limit = clamp(parseInt(req.query.limit)||500, 1, 2000);
  since = req.query.since; if present must parse to valid date & not future... allow any past date; 
  build url: `${sb}/rest/v1/locations?select=ts,device,provider,lat,lng,accuracy,speed,bearing,battery&order=ts.desc&limit=${limit}` + (since ? `&ts=gt.${encodeURIComponent(since)}` : '')
  fetch with apikey/auth
  if !ok 502
  const rows = await r.json(); rows.reverse();
  res.status(200).json(rows);
}
```

map.html — write carefully with clean dark UI. Status card top-left, buttons. ~150 lines.

Now write README with full deploy instructions including exact commands:

```
# 生成密钥（两个）
openssl rand -hex 32   → DEVICE_KEY
openssl rand -hex 32   → ACCESS_TOKEN
```
Windows alternative: PowerShell -NoProfile "[Convert]::ToHexString((1..32|ForEach-Object{Get-Random -Max 256}))" — messy; simpler: use python or https... Provide PowerShell snippet: `-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })`. That yields 64 hex chars. OK include both.

Vercel deploy steps: npm i -g vercel; cd server; vercel link; vercel env add ...; vercel --prod. Test: curl POST sample. Then map URL.

Supabase steps: new project → SQL Editor → paste schema.sql → run; Settings→API → Project URL + service_role key.

phone-termux steps: install F-Droid Termux + Termux:API + Termux:Boot (open each once); copy phone-termux folder to phone ($HOME/phone-loc) — via git clone or scp/termux storage. dos2unix. run setup.sh. Then hyperos checklist. verify map.

Emergency SOP numbered.

Threat model table.

Alright — also mention timeline "最后在线" in map header.

Time to execute: mkdir, then write files, then quick syntax check with node --check on api files (node available? env is Windows with git bash presumably; try `node --check`). I'll attempt node --check; if node missing, skip gracefully. Also `bash -n` on shell scripts to syntax check (git bash has bash).

Files:
1. server/package.json
2. server/vercel.json
3. server/api/report.js
4. server/api/locations.js
5. server/public/map.html
6. server/supabase/schema.sql
7. phone-termux/report.sh
8. phone-termux/setup.sh
9. phone-termux/HYPEROS-保活清单.md
10. docs/phase0-兜底清单.md
11. README.md
12. phone-app/README.md (spec placeholder for Phase A)

Let me now write everything. Keep code comment-light (per guidelines no comments unless needed; but shell scripts benefit from minimal log messages; keep comments sparse).

For report.sh — careful with jq. termux-location output: {"latitude":xx,...}. Build payload:

```bash
lat=$(echo "$loc" | jq -r '.latitude')
lng=$(echo "$loc" | jq -r '.longitude')
acc=$(echo "$loc" | jq -r '.accuracy // empty')
spd=$(echo "$loc" | jq -r '.speed // empty')
brg=$(echo "$loc" | jq -r '.bearing // empty')

payload=$(jq -nc \
  --argjson lat "$lat" --argjson lng "$lng" \
  ${acc:+--argjson accuracy "$acc"} ... )
```
Dynamic args get messy in bash. Simpler: build with jq reading the loc JSON directly:

```bash
payload=$(jq -nc --slurpfile l <(printf '%s' "$loc") --arg provider "$PROV" --arg device "$DEVICE_NAME" --argjson ts "$(date -u +%s)" '
  ($l[0]) as $o | {
    ts: ($ts | todateiso8601... }
```
Hmm jq todate from number: `($ts|todate)` gives ISO Z string. Server accepts ISO string ts. 

battery: `--arg batt "$batt"` then `battery: (if $batt=="" then null else ($batt|tonumber) end)`.

accuracy etc: `accuracy: ($o.accuracy // null), speed: ($o.speed // null), bearing: ($o.bearing // null)`.

process substitution <() works in termux bash. Fine. But `set -e` with jq — use set -uo pipefail only, and check payload non-empty.

Also log success to report.log via cron redirect; script itself logs minimal.

One more: GPS on Redmi needs Location toggle ON obviously; note in checklist.

setup.sh — write crontab entry with $PWD fixed path:

```bash
BASEDIR=$(cd "$(dirname "$0")" && pwd)
CRON_LINE="*/$INTERVAL * * * * bash '$BASEDIR/report.sh' >> '$BASEDIR/report.log' 2>&1"
(crontab -l 2>/dev/null | grep -v "report.sh"; echo "$CRON_LINE") | crontab -
pgrep crond >/dev/null || crond
```

Boot script:
```bash
BOOT_DIR="$HOME/.termux/boot"
mkdir -p "$BOOT_DIR"
cat > "$BOOT_DIR/phone-loc.sh" <<EOF
#!/data/data/com.termux/files/usr/bin/sh
termux-wake-lock
crond
EOF
chmod +x "$BOOT_DIR/phone-loc.sh"
```

Test run: bash "$BASEDIR/report.sh" && echo OK.

Verify crond exists in PATH after cronie install.

OK also validate config: API_BASE non-empty, DEVICE_KEY non-empty. Test connectivity: curl -s -o /dev/null -w '%{http_code}' "$API_BASE/api/report" (expect 405/401 — proves reachability). Good touch: expect not 000.

Write map.html now mentally... include:
- status card: device name, 在线/失联 dot, battery, provider, accuracy, last update ago, points count
- legend colors: gps=blue circle? Keep single accent color #4f8cff.
- auto recenter toggle (follow latest)
- buttons: 居中最新, 刷新全量, follow toggle
- polling 15s

Implement follow default true; on user drag (map.on('dragstart') set follow=false).

Time formatting: minutes ago fn.

Escape XSS not needed (no user strings inserted except provider/device from own server — still escape via textContent).

Now write the files. After writing, run `bash -n` on the two .sh (via git bash), `node --check` if node exists. Then summarize with next actions.

One more consideration: Vercel functions from `server/` folder — user runs vercel inside server/ so root = server/. vercel.json + api/ + public/ + package.json all inside. Good.

Also .vercelignore? Not needed.

For jq `-t` timeout param: termux-location -p gps -t 15 (seconds? it's actually requests timeout... `-t` is timeout in seconds per docs? Termux docs: `-t` timeout in seconds before giving up. Use 15 gps / 10 network. OK.)

interval prompt in setup: default 5.

Also add phone-termux/README? The HYPEROS checklist + README top-level covers it. Add brief phone-termux/安装说明.md? Keep: HYPEROS-保活清单.md covers phone-side; top README has install steps. Enough. Actually a short 安装说明 inside phone-termux makes the folder self-contained for transfer to phone. I'll fold install steps into top README and add note at top of setup.sh usage. Fine — fewer files, clearer.

Write everything now.

开始实施。先建目录骨架，再逐个落地交付物。

**Tool: todowrite**

**Input:**
```json
{
  "todos": [
    {
      "content": "Phase 1 · server/：Vercel 全套（API×2 + 地图面板 + Supabase schema + 配置）",
      "status": "in_progress",
      "priority": "high"
    },
    {
      "content": "Phase 2 · phone-termux/：上报脚本 + 一键安装 + HyperOS 保活清单",
      "status": "pending",
      "priority": "high"
    },
    {
      "content": "Phase 3 · 联调验证：语法检查 + 本地逻辑自测",
      "status": "pending",
      "priority": "medium"
    },
    {
      "content": "Phase 4 · 文档：README 部署手册 + 丢机应急 SOP + phase0 兜底清单",
      "status": "pending",
      "priority": "medium"
    },
    {
      "content": "Phase 5 · phone-app/ Kotlin 工业版（B 验证稳定后交付）",
      "status": "pending",
      "priority": "low"
    }
  ]
}
```

**Output:**
```
[
  {
    "content": "Phase 1 · server/：Vercel 全套（API×2 + 地图面板 + Supabase schema + 配置）",
    "status": "in_progress",
    "priority": "high"
  },
  {
    "content": "Phase 2 · phone-termux/：上报脚本 + 一键安装 + HyperOS 保活清单",
    "status": "pending",
    "priority": "high"
  },
  {
    "content": "Phase 3 · 联调验证：语法检查 + 本地逻辑自测",
    "status": "pending",
    "priority": "medium"
  },
  {
    "content": "Phase 4 · 文档：README 部署手册 + 丢机应急 SOP + phase0 兜底清单",
    "status": "pending",
    "priority": "medium"
  },
  {
    "content": "Phase 5 · phone-app/ Kotlin 工业版（B 验证稳定后交付）",
    "status": "pending",
    "priority": "low"
  }
]
```

**Tool: bash**

**Input:**
```json
{
  "command": "mkdir -p server/api server/public server/supabase phone-termux docs phone-app && ls -R",
  "description": "Creates project directory skeleton"
}
```

**Output:**
```
.:
docs
phone-app
phone-termux
server
session-ses_fceb.md

./docs:

./phone-app:

./phone-termux:

./server:
api
public
supabase

./server/api:

./server/public:

./server/supabase:

```

---

## Assistant (Build · glm-5.3 · 20.8s)

_Thinking:_

Skeleton created. Now write server files in parallel: package.json, vercel.json, schema.sql, report.js, locations.js, map.html.

骨架就绪。写 Phase 1 服务端全套（零依赖，纯 Node 内置 fetch，无供应链风险）：

**Tool: write**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\server\\package.json",
  "content": "{\n  \"name\": \"phone-location-server\",\n  \"version\": \"1.0.0\",\n  \"private\": true,\n  \"type\": \"module\",\n  \"engines\": {\n    \"node\": \">=18\"\n  }\n}\n"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\server\\vercel.json",
  "content": "{\n  \"rewrites\": [\n    { \"source\": \"/map\", \"destination\": \"/map.html\" }\n  ],\n  \"headers\": [\n    {\n      \"source\": \"/api/(.*)\",\n      \"headers\": [\n        { \"key\": \"Cache-Control\", \"value\": \"no-store\" }\n      ]\n    }\n  ]\n}\n"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\server\\supabase\\schema.sql",
  "content": "-- ============================================================\n-- 手机定位系统 · Supabase Schema\n-- 执行位置: Supabase Dashboard → SQL Editor → 粘贴全文 → Run\n-- 安全模型: RLS 开启 + 零公开策略\n--           anon/authenticated 均无任何读写权限\n--           只有服务端 service_role key(Vercel 环境变量)可读写\n-- ============================================================\n\ncreate table if not exists public.locations (\n  id        bigint generated always as identity primary key,\n  ts        timestamptz not null default now(),\n  device    text not null default 'primary',\n  provider  text,\n  lat       double precision not null check (lat  between -90  and 90),\n  lng       double precision not null check (lng  between -180 and 180),\n  accuracy  double precision check (accuracy >= 0),\n  speed     double precision,\n  bearing   double precision,\n  altitude  double precision,\n  battery   double precision check (battery between 0 and 100)\n);\n\nalter table public.locations enable row level security;\n\n-- 故意不创建任何 policy: anon key 即使泄露也读不到一个字节\n\ncreate index if not exists locations_ts_desc_idx  on public.locations (ts desc);\ncreate index if not exists locations_device_ts_idx on public.locations (device, ts desc);\n\n-- 最新位置视图(security_invoker 确保视图不绕过 RLS)\ncreate or replace view public.v_latest_location\nwith (security_invoker = on) as\n  select * from public.locations\n  order by ts desc\n  limit 1;\n\n-- 可选: 数据保留 90 天, 手动或用 pg_cron 定期执行\n-- delete from public.locations where ts < now() - interval '90 days';\n"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\server\\api\\report.js",
  "content": "import crypto from 'node:crypto';\n\nconst MAX_BODY = 4096;\nconst PROVIDERS = new Set(['gps', 'network', 'fused', 'passive']);\nconst MAX_FUTURE_MS = 2 * 60 * 1000;\nconst MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;\n\nfunction safeEq(a, b) {\n  const ha = crypto.createHash('sha256').update(String(a)).digest();\n  const hb = crypto.createHash('sha256').update(String(b)).digest();\n  return crypto.timingSafeEqual(ha, hb);\n}\n\nfunction num(v) {\n  if (v === null || v === undefined || v === '') return null;\n  const n = Number(v);\n  return Number.isFinite(n) ? n : null;\n}\n\nfunction bad(res, msg) {\n  return res.status(400).json({ error: msg });\n}\n\nexport default async function handler(req, res) {\n  res.setHeader('Cache-Control', 'no-store');\n  if (req.method !== 'POST') {\n    res.setHeader('Allow', 'POST');\n    return res.status(405).json({ error: 'method not allowed' });\n  }\n\n  const key = process.env.DEVICE_KEY || '';\n  const provided = req.headers['x-device-key'] || '';\n  if (!key || !provided || !safeEq(key, provided)) {\n    return res.status(401).json({ error: 'unauthorized' });\n  }\n\n  const raw = typeof req.body === 'string' ? req.body : JSON.stringify(req.body ?? {});\n  if (raw.length > MAX_BODY) return bad(res, 'payload too large');\n\n  let p;\n  try { p = JSON.parse(raw); } catch { return bad(res, 'invalid json'); }\n\n  const lat = num(p.lat);\n  const lng = num(p.lng);\n  if (lat === null || lng === null) return bad(res, 'invalid coordinates');\n  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return bad(res, 'invalid coordinates');\n\n  const accuracy = num(p.accuracy);\n  if (accuracy !== null && accuracy < 0) return bad(res, 'invalid accuracy');\n\n  const battery = num(p.battery);\n  if (battery !== null && (battery < 0 || battery > 100)) return bad(res, 'invalid battery');\n\n  const speed = num(p.speed);\n  if (speed !== null && speed < 0) return bad(res, 'invalid speed');\n\n  const bearing = num(p.bearing);\n  if (bearing !== null && (bearing < 0 || bearing > 360)) return bad(res, 'invalid bearing');\n\n  const provider = p.provider === undefined || p.provider === null ? 'gps' : String(p.provider);\n  if (!PROVIDERS.has(provider)) return bad(res, 'invalid provider');\n\n  const device = p.device === undefined || p.device === null ? 'primary' : String(p.device).slice(0, 64);\n\n  let tsMs = Date.now();\n  if (p.ts !== undefined && p.ts !== null && p.ts !== '') {\n    const t = Date.parse(String(p.ts));\n    if (!Number.isFinite(t)) return bad(res, 'invalid ts');\n    if (t > Date.now() + MAX_FUTURE_MS) return bad(res, 'ts in future');\n    if (t < Date.now() - MAX_AGE_MS) return bad(res, 'ts too old');\n    tsMs = t;\n  }\n\n  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\\/+$/, '');\n  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';\n  if (!sbUrl || !sbKey) {\n    console.error('report: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');\n    return res.status(500).json({ error: 'server misconfigured' });\n  }\n\n  const row = {\n    ts: new Date(tsMs).toISOString(),\n    device,\n    provider,\n    lat,\n    lng,\n    accuracy,\n    speed,\n    bearing,\n    altitude: num(p.altitude),\n    battery,\n  };\n\n  let r;\n  try {\n    r = await fetch(`${sbUrl}/rest/v1/locations`, {\n      method: 'POST',\n      headers: {\n        apikey: sbKey,\n        Authorization: `Bearer ${sbKey}`,\n        'Content-Type': 'application/json',\n        Prefer: 'return=minimal',\n      },\n      body: JSON.stringify(row),\n    });\n  } catch (e) {\n    console.error('report: supabase fetch failed', e.message);\n    return res.status(502).json({ error: 'upstream failed' });\n  }\n\n  if (!r.ok) {\n    const t = await r.text().catch(() => '');\n    console.error(`report: supabase ${r.status} ${t.slice(0, 300)}`);\n    return res.status(502).json({ error: 'upstream failed' });\n  }\n\n  return res.status(200).json({ ok: true, ts: row.ts });\n}\n"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\server\\api\\locations.js",
  "content": "import crypto from 'node:crypto';\n\nfunction safeEq(a, b) {\n  const ha = crypto.createHash('sha256').update(String(a)).digest();\n  const hb = crypto.createHash('sha256').update(String(b)).digest();\n  return crypto.timingSafeEqual(ha, hb);\n}\n\nexport default async function handler(req, res) {\n  res.setHeader('Cache-Control', 'no-store');\n  if (req.method !== 'GET') {\n    res.setHeader('Allow', 'GET');\n    return res.status(405).json({ error: 'method not allowed' });\n  }\n\n  const token = process.env.ACCESS_TOKEN || '';\n  const provided = String(req.query.token || '');\n  if (!token || !provided || !safeEq(token, provided)) {\n    return res.status(401).json({ error: 'unauthorized' });\n  }\n\n  let limit = parseInt(req.query.limit, 10);\n  if (!Number.isFinite(limit)) limit = 500;\n  limit = Math.max(1, Math.min(2000, limit));\n\n  let since = req.query.since ? String(req.query.since) : '';\n  if (since) {\n    const t = Date.parse(since);\n    if (!Number.isFinite(t)) return res.status(400).json({ error: 'invalid since' });\n    since = new Date(t).toISOString();\n  }\n\n  const sbUrl = (process.env.SUPABASE_URL || '').replace(/\\/+$/, '');\n  const sbKey = process.env.SUPABASE_SERVICE_KEY || '';\n  if (!sbUrl || !sbKey) {\n    console.error('locations: missing SUPABASE_URL / SUPABASE_SERVICE_KEY');\n    return res.status(500).json({ error: 'server misconfigured' });\n  }\n\n  const qs = new URLSearchParams({\n    select: 'ts,device,provider,lat,lng,accuracy,speed,bearing,battery',\n    order: 'ts.desc',\n    limit: String(limit),\n  });\n  if (since) qs.set('ts', `gt.${since}`);\n\n  let r;\n  try {\n    r = await fetch(`${sbUrl}/rest/v1/locations?${qs}`, {\n      headers: { apikey: sbKey, Authorization: `Bearer ${sbKey}` },\n    });\n  } catch (e) {\n    console.error('locations: supabase fetch failed', e.message);\n    return res.status(502).json({ error: 'upstream failed' });\n  }\n\n  if (!r.ok) {\n    const t = await r.text().catch(() => '');\n    console.error(`locations: supabase ${r.status} ${t.slice(0, 300)}`);\n    return res.status(502).json({ error: 'upstream failed' });\n  }\n\n  const rows = await r.json().catch(() => null);\n  if (!Array.isArray(rows)) return res.status(502).json({ error: 'upstream failed' });\n\n  rows.reverse();\n  return res.status(200).json(rows);\n}\n"
}
```

**Output:**
```
Wrote file successfully.
```

---

## Assistant (Build · glm-5.3 · 26.0s)

_Thinking:_

Now the map.html panel — the browser-facing piece. Dark UI, Leaflet, polling, status card, follow mode.

API 层完成。写浏览器端 `/map` 面板（Leaflet + OSM 免费瓦片，零 API key）：

**Tool: write**

**Input:**
```json
{
  "content": "<!doctype html>\n<html lang=\"zh-CN\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>设备位置追踪</title>\n<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\">\n<style>\n  * { box-sizing: border-box; margin: 0; padding: 0; }\n  html, body { height: 100%; background: #0f1420; font-family: -apple-system, \"PingFang SC\", \"Microsoft YaHei\", sans-serif; }\n  #map { height: 100%; }\n  .panel {\n    position: absolute; top: 12px; left: 12px; z-index: 1000;\n    background: rgba(16, 22, 36, .92); color: #e6ebf5;\n    border: 1px solid #2a3752; border-radius: 12px;\n    padding: 12px 16px; min-width: 260px; max-width: 90vw;\n    backdrop-filter: blur(6px); box-shadow: 0 4px 24px rgba(0,0,0,.4);\n    font-size: 13px; line-height: 1.7;\n  }\n  .row { display: flex; justify-content: space-between; gap: 18px; }\n  .row .k { color: #8b97ad; }\n  .row .v { color: #e6ebf5; font-variant-numeric: tabular-nums; text-align: right; }\n  .dot { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 6px; }\n  .dot.on  { background: #34d399; box-shadow: 0 0 8px #34d399; }\n  .dot.off { background: #f87171; box-shadow: 0 0 8px #f87171; }\n  .state-on  { color: #34d399; font-weight: 600; }\n  .state-off { color: #f87171; font-weight: 700; }\n  .btns { position: absolute; top: 12px; right: 12px; z-index: 1000; display: flex; flex-direction: column; gap: 8px; }\n  .btn {\n    background: rgba(16,22,36,.92); color: #cdd6e4; border: 1px solid #2a3752;\n    border-radius: 8px; padding: 8px 12px; font-size: 13px; cursor: pointer;\n  }\n  .btn:hover { border-color: #4f8cff; color: #fff; }\n  .btn.active { border-color: #4f8cff; color: #4f8cff; }\n  .footer { margin-top: 8px; padding-top: 8px; border-top: 1px solid #2a3752; color: #64748b; font-size: 11px; }\n  .err { color: #f87171; }\n</style>\n</head>\n<body>\n<div id=\"map\"></div>\n<div class=\"panel\" id=\"panel\">正在加载…</div>\n<div class=\"btns\">\n  <button class=\"btn active\" id=\"btnFollow\" title=\"自动跟随最新位置\">跟随</button>\n  <button class=\"btn\" id=\"btnCenter\" title=\"居中到最新位置\">居中</button>\n  <button class=\"btn\" id=\"btnReload\" title=\"重新拉取全部轨迹\">刷新全量</button>\n</div>\n<script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n<script>\n(function () {\n  var qs = new URLSearchParams(location.search);\n  var token = qs.get('token') || '';\n  var panel = document.getElementById('panel');\n  if (!token) {\n    panel.innerHTML = '<span class=\"err\">缺少 token 参数。<br>用法：/map?token=你的访问令牌</span>';\n    return;\n  }\n\n  var OFFLINE_MS = 15 * 60 * 1000;\n  var POLL_MS = 15000;\n\n  var map = L.map('map', { zoomControl: true }).setView([35.0, 105.0], 4);\n  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n    maxZoom: 19,\n    attribution: '&copy; OpenStreetMap'\n  }).addTo(map);\n\n  var track = [];\n  var lastTs = null;\n  var firstLoad = true;\n  var follow = true;\n  var marker = null;\n  var circle = null;\n  var polyline = L.polyline([], { color: '#4f8cff', weight: 3, opacity: .75 }).addTo(map);\n\n  map.on('dragstart', function () { setFollow(false); });\n\n  function setFollow(v) {\n    follow = v;\n    document.getElementById('btnFollow').classList.toggle('active', v);\n  }\n  document.getElementById('btnFollow').onclick = function () { setFollow(!follow); if (follow) recenter(); };\n  document.getElementById('btnCenter').onclick = recenter;\n  document.getElementById('btnReload').onclick = function () {\n    track = []; lastTs = null; firstLoad = true;\n    polyline.setLatLngs([]);\n    if (marker) { map.removeLayer(marker); marker = null; }\n    if (circle) { map.removeLayer(circle); circle = null; }\n    refresh();\n  };\n\n  function recenter() {\n    if (track.length) map.setView([track[track.length - 1].lat, track[track.length - 1].lng], Math.max(map.getZoom(), 16));\n  }\n\n  function esc(s) { var d = document.createElement('i'); d.textContent = s == null ? '' : String(s); return d.innerHTML; }\n\n  function ago(ts) {\n    var s = Math.max(0, (Date.now() - new Date(ts).getTime()) / 1000);\n    if (s < 60) return Math.round(s) + ' 秒前';\n    if (s < 3600) return Math.round(s / 60) + ' 分钟前';\n    if (s < 86400) return Math.round(s / 3600) + ' 小时前';\n    return Math.round(s / 86400) + ' 天前';\n  }\n\n  function fmtTime(ts) {\n    var d = new Date(ts);\n    function p(n) { return n < 10 ? '0' + n : n; }\n    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());\n  }\n\n  function render() {\n    if (!track.length) return;\n    var pts = track.map(function (r) { return [r.lat, r.lng]; });\n    polyline.setLatLngs(pts);\n    var last = track[track.length - 1];\n\n    if (!marker) {\n      marker = L.circleMarker([last.lat, last.lng], {\n        radius: 8, color: '#fff', weight: 2, fillColor: '#4f8cff', fillOpacity: 1\n      }).addTo(map);\n      marker.bindPopup(function () {\n        var l = track[track.length - 1];\n        return '<b>' + esc(l.device || 'primary') + '</b><br>时间: ' + esc(fmtTime(l.ts)) +\n          '<br>来源: ' + esc(l.provider || '-') + ' · 精度 ±' + esc(l.accuracy != null ? Math.round(l.accuracy) + ' m' : '未知') +\n          (l.battery != null ? '<br>电量: ' + Math.round(l.battery) + '%' : '');\n      });\n    }\n    marker.setLatLng([last.lat, last.lng]);\n\n    if (last.accuracy != null && last.accuracy > 0) {\n      if (!circle) circle = L.circle([last.lat, last.lng], {\n        radius: last.accuracy, color: '#4f8cff', weight: 1, fillOpacity: .08\n      }).addTo(map);\n      circle.setLatLng([last.lat, last.lng]).setRadius(last.accuracy);\n    }\n\n    if (firstLoad) {\n      map.fitBounds(L.latLngBounds(pts).pad(0.2));\n      firstLoad = false;\n    }\n    if (follow && !firstLoad) recenter();\n  }\n\n  function renderStatus() {\n    if (!track.length) {\n      panel.innerHTML = '暂无任何上报记录。<span class=\"err\">请确认手机端已开始上报。</span>';\n      return;\n    }\n    var l = track[track.length - 1];\n    var age = Date.now() - new Date(l.ts).getTime();\n    var online = age < OFFLINE_MS;\n    panel.innerHTML =\n      '<div class=\"row\"><span class=\"k\"><span class=\"dot ' + (online ? 'on' : 'off') + '\"></span>状态</span><span class=\"v ' + (online ? 'state-on' : 'state-off') + '\">' + (online ? '在线' : '失联 ' + ago(l.ts)) + '</span></div>' +\n      '<div class=\"row\"><span class=\"k\">设备</span><span class=\"v\">' + esc(l.device || 'primary') + '</span></div>' +\n      '<div class=\"row\"><span class=\"k\">最后上报</span><span class=\"v\">' + esc(ago(l.ts)) + '</span></div>' +\n      (l.battery != null ? '<div class=\"row\"><span class=\"k\">电量</span><span class=\"v\">' + Math.round(l.battery) + '%' + (l.battery <= 20 ? ' ⚠' : '') + '</span></div>' : '') +\n      '<div class=\"row\"><span class=\"k\">定位来源</span><span class=\"v\">' + esc(l.provider || '-') + ' · ±' + esc(l.accuracy != null ? Math.round(l.accuracy) + 'm' : '?') + '</span></div>' +\n      '<div class=\"row\"><span class=\"k\">轨迹点数</span><span class=\"v\">' + track.length + '</span></div>' +\n      '<div class=\"footer\">最后上报时间: ' + esc(fmtTime(l.ts)) + '</div>';\n  }\n\n  function renderError(msg) {\n    panel.innerHTML = '<span class=\"err\">' + esc(msg) + '</span>';\n  }\n\n  function refresh() {\n    var url = '/api/locations?token=' + encodeURIComponent(token) + '&limit=500';\n    if (lastTs) url += '&since=' + encodeURIComponent(lastTs);\n    fetch(url)\n      .then(function (r) {\n        if (r.status === 401) throw new Error('访问令牌无效');\n        if (!r.ok) throw new Error('加载失败 HTTP ' + r.status);\n        return r.json();\n      })\n      .then(function (rows) {\n        if (rows.length) {\n          for (var i = 0; i < rows.length; i++) {\n            if (!lastTs || new Date(rows[i].ts) > new Date(lastTs)) track.push(rows[i]);\n          }\n          lastTs = rows[rows.length - 1].ts;\n          render();\n        }\n        renderStatus();\n      })\n      .catch(function (e) { renderError(e.message); });\n  }\n\n  refresh();\n  setInterval(refresh, POLL_MS);\n})();\n</script>\n</body>\n</html>\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\server\\public\\map.html"
}
```

**Output:**
```
Wrote file successfully.
```

---

## Assistant (Build · glm-5.3 · 24.6s)

_Thinking:_

Server side done. Mark Phase 1 complete, start Phase 2: phone-termux files (report.sh, setup.sh, HYPEROS-保活清单.md).

Phase 1 完成。进入 Phase 2 手机端（Termux 上报脚本 + 一键安装 + 保活清单）：

**Tool: todowrite**

**Input:**
```json
{
  "todos": [
    {
      "content": "Phase 1 · server/：Vercel 全套（API×2 + 地图面板 + Supabase schema + 配置）",
      "priority": "high",
      "status": "completed"
    },
    {
      "content": "Phase 2 · phone-termux/：上报脚本 + 一键安装 + HyperOS 保活清单",
      "priority": "high",
      "status": "in_progress"
    },
    {
      "content": "Phase 3 · 联调验证：语法检查 + 本地逻辑自测",
      "priority": "medium",
      "status": "pending"
    },
    {
      "content": "Phase 4 · 文档：README 部署手册 + 丢机应急 SOP + phase0 兜底清单",
      "priority": "medium",
      "status": "pending"
    },
    {
      "content": "Phase 5 · phone-app/ Kotlin 工业版（B 验证稳定后交付）",
      "priority": "low",
      "status": "pending"
    }
  ]
}
```

**Output:**
```
[
  {
    "content": "Phase 1 · server/：Vercel 全套（API×2 + 地图面板 + Supabase schema + 配置）",
    "priority": "high",
    "status": "completed"
  },
  {
    "content": "Phase 2 · phone-termux/：上报脚本 + 一键安装 + HyperOS 保活清单",
    "priority": "high",
    "status": "in_progress"
  },
  {
    "content": "Phase 3 · 联调验证：语法检查 + 本地逻辑自测",
    "priority": "medium",
    "status": "pending"
  },
  {
    "content": "Phase 4 · 文档：README 部署手册 + 丢机应急 SOP + phase0 兜底清单",
    "priority": "medium",
    "status": "pending"
  },
  {
    "content": "Phase 5 · phone-app/ Kotlin 工业版（B 验证稳定后交付）",
    "priority": "low",
    "status": "pending"
  }
]
```

**Tool: write**

**Input:**
```json
{
  "content": "#!/data/data/com.termux/files/usr/bin/bash\n# 定位上报脚本 · Redmi Note 15 Pro (Termux)\n# 流程: GPS定位(15s) → 失败降级网络定位 → 读电量 → POST上报 → 失败落盘outbox → 下轮补传\nset -uo pipefail\n\nBASEDIR=$(cd \"$(dirname \"$0\")\" && pwd)\nCONFIG=\"$BASEDIR/config.env\"\nOUTBOX=\"$BASEDIR/outbox.jsonl\"\n[ -f \"$CONFIG\" ] || { echo \"[$(date '+%F %T')] 缺少 config.env, 请先运行 setup.sh\"; exit 1; }\n# shellcheck source=/dev/null\nsource \"$CONFIG\"\n: \"${API_BASE:?config.env 缺少 API_BASE}\"\n: \"${DEVICE_KEY:?config.env 缺少 DEVICE_KEY}\"\nDEVICE_NAME=\"${DEVICE_NAME:-redmi-note15pro}\"\n\nlog() { echo \"[$(date '+%F %T')] $*\"; }\n\nsend_json() {\n  local body=\"$1\" code\n  code=$(curl -sS -m 30 -w '%{http_code}' -o /dev/null \\\n    -X POST \"$API_BASE/api/report\" \\\n    -H \"Content-Type: application/json\" \\\n    -H \"X-Device-Key: $DEVICE_KEY\" \\\n    -d \"$body\" 2>/dev/null) || return 1\n  [ \"$code\" = \"200\" ] || [ \"$code\" = \"204\" ]\n}\n\nflush_outbox() {\n  [ -s \"$OUTBOX\" ] || return 0\n  local remaining=\"\" line\n  while IFS= read -r line; do\n    [ -n \"$line\" ] || continue\n    if send_json \"$line\"; then\n      log \"补传成功: ${line:0:80}...\"\n    else\n      remaining+=\"$line\"$'\\n'\n    fi\n  done < \"$OUTBOX\"\n  printf '%s' \"$remaining\" > \"$OUTBOX\"\n  [ ! -s \"$OUTBOX\" ]\n}\n\nvalid_loc() {\n  printf '%s' \"$1\" | jq -e '(.latitude|type)==\"number\" and (.longitude|type)==\"number\"' >/dev/null 2>&1\n}\n\nloc=$(termux-location -p gps -t 15 2>/dev/null)\nprov=\"gps\"\nif ! valid_loc \"$loc\"; then\n  log \"GPS 定位失败, 降级网络定位\"\n  loc=$(termux-location -p network -t 10 2>/dev/null)\n  prov=\"network\"\nfi\nif ! valid_loc \"$loc\"; then\n  log \"定位彻底失败 (检查: 位置开关/定位权限/Termux:API)\"\n  flush_outbox || true\n  exit 2\nfi\n\nbatt=$(termux-battery-status 2>/dev/null | jq -r '.percentage // empty')\n\npayload=$(jq -nc --slurpfile l <(printf '%s' \"$loc\") \\\n  --arg provider \"$prov\" --arg device \"$DEVICE_NAME\" --arg batt \"$batt\" \\\n  --argjson ts \"$(date -u +%s)\" '\n  ($l[0]) as $o | {\n    lat: $o.latitude,\n    lng: $o.longitude,\n    accuracy: ($o.accuracy // null),\n    speed: ($o.speed // null),\n    bearing: ($o.bearing // null),\n    provider: $provider,\n    device: $device,\n    battery: (if $batt == \"\" then null else ($batt | tonumber) end),\n    ts: ($ts | todate)\n  }')\n\n[ -n \"$payload\" ] || { log \"payload 构造失败\"; exit 3; }\n\nflush_outbox || log \"outbox 补传仍有残留, 下轮重试\"\n\nif send_json \"$payload\"; then\n  log \"上报成功 [$prov] $(printf '%s' \"$payload\" | jq -r '\"\\(.lat),\\(.lng) ±\\(.accuracy // \"?\")m 电\\(.battery // \"?\")%\"')\"\nelse\n  printf '%s\\n' \"$payload\" >> \"$OUTBOX\"\n  log \"上报失败, 已存入 outbox 待补传 ($(wc -l < \"$OUTBOX\") 条)\"\nfi\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\phone-termux\\report.sh"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "content": "#!/data/data/com.termux/files/usr/bin/bash\n# 一键安装 · 在 Termux 中执行: bash setup.sh\nset -euo pipefail\n\nBASEDIR=$(cd \"$(dirname \"$0\")\" && pwd)\nCONFIG=\"$BASEDIR/config.env\"\n\necho \"==> [1/7] 安装依赖 (termux-api jq curl cronie)\"\nyes | pkg update >/dev/null 2>&1 || true\npkg install -y termux-api jq curl cronie >/dev/null\n\necho \"==> [2/7] 检查 Termux:API 配套 App\"\nif ! termux-location -p network -t 5 >/dev/null 2>&1; then\n  echo \"    [警告] termux-location 不可用!\"\n  echo \"    请从 F-Droid 安装 Termux:API App 后重新运行本脚本\"\n  echo \"    F-Droid: https://f-droid.org/packages/com.termux.api/\"\n  exit 1\nfi\n\necho \"==> [3/7] 写入配置\"\nif [ -f \"$CONFIG\" ]; then\n  echo \"    已存在 config.env, 跳过 (如需修改请手动编辑)\"\nelse\n  read -rp \"    服务端地址 API_BASE (如 https://your-app.vercel.app): \" API_BASE\n  API_BASE=\"${API_BASE%%/}\"\n  read -rp \"    设备密钥 DEVICE_KEY: \" DEVICE_KEY\n  read -rp \"    设备名称 DEVICE_NAME [redmi-note15pro]: \" DEVICE_NAME\n  DEVICE_NAME=\"${DEVICE_NAME:-redmi-note15pro}\"\n  [ -n \"$API_BASE\" ] && [ -n \"$DEVICE_KEY\" ] || { echo \"API_BASE/DEVICE_KEY 不能为空\"; exit 1; }\n  umask 077\n  cat > \"$CONFIG\" <<EOF\nAPI_BASE='$API_BASE'\nDEVICE_KEY='$DEVICE_KEY'\nDEVICE_NAME='$DEVICE_NAME'\nEOF\nfi\n\n# shellcheck source=/dev/null\nsource \"$CONFIG\"\n\necho \"==> [4/7] 测试服务端连通性\"\ncode=$(curl -s -o /dev/null -w '%{http_code}' -m 15 \"$API_BASE/api/report\" 2>/dev/null || echo 000)\nif [ \"$code\" = \"405\" ] || [ \"$code\" = \"401\" ]; then\n  echo \"    服务端可达 (HTTP $code)\"\nelse\n  echo \"    [警告] 服务端返回 $code (期望 405/401), 请检查 API_BASE 和部署状态\"\n  read -rp \"    仍然继续? [y/N] \" yn\n  [[ \"$yn\" =~ ^[Yy]$ ]] || exit 1\nfi\n\necho \"==> [5/7] 配置定时任务 (cron)\"\nread -rp \"    上报间隔分钟 [5]: \" INTERVAL\nINTERVAL=\"${INTERVAL:-5}\"\n[[ \"$INTERVAL\" =~ ^[0-9]+$ ]] && [ \"$INTERVAL\" -ge 1 ] && [ \"$INTERVAL\" -le 59 ] || { echo \"间隔须为 1-59\"; exit 1; }\nCRON_LINE=\"*/$INTERVAL * * * * bash '$BASEDIR/report.sh' >> '$BASEDIR/report.log' 2>&1\"\n(crontab -l 2>/dev/null | grep -v \"report.sh\"; echo \"$CRON_LINE\") | crontab -\npgrep crond >/dev/null 2>&1 || crond\necho \"    已注册: $CRON_LINE\"\n\necho \"==> [6/7] 配置开机自启 (Termux:Boot)\"\nBOOT_DIR=\"$HOME/.termux/boot\"\nif [ -d \"$BOOT_DIR\" ] || [ -d \"$HOME/.termux\" ]; then\n  mkdir -p \"$BOOT_DIR\"\n  cat > \"$BOOT_DIR/phone-loc.sh\" <<'EOF'\n#!/data/data/com.termux/files/usr/bin/sh\ntermux-wake-lock\ncrond\nEOF\n  chmod +x \"$BOOT_DIR/phone-loc.sh\"\n  echo \"    已写入 ~/.termux/boot/phone-loc.sh (开机: 唤醒锁 + crond)\"\n  command -v termux-boot >/dev/null 2>&1 || \\\n  [ ! -d /data/data/com.termux.boot ] && \\\n  echo \"    [提示] 建议安装 Termux:Boot (F-Droid) 并打开一次, 开机自启才生效\"\nelse\n  echo \"    [警告] 未检测到 Termux:Boot, 重启后需手动打开 Termux 运行: crond\"\nfi\ntermux-wake-lock\necho \"    已开启唤醒锁 (termux-wake-lock)\"\n\necho \"==> [7/7] 立即试运行一次上报\"\nif bash \"$BASEDIR/report.sh\"; then\n  echo \"\"\n  echo \"==============================================\"\n  echo \" 安装完成! 浏览器打开:\"\n  echo \" $API_BASE/map?token=你的访问令牌\"\n  echo \" 下一步: 按 HYPEROS-保活清单.md 逐项设置手机\"\n  echo \" 日志: tail -f $BASEDIR/report.log\"\n  echo \"==============================================\"\nelse\n  echo \"    [警告] 试运行失败, 查看日志: $BASEDIR/report.log\"\nfi\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\phone-termux\\setup.sh"
}
```

**Output:**
```
Wrote file successfully.
```

---

## Assistant (Build · glm-5.3 · 25.5s)

_Thinking:_

Now the HyperOS keep-alive checklist and phase0 doc, then README, then phone-app placeholder spec.

写 HyperOS 保活清单（Redmi Note 15 Pro 专项）和 Phase 0 兜底文档：

**Tool: write**

**Input:**
```json
{
  "content": "# HyperOS 保活清单 · Redmi Note 15 Pro\n\n> Termux 方案的最大敌人是系统杀后台。以下每项都必须完成，缺一项就可能出现\"息屏后不再上报\"。\n> 按顺序逐项打勾。菜单名称基于 HyperOS 中文版，个别版本措辞略有差异，找不到时在设置内搜索关键词。\n\n## 0. 前置条件\n\n- [ ] 手机已开启 **位置信息**（下拉控制中心 → 位置信息 = 开）\n- [ ] 定位模式选择 **高精度**（设置 → 位置信息 → 高精度定位：GPS + 网络）\n- [ ] SIM 卡已开通流量，且 Termux 联网权限允许 **WLAN 与数据网络**（设置 → 应用管理 → Termux → 联网控制）\n- [ ] 已从 **F-Droid** 安装三件套并各打开一次：\n  - Termux、Termux:API、Termux:Boot\n  - ⚠ 必须 F-Droid 版（Google Play 版 Termux 已停更且无 API 权限）\n\n## 1. 权限设置（决定定位能否成功）\n\n- [ ] 设置 → 隐私保护 → 权限管理 → **Termux** → 位置 → **始终允许**\n  - 关闭\"仅使用期间允许\"，必须\"始终允许\"，否则息屏后拿不到定位\n- [ ] 权限管理 → Termux → **通知** → 允许（常驻通知是保活的一部分）\n- [ ] Termux:API、Termux:Boot 同样允许通知和自启动\n\n## 2. 防杀后台（决定脚本能活着）\n\n- [ ] 最近任务界面 → 找到 Termux 卡片 → **长按 → 锁定**（出现挂锁图标）\n- [ ] 设置 → 应用设置 → 应用管理 → Termux → **省电策略 → 无限制**\n- [ ] 设置 → 应用设置 → 应用管理 → Termux → **自启动 → 开**\n- [ ] 设置 → 电量与性能 → 应用智能省电 → Termux → **无限制**（若有此项）\n- [ ] 设置 → 电池 → 关闭\"锁屏后清理后台\"类选项（若有）\n- [ ] 开发者选项 → 正在运行的服务 → 确认 crond 存在（验证方法见下）\n\n## 3. 唤醒锁（防止 CPU 休眠跳过 cron）\n\n- [ ] Termux 内执行 `termux-wake-lock`，通知栏出现 \"Termux 已获取唤醒锁\" 常驻通知\n- [ ] 该命令已由 setup.sh 自动执行并写入开机脚本，这里只需确认通知存在\n\n## 4. 开机自启验证\n\n- [ ] 已安装 Termux:Boot 并**打开过一次**（必须，否则收不到开机广播）\n- [ ] 重启手机 → 不打开任何 App → 等 3 分钟 → `tail phone-loc/report.log` 应有新记录\n\n## 5. 终极验收（模拟丢失场景）\n\n- [ ] 锁屏放置 **30 分钟** → 浏览器打开 /map → 期间每 5 分钟仍有新点\n- [ ] 关闭 WiFi 只走流量 → 重复上面测试\n- [ ] 换个地方出门携带 → 地图轨迹连续移动\n\n## 6. 常见故障速查\n\n| 症状 | 原因 | 处理 |\n|---|---|---|\n| 日志\"定位彻底失败\" | 位置权限非\"始终允许\" / 位置开关关 | 清单 §1 |\n| 息屏后断更、亮屏恢复 | 省电策略未无限制 / 未锁定后台 | 清单 §2 |\n| 重启后不再上报 | Termux:Boot 未打开过 / crond 未起 | 清单 §4，手动 `crond` |\n| 每次都降级 network | 室内 GPS 信号弱 | 正常现象，到室外验证 gps |\n| \"API app not installed\" | 装了 Play 版或没装 Termux:API | F-Droid 重装三件套 |\n| outbox 持续增长 | 服务器/网络故障 | 检查 Vercel 部署与手机流量 |\n\n## 7. Windows 传输脚本后的换行符修复\n\n在 Windows 上编辑/解压过的 .sh 传到手机后若报错 `bad interpreter`，在 Termux 执行：\n\n```bash\npkg install dos2unix\ndos2unix ~/phone-loc/*.sh\n```\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\phone-termux\\HYPEROS-保活清单.md"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "content": "# Phase 0 · 系统级兜底清单（自建系统的最后防线）\n\n> 自建系统只能覆盖\"手机有电、有网、未被刷机\"的场景。\n> 以下两条**厂商级通道**是手机被锁屏关流量、被换 SIM、被擦除前的官方高精手段，必须提前开启并验证。\n> ⚠ 关键原则：**丢手机当天才去开，就来不及了。**\n\n## 1. 小米查找设备（国内行货首选，走小米云 + SIM 独立通道）\n\n### 开启（现在就做）\n- [ ] 设置 → 小米账号 → 云服务 → **查找设备 → 开启**\n- [ ] 同页打开 **\"离线查找\"**（借助其他小米设备蓝牙众包网络定位）\n- [ ] 设置 → 密码与安全 → 确认已设置**锁屏密码**（无锁屏密码=丢失后门户大开）\n- [ ] 设置 → 密码与安全 → 系统 → **关机需密码/验证**（若有此选项；防止捡到者直接关机断联）\n\n### 验证（必须做一次）\n- [ ] 任意电脑浏览器登录 **i.mi.com** → 查找设备 → 能看到本机当前定位\n- [ ] 试一次\"响铃\"功能（手机会最大音量响，即使静音状态）\n- [ ] 记住：此功能走的是手机流量+短信通道，WiFi 被关也能用\n\n## 2. Google 查找设备（若已安装 Google 服务框架）\n\n- [ ] google.com/android/find → 登录手机上的 Google 账号 → 确认本机可见\n- [ ] 设置 → Google → 安全 → 查找我的设备 → 开启\n- [ ] 国内行货若未预装 GMS 则跳过本节，以小米通道为准\n\n## 3. SIM 与身份信息（报警/补卡要用）\n\n- [ ] 设置 **SIM 卡 PIN 码**（防止 SIM 被插到别的手机收验证码）\n  - 设置 → 密码与安全 → 系统安全 → SIM 卡锁定\n  - ⚠ PIN 码记入密码管理器，连错 3 次会锁卡需 PUK\n- [ ] 拨 `*#06#` 记录 **IMEI**（15 位），存到云笔记/家人手机\n- [ ] 记录运营商客服号：移动 10086 / 联通 10010 / 电信 10000（挂失补卡用）\n\n## 4. 三层防御体系全景\n\n| 层级 | 手段 | 覆盖场景 | 失效场景 |\n|---|---|---|---|\n| L1 自建系统 | Termux/App + 本项目 | 有电有网，5 分钟级轨迹，**含历史轨迹** | 被刷机/关机/断网 |\n| L2 小米云 | i.mi.com 查找设备 | 关流量走短信，可响铃/锁定/擦除 | 被刷机且退出账号 |\n| L3 线下 | IMEI + 运营商 + 报警 | 最终法律途径 | — |\n\n**每季度演练一次**：模拟丢失（关 WiFi 走流量）→ 开 /map 看位置 → 登 i.mi.com 响铃 → 记录各环节耗时。\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\docs\\phase0-兜底清单.md"
}
```

**Output:**
```
Wrote file successfully.
```

---

## Assistant (Build · glm-5.3 · 27.2s)

_Thinking:_

Now README.md (main deploy manual) and phone-app/README.md (Phase A spec placeholder). Then Phase 3 verification.

写总 README（部署手册 + 应急 SOP）和 phone-app 规格占位：

**Tool: write**

**Input:**
```json
{
  "content": "# 手机精准找回系统 · Redmi Note 15 Pro\n\n自建定位追踪系统：手机定时上报 GPS 位置 → Vercel 接收存储 → **任意浏览器**打开地图查看精确位置与轨迹。与小米/谷歌官方查找设备（Phase 0 兜底）构成三层防御。\n\n## 架构\n\n```\nRedmi Note 15 Pro (HyperOS)\n └─ Termux + cron 每5分钟: termux-location(gps→network降级)\n     + 电量 → POST /api/report (X-Device-Key 鉴权)\n        │ HTTPS\n        ▼\nVERCEL (serverless, 零依赖 Node)\n ├─ api/report.js     上报入口: 严格校验+时序安全密钥比对\n ├─ api/locations.js  轨迹查询: 访问令牌鉴权(密钥不落浏览器)\n └─ public/map.html   /map 面板: Leaflet+OSM 实时位置/精度圈/轨迹/电量/在线状态\n        │ service_role (仅存于 Vercel 环境变量)\n        ▼\nSUPABASE (Postgres)\n └─ locations 表: RLS开启+零公开策略, anon key泄露也读不到数据\n```\n\n## 目录\n\n```\nserver/        部署到 Vercel（见下）\nphone-termux/  拷入手机的 Termux 脚本（B 方案·先用）\nphone-app/     Kotlin 工业版（A 方案·B 验证稳定后交付）\ndocs/          Phase 0 系统级兜底清单（必读）\n```\n\n## 部署步骤（约 20 分钟）\n\n### 1. Supabase\n\n1. supabase.com 新建项目（区域选香港/新加坡，离大陆近）\n2. SQL Editor → 粘贴 `server/supabase/schema.sql` 全文 → Run\n3. Settings → API → 记下 **Project URL** 和 **service_role** 密钥（⚠ 绝密，等同数据库全权）\n\n### 2. 生成两把密钥\n\n```bash\nopenssl rand -hex 32   # → DEVICE_KEY   (手机上报用)\nopenssl rand -hex 32   # → ACCESS_TOKEN (浏览器看地图用)\n```\nWindows PowerShell 替代：\n```powershell\n-join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Max 16) })\n```\n\n### 3. Vercel\n\n```bash\nnpm i -g vercel\ncd server\nvercel link                     # 关联/创建项目\nvercel env add SUPABASE_URL          # 粘贴 Project URL\nvercel env add SUPABASE_SERVICE_KEY  # 粘贴 service_role\nvercel env add DEVICE_KEY            # 第1把密钥\nvercel env add ACCESS_TOKEN          # 第2把密钥\nvercel --prod\n```\n部署完成后拿到 `https://xxx.vercel.app`。\n\n### 4. 验证服务端\n\n```bash\n# 应返回 {\"ok\":true,...}\ncurl -X POST https://xxx.vercel.app/api/report \\\n  -H \"Content-Type: application/json\" \\\n  -H \"X-Device-Key: <DEVICE_KEY>\" \\\n  -d '{\"lat\":39.9042,\"lng\":116.4074,\"accuracy\":10,\"battery\":80,\"provider\":\"gps\",\"device\":\"test\"}'\n\n# 应返回刚才的 JSON 数组\ncurl \"https://xxx.vercel.app/api/locations?token=<ACCESS_TOKEN>&limit=10\"\n```\n\n### 5. 手机端（Termux）\n\n1. F-Droid 安装 **Termux / Termux:API / Termux:Boot** 三件套，各打开一次\n   （https://f-droid.org，⚠ 不要用 Google Play 版 Termux，已停更）\n2. 把 `phone-termux/` 整个文件夹拷入手机（git clone 或 `termux-setup-storage` 后从 Download 拷贝），放到 `$HOME/phone-loc/`\n3. Termux 中：\n   ```bash\n   cd ~/phone-loc\n   bash setup.sh     # 交互式: 装依赖→写配置→注册cron→开机自启→试运行\n   ```\n4. 逐项完成 `HYPEROS-保活清单.md`（决定生死的一步）\n\n### 6. 打开地图\n\n```\nhttps://xxx.vercel.app/map?token=<ACCESS_TOKEN>\n```\n建议存为浏览器书签 + 手机主页快捷方式。15 秒自动刷新，显示实时位置、精度圈、轨迹、电量、在线/失联状态。\n\n## 丢机应急 SOP\n\n1. **立即**：任意浏览器打开 `/map` 书签 → 看最后位置与轨迹方向（自建，最新鲜）\n2. **同时**：登录 **i.mi.com** → 查找设备 → 响铃（可能就在附近）→ 锁定 → 必要时开启\"丢失模式\"留言\n3. 手机失联（被关机/断网）：地图看最后轨迹 + i.mi.com 等设备重新上线（小米通道走短信会唤醒）\n4. 确认被盗：i.mi.com 远程擦除 → 运营商挂失 SIM → 带 IMEI 报警\n5. 事后：轮换 DEVICE_KEY 与 ACCESS_TOKEN（vercel env 更新 + 手机 config.env 更新）\n\n## 安全模型\n\n- **双密钥分离**：上报密钥与查看令牌独立，泄露一把不伤另一把\n- **时序安全比对**：防计时侧信道猜测密钥\n- **RLS 全锁**：Supabase 表无任何公开 policy，浏览器只接触 ACCESS_TOKEN，service_key 永不出服务端\n- **输入校验**：坐标/电量/时间戳范围校验，历史补传时间窗限 30 天\n- **轮换预案**：密钥改环境变量即全局生效，手机端改一行 config.env\n\n## 调优\n\n- 上报频率：`bash setup.sh` 重跑改 cron 间隔（1–59 分钟）；频繁定位耗电增加，5 分钟是精度/续航平衡点\n- 数据膨胀：Supabase SQL Editor 定期执行 schema.sql 末尾的 90 天清理语句\n- 高精度需求时段（旅途）：临时改 1 分钟间隔\n\n## 边界（必须清楚）\n\n| 场景 | 本系统 | 应对 |\n|---|---|---|\n| 手机有电有网 | ✅ 5分钟级位置+完整轨迹 | /map |\n| 手机关机/被断网 | ❌ | 看最后轨迹 + i.mi.com 等上线 |\n| 被刷机/恢复出厂 | ❌ 自建失效 | 小米账号锁 + IMEI 报警 |\n| 电池耗尽前 | 最后位置+电量趋势可推断关机时间 | — |\n\nPhase A（Kotlin App 工业版）将在 B 方案稳定运行后交付，见 `phone-app/README.md`。\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\README.md"
}
```

**Output:**
```
Wrote file successfully.
```

**Tool: write**

**Input:**
```json
{
  "content": "# Phase A · Kotlin 工业版驻留 App（规格）\n\n> 交付条件：phone-termux（B 方案）已稳定运行 ≥ 1 周，确认服务端链路无问题后，本目录生成完整可编译工程。B 方案与 A 方案共用同一套 API，可无缝切换/并存。\n\n## 与 Termux 版的差异（为什么要升级）\n\n| 项 | Termux (B) | Kotlin App (A) |\n|---|---|---|\n| 驻留 | 依赖用户保活设置 | 前台服务 + WorkManager 双保险，系统级 |\n| 定位 | termux-location 单点 | FusedLocationProvider 融合定位 |\n| 断网 | 落盘 outbox 补传 | Room 数据库 + 自动补传 |\n| 电量策略 | 无 | 低电量自动降频(5min→30min)，充电恢复 |\n| 响铃指令 | 无 | 服务端下发指令，手机最大音量响铃 |\n| 换 SIM | 无 | 检测 SIM 变更上报，附带新号码 |\n\n## 工程范围（届时生成）\n\n- Kotlin + minSdk 29，目标 Redmi Note 15 Pro (HyperOS)\n- 前台常驻服务（通知栏）+ WorkManager 周期定位双通道\n- 断网 Room 缓存补传，时间窗 30 天对齐服务端校验\n- 开机自启（RECEIVE_BOOT_COMPLETED + 前台服务）\n- 深度电池优化引导页（引导用户白名单，等于内置保活清单）\n- 构建：Android Studio 打开即编译，无第三方密钥依赖\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\phone-app\\README.md"
}
```

**Output:**
```
Wrote file successfully.
```

---

## Assistant (Build · glm-5.3 · 7.8s)

_Thinking:_

Phase 2 & 4 done. Now Phase 3: verification. Run syntax checks:
1. bash -n on the two shell scripts
2. node --check on the two API files (if node available)
3. jq parse test of the payload construction logic locally (if jq available) — simulate with sample termux-location JSON
4. Validate map.html structure (basic — can't run browser; skip or do a quick node check? It's plain HTML/JS without modules; can't node --check. Eyeball.)

Let me run bash -n and node --check in parallel.

进入 Phase 3 联调验证——对全部代码做语法检查：

**Tool: bash**

**Input:**
```json
{
  "command": "bash -n phone-termux/report.sh && bash -n phone-termux/setup.sh && echo \"SHELL SYNTAX OK\"",
  "description": "Syntax-checks both shell scripts"
}
```

**Output:**
```
SHELL SYNTAX OK

```

**Tool: bash**

**Input:**
```json
{
  "command": "node --check server/api/report.js && node --check server/api/locations.js && echo \"NODE SYNTAX OK\" || echo \"node not available or check failed\"",
  "description": "Syntax-checks both Vercel API files"
}
```

**Output:**
```
NODE SYNTAX OK

```

---

## Assistant (Build · glm-5.3 · 19.8s)

_Thinking:_

Both pass. Now deeper logic tests:

1. Test the jq payload construction locally — simulate termux-location output and run the exact jq command from report.sh (if jq is available locally).
2. Test the report.js handler logic offline — mock req/res? Quick approach: extract validation logic via a small inline node test that imports handler with mocked req/res and stubbed fetch. That's doable: set env vars, mock global fetch to return {ok:true}, craft req object {method:'POST', headers, body}, res stub with status/json capturing. Let me write a quick throwaway test script and run it.

Also test locations.js similarly.

语法全过。再做逻辑级自测——用 mock 请求验证两个 API 的鉴权/校验/入库链路，以及 jq 报文构造：

**Tool: bash**

**Input:**
```json
{
  "command": "jq --version 2>/dev/null && printf '%s' '{\"latitude\":39.9042,\"longitude\":116.4074,\"accuracy\":12.5,\"speed\":1.2,\"bearing\":90.0,\"altitude\":50}' | jq -nc --slurpfile l /dev/stdin --arg provider \"gps\" --arg device \"redmi-note15pro\" --arg batt \"83\" --argjson ts \"1700000000\" '($l[0]) as $o | {lat: $o.latitude, lng: $o.longitude, accuracy: ($o.accuracy // null), speed: ($o.speed // null), bearing: ($o.bearing // null), provider: $provider, device: $device, battery: (if $batt == \"\" then null else ($batt | tonumber) end), ts: ($ts | todate)}' || echo \"jq not available locally, skip\"",
  "description": "Tests jq payload construction with sample location"
}
```

**Output:**
```
jq not available locally, skip

```

**Tool: write**

**Input:**
```json
{
  "content": "import handler from '../server/api/report.js';\n\nconst sleep = (ms) => new Promise(r => setTimeout(r, ms));\n\nfunction mockRes() {\n  return {\n    code: 0, body: null,\n    setHeader() {}, \n    status(c) { this.code = c; return this; },\n    json(j) { this.body = j; return this; },\n  };\n}\n\nfunction mockReq(method, headers, body) {\n  return { method, headers, body, query: {} };\n}\n\nlet pass = 0, fail = 0;\nfunction check(name, cond, extra = '') {\n  if (cond) { pass++; console.log(`  PASS  ${name}`); }\n  else { fail++; console.log(`  FAIL  ${name} ${extra}`); }\n}\n\n// stub supabase fetch\nlet lastFetch = null;\nglobalThis.fetch = async (url, opts) => {\n  lastFetch = { url, opts };\n  return { ok: true, status: 201, text: async () => '' };\n};\n\nprocess.env.DEVICE_KEY = 'dk_123456';\nprocess.env.ACCESS_TOKEN = 'at_654321';\nprocess.env.SUPABASE_URL = 'https://fake.supabase.co';\nprocess.env.SUPABASE_SERVICE_KEY = 'svc_fake';\n\nconst goodBody = { lat: 39.9042, lng: 116.4074, accuracy: 12, battery: 80, provider: 'gps', device: 'test' };\n\nconsole.log('report.js tests:');\n\n// T1 wrong method\nlet r = mockRes();\nawait handler(mockReq('GET', {}), r);\ncheck('GET rejected 405', r.code === 405);\n\n// T2 no key\nr = mockRes();\nawait handler(mockReq('POST', {}, goodBody), r);\ncheck('missing key 401', r.code === 401);\n\n// T3 wrong key\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'wrong' }, goodBody), r);\ncheck('wrong key 401', r.code === 401);\n\n// T4 good key + good body\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, goodBody), r);\ncheck('valid report 200', r.code === 200, `got ${r.code} ${JSON.stringify(r.body)}`);\ncheck('row written to supabase', lastFetch && lastFetch.url.includes('/rest/v1/locations'));\nconst rowSent = JSON.parse(lastFetch.opts.body);\ncheck('row fields mapped', rowSent.lat === 39.9042 && rowSent.provider === 'gps' && rowSent.battery === 80);\n\n// T5 invalid coords\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, lat: 999 }), r);\ncheck('lat 999 rejected 400', r.code === 400);\n\n// T6 invalid battery\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, battery: 150 }), r);\ncheck('battery 150 rejected 400', r.code === 400);\n\n// T7 bad provider\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, provider: 'xyz' }), r);\ncheck('bad provider rejected 400', r.code === 400);\n\n// T8 future ts\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, ts: new Date(Date.now() + 3600_000).toISOString() }), r);\ncheck('future ts rejected 400', r.code === 400);\n\n// T9 backfill ts accepted\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, { ...goodBody, ts: new Date(Date.now() - 3600_000).toISOString() }), r);\ncheck('1h-ago ts accepted 200', r.code === 200);\ncheck('client ts preserved', JSON.parse(lastFetch.opts.body).ts.includes('T'));\n\n// T10 malformed json\nr = mockRes();\nawait handler(mockReq('POST', { 'x-device-key': 'dk_123456' }, '{bad json'), r);\ncheck('malformed json 400', r.code === 400);\n\n// locations.js tests\nconst locHandler = (await import('../server/api/locations.js')).default;\nlet fetchArgs = null;\nglobalThis.fetch = async (url, opts) => {\n  fetchArgs = { url, opts };\n  return { ok: true, status: 200, json: async () => [{ ts: '2024-01-01T00:00:02Z', lat: 1, lng: 2 }, { ts: '2024-01-01T00:00:01Z', lat: 1, lng: 2 }] };\n};\n\nconsole.log('locations.js tests:');\n\n// L1 no token\nr = mockRes();\nawait locHandler({ method: 'GET', query: {} }, r);\ncheck('missing token 401', r.code === 401);\n\n// L2 wrong token\nr = mockRes();\nawait locHandler({ method: 'GET', query: { token: 'nope' } }, r);\ncheck('wrong token 401', r.code === 401);\n\n// L3 valid\nr = mockRes();\nawait locHandler({ method: 'GET', query: { token: 'at_654321', limit: '10' } }, r);\ncheck('valid token 200', r.code === 200);\ncheck('desc rows reversed to asc', r.body[0].ts < r.body[1].ts);\ncheck('select fields present', 'select=ts,device,provider' in fetchArgs.url);\ncheck('limit applied', 'limit=10' in fetchArgs.url);\n\n// L4 since filter\nr = mockRes();\nawait locHandler({ method: 'GET', query: { token: 'at_654321', since: '2024-01-01T00:00:00Z' } }, r);\ncheck('since filter encoded', 'ts=gt.2024-01-01T00' in fetchArgs.url);\n\n// L5 bad since\nr = mockRes();\nawait locHandler({ method: 'GET', query: { token: 'at_654321', since: 'not-a-date' } }, r);\ncheck('bad since 400', r.code === 400);\n\n// L6 limit clamped\nr = mockRes();\nawait locHandler({ method: 'GET', query: { token: 'at_654321', limit: '99999' } }, r);\ncheck('limit clamped to 2000', 'limit=2000' in fetchArgs.url);\n\nconsole.log(`\\n${pass} passed, ${fail} failed`);\nprocess.exit(fail ? 1 : 0);\n",
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\tests\\api-test.mjs"
}
```

**Output:**
```
Wrote file successfully.
```

---

## Assistant (Build · glm-5.3 · 3.9s)

_Thinking:_

jq not available locally — skip, the syntax is standard jq and the pattern was verified mentally. Note: in report.sh I use `--slurpfile l <(printf ...)` — process substitution. On Termux bash this works.

Wait, there's one issue in my local jq test: `--slurpfile l /dev/stdin` with pipe — I piped printf into jq's stdin while also slurping /dev/stdin; that pattern is different from report.sh's process substitution. Doesn't matter, skipped.

Now run the node API test.

jq 本机没有（脚本内语法已按标准 jq 核对，Termux 端由 setup.sh 安装）。跑 API 逻辑自测：

**Tool: bash**

**Input:**
```json
{
  "command": "node tests/api-test.mjs",
  "description": "Runs mocked API integration tests"
}
```

**Output:**
```
report.js tests:
  PASS  GET rejected 405
  PASS  missing key 401
  PASS  wrong key 401
  PASS  valid report 200
  PASS  row written to supabase
  PASS  row fields mapped
  PASS  lat 999 rejected 400
  PASS  battery 150 rejected 400
  PASS  bad provider rejected 400
  PASS  future ts rejected 400
  PASS  1h-ago ts accepted 200
  PASS  client ts preserved
  PASS  malformed json 400
locations.js tests:
  PASS  missing token 401
  PASS  wrong token 401
  PASS  valid token 200
  PASS  desc rows reversed to asc
file:///H:/tools%20projects%20collections/PHONE%20LOCATION%20SCRIPT/tests/api-test.mjs:119
check('select fields present', 'select=ts,device,provider' in fetchArgs.url);
                                                           ^

TypeError: Cannot use 'in' operator to search for 'select=ts,device,provider' in https://fake.supabase.co/rest/v1/locations?select=ts%2Cdevice%2Cprovider%2Clat%2Clng%2Caccuracy%2Cspeed%2Cbearing%2Cbattery&order=ts.desc&limit=10
    at file:///H:/tools%20projects%20collections/PHONE%20LOCATION%20SCRIPT/tests/api-test.mjs:119:60

Node.js v24.11.1

```

---

## Assistant (Build · glm-5.3 · 15.1s)

_Thinking:_

Test script bug: URLSearchParams encodes commas as %2C, so my `in` check on raw string failed with TypeError (because `in` operator is for objects, not strings — my mistake using `in` on a string). Fix the test to use `.includes()` and check encoded form. The API code itself is correct — the URL shows exactly the right query params (select encoded, order, limit).

Fix test assertions: use includes with encoded string or decodeURIComponent(fetchArgs.url).

16/16 逻辑全对——报错是**测试脚本自身的 bug**（对字符串误用 `in` 运算符），URL 输出显示 API 实际构造完全正确。修正测试断言后重跑：

**Tool: edit**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\tests\\api-test.mjs",
  "oldString": "check('select fields present', 'select=ts,device,provider' in fetchArgs.url);\ncheck('limit applied', 'limit=10' in fetchArgs.url);",
  "newString": "const decodedUrl = decodeURIComponent(fetchArgs.url);\ncheck('select fields present', decodedUrl.includes('select=ts,device,provider'));\ncheck('limit applied', decodedUrl.includes('limit=10'));"
}
```

**Output:**
```
Edit applied successfully.
```

**Tool: edit**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\tests\\api-test.mjs",
  "oldString": "check('since filter encoded', 'ts=gt.2024-01-01T00' in fetchArgs.url);",
  "newString": "check('since filter encoded', decodeURIComponent(fetchArgs.url).includes('ts=gt.2024-01-01T00'));"
}
```

**Output:**
```
Edit applied successfully.
```

**Tool: edit**

**Input:**
```json
{
  "filePath": "H:\\tools projects collections\\PHONE LOCATION SCRIPT\\tests\\api-test.mjs",
  "oldString": "check('limit clamped to 2000', 'limit=2000' in fetchArgs.url);",
  "newString": "check('limit clamped to 2000', decodeURIComponent(fetchArgs.url).includes('limit=2000'));"
}
```

**Output:**
```
Edit applied successfully.
```

---

## Assistant (Build · glm-5.3 · 1.9s)

**Tool: bash**

**Input:**
```json
{
  "command": "node tests/api-test.mjs",
  "description": "Reruns API integration tests"
}
```

**Output:**
```
report.js tests:
  PASS  GET rejected 405
  PASS  missing key 401
  PASS  wrong key 401
  PASS  valid report 200
  PASS  row written to supabase
  PASS  row fields mapped
  PASS  lat 999 rejected 400
  PASS  battery 150 rejected 400
  PASS  bad provider rejected 400
  PASS  future ts rejected 400
  PASS  1h-ago ts accepted 200
  PASS  client ts preserved
  PASS  malformed json 400
locations.js tests:
  PASS  missing token 401
  PASS  wrong token 401
  PASS  valid token 200
  PASS  desc rows reversed to asc
  PASS  select fields present
  PASS  limit applied
  PASS  since filter encoded
  PASS  bad since 400
  PASS  limit clamped to 2000

22 passed, 0 failed

```

---

## Assistant (Build · glm-5.3 · 8.2s)

_Thinking:_

22/22 all pass. Now let me also do a real local end-to-end simulation: start a tiny node http server that mounts the two handlers + serves map.html, then curl POST a report (with mocked supabase? no — real end-to-end requires Supabase). The unit tests already covered handler logic with mocked Supabase; real deployment E2E requires user's Supabase/Vercel accounts, which is the user's step.

One more check: verify final directory structure and line-endings of shell scripts (should be LF for Termux; Write tool on Windows — check with file/od). Let me check for CRLF in the .sh files.

22/22 全过。最后检查 .sh 文件的换行符（Windows 环境写入需确认是 LF，否则 Termux 里跑不起来）和整体交付结构：

**Tool: bash**

**Input:**
```json
{
  "command": "file phone-termux/*.sh server/api/*.js 2>/dev/null; od -c phone-termux/report.sh | grep -c '\\\\r' || echo \"0 CR bytes (LF-only) in report.sh\"; od -c phone-termux/setup.sh | grep -c '\\\\r' || echo \"0 CR bytes (LF-only) in setup.sh\"",
  "description": "Checks line endings of shell scripts"
}
```

**Output:**
```
phone-termux/report.sh:  a /data/data/com.termux/files/usr/bin/bash script, Unicode text, UTF-8 text executable
phone-termux/setup.sh:   a /data/data/com.termux/files/usr/bin/bash script, Unicode text, UTF-8 text executable
server/api/locations.js: JavaScript source, ASCII text
server/api/report.js:    JavaScript source, ASCII text
51
65

```

---

## Assistant (Build · glm-5.3 · 13.5s)

_Thinking:_

Hmm, `od -c ... | grep -c '\\r'` returned 51 and 65 — wait, that's suspicious. Let me look: grep -c '\r' — in git bash, the pattern '\\r' passed through my JSON becomes literal backslash-r in the shell command... Actually the output shows "51" and "65" — but wait, the `|| echo` didn't fire since grep found matches (exit 0). Does that mean there ARE \r bytes? But `file` said plain text without "with CRLF line terminators" — file would say "with CRLF line terminators" if there were CR. 

Hmm, the grep pattern '\\r' in shell single... I used double quotes? No — the command was: od -c phone-termux/report.sh | grep -c '\\r'. In bash, '\\r' inside single quotes is literally \\r → grep sees \\r which is escaped backslash followed by r, matching literal "\r" two-char sequence. od -c output shows \r as the two characters `\` `r`. So grep counting lines containing "\r" representation... but file said no CRLF. Contradiction?

Wait — od -c represents CR as \r. If file says no CRLF terminators, there are no CR bytes, so od output wouldn't contain \r. But grep counted 51 lines... unless grep pattern '\\r' matches something else. With single quotes '\\r': bash single quotes preserve both chars, so grep receives \\r (two backslashes + r? No — single-quoted '\\r' is exactly: \ \ r — two backslashes and r, 3 chars). grep interprets \\ as escaped literal backslash, then r → matches literal "\r" in od output. If it matched 51 lines, od output DOES contain \r → file contradicts.

Hold on, actually the JSON encoding: I wrote "grep -c '\\\\r'"? Let me recheck what I sent: `od -c phone-termux/report.sh | grep -c '\\r'` — in the JSON string I wrote '\\r' which after JSON decoding becomes '\r'?? No wait. In my function call, the command parameter is a JSON string: "od -c phone-termux/report.sh | grep -c '\\r'" — JSON decodes \\ to \, so bash receives: grep -c '\r' (single-quoted backslash-r). grep pattern \r = escaped r = matches 'r'. Every line of od output containing letter 'r' matches — that's why 51 and 65! The od output contains lines with "usr/bin/bash", "report.sh" etc? No, od -c shows character dump like `0000000 # ! / d a t a ...` — letters appear as-is, so many 'r' characters (from "a", no...). od -c prints printable chars as themselves: 'd a t a / d a t a / c o m . t e r m u x ...' — contains 'r' (termux). Yes! So the count 51/65 is meaningless — matched letter 'r'.

The authoritative check is `file`: it did NOT say "with CRLF line terminators", so files are LF-only. Good. Let me do a precise check with a proper pattern to be certain: use grep -c $'\r' or od -c | grep -c '\\r' with proper quoting... simpler: `tr -d -c '\r' < file | wc -c`.

`file` 已确认无 CRLF，再用精确字节数复核一次：

**Tool: bash**

**Input:**
```json
{
  "command": "tr -dc '\\r' < phone-termux/report.sh | wc -c; tr -dc '\\r' < phone-termux/setup.sh | wc -c",
  "description": "Counts actual CR bytes in both scripts"
}
```

**Output:**
```
0
0

```

---

## Assistant (Build · glm-5.3 · 3.2s)

_Thinking:_

0 CR bytes — both scripts are LF-only, ready for Termux. All phases complete except Phase 5 (deliberately deferred). Update todos and give final summary with next actions.

**Tool: todowrite**

**Input:**
```json
{
  "todos": [
    {
      "content": "Phase 1 · server/：Vercel 全套（API×2 + 地图面板 + Supabase schema + 配置）",
      "priority": "high",
      "status": "completed"
    },
    {
      "content": "Phase 2 · phone-termux/：上报脚本 + 一键安装 + HyperOS 保活清单",
      "priority": "high",
      "status": "completed"
    },
    {
      "content": "Phase 3 · 联调验证：语法检查 + 本地逻辑自测（22/22 通过）",
      "priority": "medium",
      "status": "completed"
    },
    {
      "content": "Phase 4 · 文档：README 部署手册 + 丢机应急 SOP + phase0 兜底清单",
      "priority": "medium",
      "status": "completed"
    },
    {
      "content": "Phase 5 · phone-app/ Kotlin 工业版（B 验证稳定后交付）",
      "priority": "low",
      "status": "pending"
    }
  ]
}
```

**Output:**
```
[
  {
    "content": "Phase 1 · server/：Vercel 全套（API×2 + 地图面板 + Supabase schema + 配置）",
    "priority": "high",
    "status": "completed"
  },
  {
    "content": "Phase 2 · phone-termux/：上报脚本 + 一键安装 + HyperOS 保活清单",
    "priority": "high",
    "status": "completed"
  },
  {
    "content": "Phase 3 · 联调验证：语法检查 + 本地逻辑自测（22/22 通过）",
    "priority": "medium",
    "status": "completed"
  },
  {
    "content": "Phase 4 · 文档：README 部署手册 + 丢机应急 SOP + phase0 兜底清单",
    "priority": "medium",
    "status": "completed"
  },
  {
    "content": "Phase 5 · phone-app/ Kotlin 工业版（B 验证稳定后交付）",
    "priority": "low",
    "status": "pending"
  }
]
```

---

## Assistant (Build · glm-5.3 · 9.5s)

全部交付完成，22/22 测试通过，脚本 LF 纯净可直接跑 Termux。

## 交付清单

```
PHONE LOCATION SCRIPT/
├── README.md                    ★ 从这里开始：20分钟部署手册 + 应急SOP
├── server/                      → 部署 Vercel
│   ├── api/report.js            上报：时序安全密钥比对 + 全字段校验
│   ├── api/locations.js         轨迹：独立令牌鉴权，密钥不落浏览器
│   ├── public/map.html          /map 面板：实时位置/精度圈/轨迹/电量/失联告警
│   ├── supabase/schema.sql      RLS全锁，anon key泄露也读不到数据
│   ├── package.json / vercel.json
├── phone-termux/                → 拷入手机
│   ├── setup.sh                 一键：装依赖→配置→cron→开机自启→试运行
│   ├── report.sh                GPS→网络降级 / 断网outbox补传 / 电量上报
│   └── HYPEROS-保活清单.md      ★ Redmi Note 15 Pro 逐项保活（生死攸关）
├── docs/phase0-兜底清单.md      小米/谷歌查找设备开启验证（现在就做）
├── phone-app/README.md          Phase A 规格（B 稳定1周后交付）
└── tests/api-test.mjs           22项逻辑自测（已全过）
```

## 你接下来的动作顺序

1. **现在（5分钟）**：`docs/phase0-兜底清单.md` — 开启小米查找设备并验证 i.mi.com
2. **服务端（15分钟）**：按 `README.md` 步骤 1–4：Supabase 建库 → 生成两把密钥 → `vercel --prod` → curl 验证
3. **手机端（30分钟）**：F-Droid 装 Termux 三件套 → 拷 `phone-termux/` 进去 → `bash setup.sh` → **逐项完成保活清单**
4. **验收**：锁屏 30 分钟 + 关 WiFi 只走流量，浏览器看 `/map?token=...` 轨迹连续即布防完成
5. 稳定跑 1 周后回来说一声，交付 Phase A Kotlin 工业版（前台服务级驻留，不怕杀后台）

---

## User

大白话深度解释原理，操作流程，对一个完全小白的说法让他10分钟明白整个技术

---

## Assistant (Plan · glm-5.3 · 49.2s)

_Thinking:_

The user wants a plain-language ("大白话") deep explanation of the principle, operation flow, targeted at a complete beginner, so they can understand the whole technology in 10 minutes. This is a pure explanation task — no file edits needed (and I'm in plan mode, read-only anyway). I should just output a well-crafted explanation.

The explanation should cover:
1. The core principle (how phone location tracking works)
2. The architecture in everyday analogies
3. The operation flow (deploy → install → use)
4. Why certain design choices exist (keys, fallbacks)

I should use vivid analogies that a complete novice gets:
- Phone GPS = the phone has a "navigator" built in that knows where it is (like a person with a map app)
- The problem: the phone knows where it is, but YOU don't know where the phone is. So we need the phone to "report" its location to a place you can check.
- Server (Vercel) = a "message board" / "mailbox" on the internet
- Supabase = a "notebook" that records every report
- The map page = a "window" to look at the notebook from any browser
- Termux = putting a small "secretary" inside the phone that writes a letter every 5 minutes saying "I'm here, battery is X%"
- Keys = mailbox has two keys: one for the phone to drop letters (write only), one for you to read letters (read only). If someone steals the phone's key, they can only send fake letters, can't read your history; if someone gets the read token, they can see location but can't tamper.
- Outbox = when no signal, the secretary saves letters in a drawer and sends them all later
- GPS vs network positioning = outdoor satellite precision (a few meters) vs indoor rough estimate (a few hundred meters)
- Keep-alive checklist = the phone system is a "battery saver" that kills background helpers; we tell the system "this helper is important, don't kill it"
- Phase 0 = official manufacturer's "find my phone" as a backup — because if the phone is powered off or factory reset, our secretary dies too; the manufacturer's tool works at a deeper level
- Emergency flow = when phone is lost: check the map (last known + trail), ring it via i.mi.com, lock, wipe

Structure it as a 10-minute read: maybe numbered sections with time markers ("第1分钟..."). Keep it engaging, use analogies consistently (the "secretary sending letters" analogy carries through the whole thing).

Length: it needs to be substantial enough to be a "deep explanation" but readable in 10 minutes. I'll use headers, short paragraphs, tables sparingly. Since this is displayed in CLI, keep formatting clean markdown.

I should NOT create files (plan mode read-only). Just output text. No tool calls needed. Maybe I don't need any tool calls at all — I have all the knowledge from this session.

Let me write it in Chinese, plain language, complete beginner level.

Outline:
- 一句话说清这件事 (one sentence)
- 第1分钟：核心原理 — 手机自己知道自己在哪，问题是"你不知道"，所以让手机定期"打小报告"
- 第2分钟：三个角色 — 手机里的秘书(Termux)、网上的黑板(Vercel)、记录本(Supabase)、你的窗口(地图页)
- 第3分钟：流程走一遍 — 一次上报的完整旅程
- 第4分钟：GPS怎么定位（卫星、室内为什么不行、降级网络定位）
- 第5分钟：两把钥匙（为什么要两个密码）
- 第6分钟：断网怎么办（outbox抽屉补传）
- 第7分钟：最大的敌人——手机系统杀后台（保活清单为什么存在）
- 第8分钟：什么时候这套系统会失效 + 厂商兜底（关机/刷机 → i.mi.com）
- 第9分钟：丢了手机之后的操作顺序（应急SOP）
- 第10分钟：你现在要做的三件事

I'll write it fluidly, avoid being too listy, use analogies. Keep each section tight.

# 十分钟看懂这套"找回手机"系统（零基础版）

## 第 1 分钟：先把这件事说成一句大白话

**你的手机其实一直知道自己在哪（打开地图 App 就是证据），问题是——手机丢了之后，"它自己知道"没用，得让"你"知道。**

所以这套系统干的事情就一件：

> 让手机每隔 5 分钟给你发一封"小报告"：*"我现在在 XX 位置，电量还剩 83%"*。报告寄到一个你随取随看的地方——任何浏览器打开一个网址，就能看到它此刻在哪、去过哪。

没了。整个系统没有魔法，本质就是"定时打小报告 + 一块谁都能看的公告栏"。

---

## 第 2 分钟：认识三个角色（像三个员工）

| 角色 | 比喻 | 实际是什么 |
|---|---|---|
| 手机里的"秘书" | 住在手机里、每 5 分钟写一封位置信寄出去 | Termux 里跑的那个小脚本 `report.sh` |
| 网上的"公告栏" | 收信、存档、给你看 | Vercel（免费云服务）+ Supabase（云数据库，相当于存信的柜子） |
| 你手里的"窗口" | 任何浏览器打开的一个网页 | `你的网址/map?token=你的令牌` |

为什么需要三个而不是一个？因为手机会丢——**凡是存在手机上的东西，丢了就一起没了**。所以位置信息必须寄到"手机外面"的地方，才叫保险。

---

## 第 3 分钟：一封"位置小报告"的完整旅程

跟着一封信走一遍，你就懂了全流程：

1. **写地址**：秘书（脚本）问手机系统："咱现在在哪？" 手机里有 GPS 芯片，它回答："北纬 39.90、东经 116.40，误差约 10 米。" 顺手再报一下电量。
2. **装信封**：这些数字被打包成一小段标准格式的文字（JSON），相当于信件正文。
3. **贴条码**：信封上盖一个**只有你家公告栏认识的暗号**（那把 DEVICE_KEY 密钥）。公告栏一看暗号不对，直接当垃圾扔掉——防止坏人乱塞假位置污染你的记录。
4. **寄出**：手机用流量/WiFi 把信发到 Vercel 那个网址。
5. **存档**：Vercel 拆信验暗号，确认格式没问题，存进 Supabase 柜子里，每封信自动盖时间戳。
6. **查看**：你打开浏览器输入地图网址，网页每 15 秒自动问一次柜子"有新信吗？"，把最新位置画在地图上，历史信件连成一条线——就是**轨迹**。

整套循环就这么多。所谓"精准定位"，精度来自第 1 步手机的 GPS 芯片，跟这套系统无关——我们只是**忠实搬运工**。

---

## 第 4 分钟：GPS 为什么准，为什么室内会变"猜"

- **GPS 定位**：手机头顶上有几十颗卫星，信号里带着精确时钟。手机同时听 4 颗以上卫星"报时"，根据时间差算出自己离每颗星多远，交会出位置。误差通常 **3~10 米**——这就是"精准"的来源。缺点：卫星信号穿不透屋顶和厚墙，**室内经常收不到**。
- **网络定位**：退而求其次，手机拿"附近哪些 WiFi、哪个基站"去比对数据库，猜出大致位置。误差 **50~500 米**，能知道"在这栋楼附近"，但说不清哪个房间。

所以脚本设计了**自动降级**：先等 GPS 15 秒，拿不到就立刻改用网络定位，并在报告里标注这次是 `gps` 还是 `network`。地图上你会看到位置点外面有个圈——那是误差范围，圈越小越准。**丢了手机在室外找，GPS 能把你带到几米内。**

---

## 第 5 分钟：为什么有两把钥匙，而不是一个密码

这是全系统最重要的安全设计，用大白话讲：

- **DEVICE_KEY（寄信暗号）**：贴在手机发来的每封信上。有了它能**往柜子里塞信**，但看不了任何记录。
- **ACCESS_TOKEN（看信门票）**：拼在你打开的地图网址里。有了它能**看记录**，但不能塞假信。

好处：两个权限分开，坏一个不伤另一个。
- 小偷捡到手机，翻出寄信暗号 → 他只能发假位置，**看不到你家在哪、你常去哪**（轨迹才是最敏感的隐私）。
- 你把地图网址发给别人帮忙找手机 → 他能看位置，**不能篡改记录**。

另外，Supabase 柜子上了三重锁（数据库那套 RLS），就算数据库的"公共钥匙"泄露，别人也读不到里面一个字。这些设计你已经不用管，我只是告诉你：**这套东西按" assume 会被攻击"的标准造的**。

---

## 第 6 分钟：手机没信号了，报告会丢吗？

会丢——如果不处理。所以脚本有个"**抽屉**"（outbox 文件）：

> 手机进隧道、没流量了 → 信寄不出去 → 秘书把信暂存在抽屉里 → 下一轮信号恢复了，**先把抽屉里的旧信全部补寄，再寄新信**。

对找回手机的意义：如果丢手机那晚它恰好进过信号盲区，恢复信号后你依然能看到完整轨迹，不会断片。每封信还带着自己的写信时间（不是寄达时间），所以补寄的历史顺序也不会乱。

---

## 第 7 分钟：这套系统唯一的"天敌"——手机自己

听起来反直觉：手机系统（HyperOS）为了省电，会**主动掐死在后台偷偷干活的东西**。我们的秘书每 5 分钟醒一次，在系统眼里就是"可疑耗电分子"，逮住机会就杀。

这就是为什么有那份《HYPEROS 保活清单》——它不是可选项，是**生死线**。清单做的事翻译成大白话就是，你拿着手机对系统说了四句话：

1. "这个 Termux 是自己人，**锁定**别清掉"（最近任务挂锁 🔒）
2. "别对它省电，**随便用**"（省电策略→无限制）
3. "开机它要先活过来"（自启动 + Termux:Boot）
4. "它干活时**别让 CPU 睡死**"（唤醒锁，通知栏那个常驻通知就是凭证）

做完这四件事 + 重启验证一次， secretary 就基本杀不死了。**装完不照着做 = 白装。**

---

## 第 8 分钟：它什么时候救不了你（以及那两条后路）

诚实说清楚边界，比吹牛重要：

| 场景 | 本系统 | 原因 |
|---|---|---|
| 手机有电、有网（哪怕被捡走） | ✅ 每 5 分钟报位置 + 完整轨迹 | 秘书还活着 |
| 被关机 / 断网 | ⚠️ 只能看到**最后位置和轨迹** | 信寄不出来了 |
| 被刷机 / 恢复出厂设置 | ❌ 彻底失效 | 秘书被连根拔掉 |

所以才有 **Phase 0 兜底**（那份清单让你现在就开好）：

- **小米查找设备（i.mi.com）**：小米官方的同类系统，埋在系统最底层，**走短信/流量通道，捡到的人关掉 WiFi 也能唤醒它**，还能远程响铃（静音也照响）、锁屏、留"失主电话"。刷机也甩不掉账号锁。
- **报警用的 IMEI**：手机的"身份证号"，提前记下来，是最后的法律手段。

三层保险的关系：**自建系统（新鲜轨迹）→ 小米云（响铃/锁定/擦除）→ IMEI 报警**，一层失效下一层顶上。

---

## 第 9 分钟：真丢了手机，你按这个顺序操作

1. **抓起任何浏览器** → 打开你收藏的 `/map` 网址 → 看最后位置 + 轨迹往哪个方向延伸（自建系统最新鲜）
2. **登 i.mi.com** → 点"响铃"（可能掉沙发缝了）→ 没反应就"锁定 + 丢失模式"，屏幕上显示你的联系电话
3. 地图显示**停止移动很久** → 大概率没电或被关机，记住最后出现点，等小米通道把它唤醒上线
4. 确认被盗 → 小米云**远程擦除** → 打运营商电话挂失 SIM → 带 IMEI 报警
5. 找回/换机后：换掉两把钥匙（改环境变量 + 手机里改一行配置），防止旧钥匙落在别人手里

---

## 第 10 分钟：收束成一张图和三件事

```
手机(GPS知道自己在哪)
  └─ 秘书每5分钟写信 ──暗号──▶ 公告栏(验证+存档) ──▶ 柜子(上锁)
                                                    │
            你 ◀──任意浏览器打开地图网址──门票──────────┘
```

现在要做的就三件事，别的都可以以后再说：

1. **今天**：花 5 分钟做完 `docs/phase0-兜底清单.md`（开小米查找设备 + 记 IMEI）
2. **这周**：照 `README.md` 把服务端部署了、手机脚本装了、保活清单做完，锁屏放 30 分钟验收
3. **养成习惯**：每个季度演练一次"假装丢了"，确保系统一直活着

懂了这三件事——**手机自己知道在哪、让它寄出去、你从外面看**——这套系统的所有文件、配置、清单，都只是这三句话的工程化细节而已。

---

## User

可以不是默认15秒，而是指定任意时间，加上可以查找端按“获取地址”按钮，才发送，可以有被动和主动两种方式？

---

## Assistant (Plan · glm-5.3 · 1752.9s)

_Thinking:_

The user is asking for two enhancements:

1. **Polling interval configurable**: instead of hardcoded 15s polling in map.html, allow arbitrary interval.
2. **On-demand location fetch ("被动和主动两种方式")**:
   - 被动 (passive): phone reports on its own schedule (current cron behavior)
   - 主动 (active/on-demand): the viewer clicks a "获取位置" button on the map page, which triggers the phone to report its location immediately.

The second is a classic command channel design problem. Let me think through the architecture:

**On-demand fetch architecture:**

The map page needs to send a "command" to the phone. The phone can't be reached directly (no inbound connection to a phone behind carrier NAT). The standard pattern:

```
Browser → POST /api/ping (create command row in Supabase: commands table)
Phone polls periodically → GET /api/poll (checks for pending commands)
Phone sees pending "locate now" command → runs termux-location immediately → POST /api/report (with reply/command id)
Browser (already polling) sees fresh point appear → done
```

Key design decisions:

1. **How does the phone receive commands?** The cron job runs every 5 minutes — too slow for on-demand. Options:
   - a) A long-running loop script (daemon) on the phone that polls the command endpoint every N seconds (e.g., 30-60s), plus keeps the cron as backup. This is the practical approach in Termux. But it keeps the phone awake more; termux-wake-lock is already on. A polling loop with `sleep 60` is lightweight.
   - b) Push notifications (FCM) — requires Google services, an app, not feasible with pure Termux script.
   
   So: a resident daemon loop is the right answer. It checks /api/poll every ~45-60s; if there's a pending command, it runs report.sh immediately and marks it done.

   Actually smarter hybrid: the daemon loop IS the reporting engine too. But plan-wise, minimal change: keep cron (belt) + add daemon loop (suspenders) — or make daemon the primary with cron as fallback if daemon gets killed. Both use same report.sh.

2. **Commands table in Supabase:**
   ```sql
   create table public.commands (
     id bigint generated always as identity primary key,
     created_at timestamptz default now(),
     device text default 'primary',
     type text not null check (type in ('locate')),  -- extensible: ring later in Phase A
     status text not null default 'pending' check (status in ('pending','done','expired')),
     claimed_at timestamptz,
     done_at timestamptz
   );
   ```
   Phone polls: GET /api/poll → returns oldest pending command id, marks it claimed. After executing, phone POSTs /api/report with `cmd_id`, and the report endpoint marks command done. Or simpler: phone calls POST /api/ack with cmd id... The report endpoint can accept cmd_id field and mark done — couples cleanly: the reply IS the report.

   Expiry: commands older than e.g. 10 minutes auto-expire (phone offline). Can be handled in poll query (where created_at > now()-10min) and the map UI shows status.

3. **New API endpoints:**
   - POST /api/ping (browser, ACCESS_TOKEN auth): inserts locate command, returns command id + ETA
   - GET /api/poll (phone, DEVICE_KEY auth): claim & return pending command
   - report.js: accept optional `cmd_id` → mark that command done
   - Command status visible: GET /api/locations could include latest command status, or map polls /api/command-status?id=. Simpler: map's existing refresh shows new point; for UX feedback (spinner "等待手机响应"), map polls a status endpoint. Could fold into /api/ping response + subsequent /api/locations responses... Cleanest small addition: GET /api/poll doesn't help browser. Add lightweight /api/cmd?id= (token auth) returning status. Or piggyback: /api/locations response header... no, keep it explicit and simple: map page after clicking 获取位置 polls every 3s a status until done/timeout(90s), then refreshes locations.

   Actually simpler unified: extend locations.js? No — keep endpoints single-purpose. New endpoint `api/command.js` handling both POST (create, token auth) and GET (status by id, token auth). Phone side: `api/poll.js` (device key auth).

4. **Map UI changes:**
   - "获取位置" button: disabled while pending; shows countdown/spinner; on success recenter to newest point.
   - Polling interval: a small control (dropdown or input: 5s/15s/30s/60s/custom) persisted in localStorage. The 15s hardcoded POLL_MS becomes user-settable.
   - Note in UI: 获取位置 response latency depends on phone's poll interval (daemon sleep). So make phone poll interval configurable too (setup.sh prompt, default 45s or 60s), and document the relationship: on-demand latency ≈ phone poll interval + GPS lock time.

5. **Phone side changes:**
   - New script `agent.sh` (daemon loop): every POLL_INTERVAL seconds → GET /api/poll; if command → bash report.sh (maybe with gps priority and longer timeout for accuracy) → report.sh sends cmd_id ack.
   - How does report.sh know cmd_id? agent.sh passes it as arg: `bash report.sh "$cmd_id"` → included in payload as cmd_id.
   - setup.sh: updated to install/start agent daemon (nohup + pgrep check), boot script starts crond AND agent. Cron stays as watchdog/backup reporting (maybe reduce cron to every 15 min as backup heartbeat while daemon does 5-min + on-demand?). Hmm — decide: keep cron 5-min as the passive backbone; daemon only handles on-demand commands (poll every 60s, lightweight). That's clean separation: cron = passive timer reports; daemon = command listener. Both wake-locked. Also daemon could double as cron-watchdog (restart crond if dead).
   - Battery consideration: polling /api/poll every 60s over mobile data uses tiny bandwidth (~each poll a few hundred bytes; 1440 polls/day ≈ maybe 5-10MB/month) — acceptable, must document.

6. **Race conditions:** two commands queued → poll returns one at a time (claim via update ... where status='pending' ... limit 1). Supabase REST doesn't do atomic claim easily without RPC. Options: create an RPC function `claim_pending_command(device)` in Postgres with security definer... but service key bypasses RLS anyway; atomicity via `for update skip locked` in an RPC. For single-device personal use, race risk is minimal (one phone, one daemon). Keep simple: poll fetches oldest pending, then patch to 'claimed' — document single-consumer assumption. Or do RPC properly — one function, cleaner. I'd include an RPC in schema (claim_next_command) — it's industrial-grade and cheap to add.

7. **Map polling interval** — trivially: UI select persisted to localStorage, applied to setInterval; plus a manual "立即刷新" maybe not needed since 获取位置 covers it.

Also user said "不是默认15秒，而是指定任意时间" — this refers to map auto-refresh interval (the 15s POLL_MS). Make it user-configurable in UI (and maybe URL param `&interval=30`). 

Security: new endpoints auth mapping:
- POST/GET /api/command → ACCESS_TOKEN (browser)
- GET /api/poll → DEVICE_KEY (phone)
- report with cmd_id → DEVICE_KEY (existing)

Edge: expiry cleanup — poll ignores commands older than TTL (10 min), status endpoint reports 'expired'. Cleanup update on read.

Deliverables list (plan):

**Server:**
1. `server/supabase/schema.sql` — add commands table + indexes + claim_next_command() RPC + grant execute... service role bypasses RLS; RPC with security definer owner postgres. RLS enabled on commands, no policies.
2. `server/api/command.js` (new) — POST create / GET status, token auth.
3. `server/api/poll.js` (new) — device key auth, calls RPC, returns command or 204.
4. `server/api/report.js` (edit) — accept cmd_id (integer, optional) → after successful insert, patch command status done; ignore if unknown id.
5. `server/public/map.html` (edit) — 获取位置 button with pending state/timeout/feedback; interval selector (URL param + localStorage, options 5/10/15/30/60s + custom); show command result feedback (成功/超时); status card shows phone daemon last poll time? (nice: poll endpoint could also update a device heartbeat... scope creep — skip or minimal: commands table creation implies nothing. Could add 'last_poll' tracking via poll endpoint updating a devices table... skip for now, mention as optional).

**Phone:**
6. `phone-termux/agent.sh` (new) — daemon: loop poll → on command run report.sh with cmd_id arg; also watchdog restart crond; interval from config.env (AGENT_INTERVAL default 60).
7. `phone-termux/report.sh` (edit) — accept optional $1 as cmd_id into payload.
8. `phone-termux/setup.sh` (edit) — write AGENT_INTERVAL into config; start agent daemon (nohup, pgrep guard); boot script starts crond + agent; print on-demand usage note.
9. `phone-termux/HYPEROS-保活清单.md` (edit) — add agent daemon verification (pgrep -f agent.sh), note about it in troubleshooting.
10. README (edit) — new section: 主动/被动双模式说明, latency expectations, monthly data usage note, button usage.

**Tests:**
11. `tests/api-test.mjs` (edit) — add command lifecycle tests: create→poll claims→report with cmd_id→status done; expired command; auth on new endpoints.

Latency math for docs: on-demand response time ≈ agent poll interval (default 60s, configurable 30–120s) + GPS lock (up to 15s) + upload ≈ worst ~90s. Set button timeout to 120s.

Battery/data tradeoff table: agent interval 30s = faster response, more battery/data; 60s default sweet spot; 120s = frugal.

One more consideration — user's phrase "可以有被动和主动两种方式" confirms: 被动 = existing scheduled reporting (keep), 主动 = on-demand button. My plan matches.

Also plan mode is active — READ ONLY. I must NOT edit files. I should present the plan and ask clarifying questions if needed. 

Clarifying questions worth asking:
1. Agent poll interval default (30/60/120s)? — could just default 60 and make configurable; maybe ask since it's a battery tradeoff.
2. Should 获取位置 also work when daemon dead (fallback)? Can't — document limitation. No question needed.
3. Map refresh interval: any value or preset options? User said "任意时间" — so free input + presets, persisted. Fine, no question.

Maybe ask one question about battery/data preference for the phone-side poll frequency, and whether commands should be extended later (ring etc.) — the latter is Phase A anyway. I think one concise question round: default agent interval choice. Or just present plan with recommended default and note alternatives — the user already said "任意时间" for interval, so configurable everywhere; defaults are mine to recommend. I could skip questions... The guidance says ask when weighing tradeoffs. Battery vs responsiveness is a genuine tradeoff but I can present recommendation (60s) with config option. I'll present the plan with the tradeoff table inline and ask a light confirmation question including the interval default choice — one question, quick.

Let me write the plan concisely with a diagram, endpoint table, file change list, and the one question.

可以，这是经典的"命令通道"设计。手机躲在运营商内网后面，浏览器没法直接"敲门"，所以主动模式要加一条**反向轮询通道**。方案如下：

## 主动/被动双模式设计

```
被动模式(已有):  cron 每5分钟 → report.sh → POST /api/report ─┐
                                                              ├─▶ Supabase
主动模式(新增):  浏览器点[获取位置] → POST /api/command(排队)   │
                     │                                        │
                     ▼                                        │
                手机 agent.sh 守护进程每60秒 GET /api/poll ◀───┘
                     │ 领到"立即定位"命令
                     ▼
                立刻跑 report.sh(带cmd_id) → 上报+销单
                     │
                     ▼
                浏览器轮询命令状态 → 变"done"且新点出现 → 地图自动居中
```

## 变更清单（7 个文件）

| 文件 | 改动 |
|---|---|
| `server/supabase/schema.sql` | +`commands` 表（pending/claimed/done/expired）+ `claim_next_command()` RPC（`FOR UPDATE SKIP LOCKED` 原子领取，防并发双领） |
| `server/api/command.js` **新增** | 浏览器端：POST 创建命令（ACCESS_TOKEN）、GET 查命令状态（超时 10 分钟自动判 expired） |
| `server/api/poll.js` **新增** | 手机端：DEVICE_KEY 鉴权，领最老的 pending 命令并标记 claimed |
| `server/api/report.js` 微改 | 接受可选 `cmd_id` 字段 → 入库成功后把命令标 done（回复即上报，闭环） |
| `server/public/map.html` 改 | ① 新增 **[获取位置]** 按钮：点击→排队→按钮转圈"等待手机响应…"→成功居中/超时(120s)提示 ② 自动刷新间隔改为**可自选任意秒数**（下拉 5/10/15/30/60/自定义 + URL `&interval=` 参数 + localStorage 记忆） |
| `phone-termux/agent.sh` **新增** | 常驻守护：每 `AGENT_INTERVAL`(默认60s) 轮询领命令→立即精确定位上报；兼看门狗（crond 死了拉起来）；断网静默重试 |
| `phone-termux/setup.sh` / `report.sh` / 保活清单 / README | report.sh 接收 cmd_id 参数入 payload；setup.sh 启动 agent + 写入开机自启；文档补双模式说明与排障 |

## 关键权衡（需要你拍板一处）

**主动模式的响应速度 = 手机轮询间隔 + GPS 锁星时间**，轮询越快越灵敏，但流量/耗电越多：

| agent 轮询间隔 | 点按钮到出位置 | 每月流量(估) | 耗电影响 |
|---|---|---|---|
| 30s | 约 30~60 秒 | ~15MB | 略增 |
| **60s（推荐默认）** | 约 60~90 秒 | ~8MB | 几乎无感 |
| 120s | 约 2~3 分钟 | ~4MB | 最省 |

间隔写入 config.env 随时可改，不必纠结，默认值只影响开箱体验。

**Tool: question**

**Input:**
```json
{
  "questions": [
    {
      "header": "轮询默认值",
      "options": [
        {
          "description": "点按钮后约 1~1.5 分钟出位置，流量耗电几乎无感",
          "label": "60秒（推荐）"
        },
        {
          "description": "找回手机场景追求响应速度，接受流量耗电略增",
          "label": "30秒"
        },
        {
          "description": "极省电优先，主动模式等待最长可到 3 分钟",
          "label": "120秒"
        }
      ],
      "question": "手机端 agent 守护进程的默认轮询间隔用多少？（写入配置后随时可改）"
    }
  ]
}
```

**Output:**
```
User has answered your questions: "手机端 agent 守护进程的默认轮询间隔用多少？（写入配置后随时可改）"="60秒（推荐）". You can now continue with the user's answers in mind.
```

---

## Assistant (Plan · glm-5.3 · 11.0s)

_Thinking:_

User confirmed 60s default. Plan mode is active — I present the final consolidated plan and wait for approval to execute ("开始" or similar). I already presented the change list and architecture. Now finalize the plan with the confirmed decision, the execution order, and testing plan. Keep it tight since most detail was already shown. End by asking to confirm execution.

拍板：**agent 默认 60 秒轮询**（config.env 可随时改）。最终执行计划确认如下：

## 最终方案 · 双模式定位系统升级

**新增能力**
- **被动模式**（保留）：cron 每 5 分钟定时上报，零干预持续轨迹
- **主动模式**（新增）：任意浏览器点 **[获取位置]** → 约 60~90 秒内手机立即精确定位回传，地图自动居中
- **地图刷新间隔自由设定**：预设 5/10/15/30/60 秒 + 自定义任意值，URL 参数 `&interval=30` 可指定，localStorage 记忆选择

**执行顺序**（一次到位，共 7 文件改动 + 测试）

1. `server/supabase/schema.sql`：commands 表 + `claim_next_command()` 原子领取 RPC（重复执行安全，老库升级直接跑）
2. `server/api/command.js` 新增：POST 排队 / GET 查状态（ACCESS_TOKEN 鉴权，10 分钟未响应自动 expired）
3. `server/api/poll.js` 新增：DEVICE_KEY 鉴权领命令
4. `server/api/report.js`：微改支持 `cmd_id` 销单
5. `server/public/map.html`：获取位置按钮（pending 转圈/成功/超时反馈）+ 间隔选择器
6. `phone-termux/agent.sh` 新增：60s 轮询守护 + crond 看门狗；`report.sh`/`setup.sh`/保活清单/README 同步更新
7. `tests/api-test.mjs`：补命令全生命周期用例（创建→领取→上报销单→状态 done / 超时 expired / 双端鉴权 401）

**升级影响**：服务端重跑一次 schema.sql + 重新部署 Vercel 即可，历史数据不动；手机端重跑 `setup.sh` 会自动启动 agent 并注册开机自启。

计划就绪，回复"开始"即进入实施。

---

## User

被动模式（保留）：cron 每 5 分钟定时上报，零干预持续轨迹---我是担心被动模式的电量和流量消耗，要把这个降低到可以最大化，比如1小时？因为平时根本不需要知道手机定位
所有才有主动模式，急需的时候必须最快速度知道位置，最好立马知道（技术的最低值），因为主动发送请求，不会很多，应该流量和电量极少吧？

---

## Assistant (Plan · glm-5.3 · 65.0s)

_Thinking:_

The user is refining the requirements:

1. **Passive mode**: They're worried about battery/data consumption. They want to maximize savings — e.g., change cron from 5 minutes to 1 hour, because normally they don't need to know the phone's location at all.

2. **Active mode**: When urgently needed, they want the fastest possible response — ideally immediate ("技术的最低值" = the technical minimum latency). They reason that since active requests are rare, the traffic/battery cost should be minimal.

Let me think about this carefully as an architect.

**Passive mode at 1 hour:**
- cron every 60 minutes: 24 reports/day. Each report ≈ a few hundred bytes + GPS lock attempt. Battery impact: negligible. Data: < 1MB/month. 
- Downside: the "last known position" trail granularity becomes 1 hour — if phone is lost between reports, the last passive fix could be up to 60 min stale. But that's exactly what active mode covers: when you discover the phone is lost, you press the button and get a fresh fix.
- One consideration: with 1-hour passive + the "device online/offline" judgment in map.html — currently OFFLINE_MS = 15 min (3× the 5-min period). With 1-hour reporting, the online threshold must adapt or become configurable/automatic. Important detail! The map UI判断"失联"的逻辑需要跟着改 — if passive interval is 1h, the status card would show "失联" 15 minutes after the last report even though the phone is fine. Fix: derive OFFLINE threshold from... the map doesn't know the phone's passive interval. Options:
  - Pass it in URL param (`&offline=75` minutes)
  - Or better: the agent daemon polls every 60s anyway — so the daemon itself provides a natural heartbeat! The poll endpoint could record `last_seen` for the device (update a devices table or upsert into a heartbeat table). Then "online" = daemon seen within ~3 minutes. This decouples online status from passive reporting interval entirely. That's the clean industrial answer, and it's cheap: one upsert per poll (already making the request anyway).
  - That also means: even with 1-hour passive, the map still shows "device alive, last poll 30s ago" — great UX for "is the phone still on/in network".
- Actually this heartbeat insight is a genuine architectural improvement worth proposing: passive GPS reporting can go even lower (e.g., 2h or daily just for a sparse trail), while online status comes from the daemon's lightweight polling.

**Active mode latency — what's the technical minimum?**
The user asks: can it be immediate? The bottleneck chain:
- Browser POST /api/command → ~instant (<1s)
- Phone discovers the command: this is THE bottleneck. Phone is behind NAT, no push without Google services (FCM unavailable in pure Termux; China Redmi may not even have GMS active). Options:
  a) **Agent daemon polling every 60s** → worst-case 60s discovery + GPS lock (~5–15s) + upload → total ~30–90s typical. 
  b) Poll faster (10s): battery cost rises — each poll wakes radio... Actually with termux-wake-lock the CPU stays awake; each HTTPS poll ≈ few hundred bytes; every 10s = 8,640 polls/day ≈ maybe 50–100MB/month and noticeable battery. Hmm, radio wake is the battery killer; with wake-lock and radio already... on mobile data each poll keeps radio awake. 10s polling is too aggressive.
  c) **Long-polling / HTTP streaming**: agent opens a request that the server holds open until a command arrives (or 50s timeout), returns, agent immediately reconnects. This gives near-instant command delivery (~1–3s) at the cost of ~1 request per ~50s (same order as 60s polling traffic!) — BUT Vercel serverless functions have a max duration (default 10s, max 60s on hobby... actually Hobby plan: 10s default, configurable max 60s for functions; fluid compute now allows longer). Long-polling on Vercel is doable with 25–50s holds. Traffic: one held connection per ~45s ≈ same as polling every 45s, but latency drops from 45s to ~0. This is the classic trade and the RIGHT answer for "最快速度知道位置".
  - However: complexity. Holding connections on serverless is fine within limits. Implementation: /api/poll becomes long-poll: poll DB every 2s internally for up to N seconds (Vercel Hobby max execution 60s — internal DB polling every 2s for 50s = 25 Supabase reads per held connection... Supabase free tier: 500 requests/sec? No—free tier has ~2MB/s transfer and generous request counts; 25 sub-queries per 50s per device = fine for 1 device).
  - Alternatively use Supabase Realtime (websocket) — but that requires anon key in phone... service key can't be embedded in phone! Wait — the phone has DEVICE_KEY only. Supabase Realtime would need the phone to hold a Supabase key; we could give the phone the anon key, but RLS blocks everything for anon, and realtime subscriptions would need policies exposing commands table to anon — breaks our security model. So no.
  - So long-poll via our API (phone authenticates with DEVICE_KEY, server holds Supabase service key) is the architecturally clean option: **latency ≈ GPS lock time only (~5–20s)**.
  d) SMS fallback (send SMS to trigger USSD/auto-reply apps like Tasker) — out of scope, needs extra app, paid, brittle.

So the honest "technical minimum": with long-polling, button→fresh location ≈ **5–25 seconds** (dominated by GPS lock + network RTT), at traffic cost similar to current 60s polling (actually slightly higher since connection every ~50s vs 60s — negligible).

Let me reconsider: is long-polling reliable on Termux + HyperOS? The agent loop: curl with --max-time 55 to /api/poll?wait=50. Server: loop { check DB; if command → return; sleep 2 } until 50s → return 204. Agent immediately reconnects. Phone-side battery: the wake-lock keeps CPU on anyway; radio wakes per request ≈ same as regular polling. It's strictly better latency for same cost. Implementation complexity: modest (loop in the function, respect Vercel timeout).

Vercel Hobby function timeout: currently 10s default, max configurable to 60s (with fluid compute, up to 300s now? — as of 2025 Vercel raised to 800s for fluid... I shouldn't overstate; safe: configure maxDuration 60). With 60s max, hold for ~50s → effective command delivery ≤ ~2–5s after creation. 

Edge: serverless cold starts add ~1–2s occasionally. Fine.

**Data estimate for user's assumption check ("主动发送请求，应该流量和电量极少吧？")**:
- Long-poll: ~72KB/request? No — headers ~300–500 bytes each way. 1 held request per 50s → ~1,700 req/day → ~2–4MB/month. Plus each poll heartbeat upsert — server-side, doesn't add phone traffic. So ~5MB/month level, same ballpark as before.
- Active location fetch: one GPS lock + upload ≈ negligible (KBs).
- So yes, user's intuition is right: active mode is cheap; the only continuous cost is the daemon's listening channel.

**Revised architecture proposal:**

1. **Passive cron → 1 hour** (configurable at setup; default 60). Purpose: sparse daily trail + battery heartbeat. Optionally even 2–6h. I'd default 60min, let user pick.
   - Actually with daemon heartbeat, passive could be daily even. But keep 1h as sensible "trail" default per user's explicit number.

2. **Daemon agent → long-poll (hold 45–50s)** instead of sleep-60: same traffic, but command delivery in seconds.
   - GPS lock dominates: total button→position ≈ **5–25s**, worst ~40s indoor (GPS fail→network fallback 10s more).

3. **Online status via daemon heartbeat**: poll endpoint upserts device last_seen (new tiny `devices` table or reuse commands?) → map shows "daemon 在线（x秒前心跳）" independent of passive interval. Solves the OFFLINE_MS problem created by 1h passive.
   - Implementation: devices table (device text pk, last_seen timestamptz, last_report timestamptz?). Poll updates last_seen. /api/locations (or /api/command GET) returns it. Map: online = heartbeat < 3 min.

4. Map OFFLINE threshold: switch to heartbeat-based; passive staleness shown as "最后轨迹点 x 分钟前" separately.

**Battery truth-telling for user**:
- 1h passive + always-on daemon with wake-lock + long-poll: the wake-lock itself prevents deep sleep → this is the real battery cost of ANY Termux-based always-listening approach (maybe 2–4%/day extra). Honest note: if battery is paramount, Phase A app can use FusedLocation + JobScheduler with proper doze compatibility... but for now B is what we have; the daemon is required for active mode anyway.
- Actually is that true? termux-wake-lock holds PARTIAL_WAKE_LOCK → CPU never deep-sleeps → measurable drain. Without agent, cron-only 1h + wake-lock... cron also needs wake-lock to fire reliably in deep sleep. Hmm: cronie alarms don't survive deep sleep without wake lock. So either way wake-lock is needed → the marginal cost of the long-poll daemon vs cron-only is small (network radio wake per ~50s). Trade-off table should be honest: 
  - 被动 only (1h, wake-lock): ~minimal drain
  - +daemon long-poll: slightly more radio activity, still small (~1-3%/day range)
  - Numbers are estimates; advise real-world measurement via battery stats.

**What changes in the plan vs previous approved plan:**
- Passive default 5min → **60min** (setup.sh prompt default changes; report.sh unchanged)
- agent.sh: sleep-poll 60s → **long-poll** (~50s holds, instant reconnect)
- api/poll.js: add `wait` param, internal 2s DB re-check loop, maxDuration 60 in vercel.json
- New: devices heartbeat table + upsert in poll + expose in locations/command status API
- map.html: online judgment switches to heartbeat; show both "心跳 x 秒前" and "最后轨迹点 x 分钟前"; 获取位置 button expects fast response (update timeout UX ~60s, progress hint "通常 5~25 秒")
- schema.sql: +devices table, +commands (as before), +claim RPC
- Tests: heartbeat upsert, long-poll immediate-return path (can't easily test 50s holds in unit tests — test wait=0 / instant path + timeout path with tiny wait)
- README/HYPEROS checklist updates

**Latency summary to present:**
- 点按钮 → 手机收到命令: ~0–3 秒（长轮询挂着，命令一到就返回）
- GPS 锁星: 5–15 秒室外（室内降级网络 +10 秒）
- 上传+地图刷新: 1–3 秒
- **合计: 室外 ~8–25 秒，室内最坏 ~40 秒** ← 这就是纯 Termux 方案的技术下限
- 要做到"秒级"只有原生 App + 厂商推送通道（FCM/小米推送）→ Phase A 范畴

Traffic estimate:
- daemon long-poll: ~3–5MB/月
- 被动 1h: 24 次/天 × <1KB ≈ ~1MB/月 GPS 定位本身免费不耗流量
- 主动: 忽略不计
- 合计 ~5MB/月

I'm in plan mode. I should present this revised plan, maybe with one clarifying question about passive interval (they said "比如1小时" — they proposed 1h as example; I could ask 1h vs longer, but honestly 1h with heartbeat-based status is a good default and they explicitly floated it. Maybe ask whether passive trail granularity 1h is acceptable or prefer longer/shorter — I think I can make it configurable and default 60min, no question needed. But maybe one question: whether to accept the long-polling design (slightly more engineering) vs simple 60s polling (simpler, 60-90s response). Given user explicitly said "最好立马知道（技术的最低值）", long-poll is clearly what they want. I'll present both but recommend long-poll... Actually asking again may be annoying; the user's requirement is explicit: fastest possible. I'll go with long-poll and present the numbers. One thing worth confirming: default passive interval — they said "比如1小时", so default 60 min. Fine, no question. I'll present final plan and wait for 开始.

Also must address their question directly: "主动发送请求，不会很多，应该流量和电量极少吧？" → confirm their intuition is roughly right, with the nuance that the *listening channel* (daemon) has a small constant cost, and give numbers.

Keep response structured: 1) 直接回答他们的判断（对/不对在哪）2) 修订后的架构（长轮询）3) 延迟/流量/电量表 4) 变更清单 5) 等确认。

No file edits allowed (plan mode). Just text.

你的判断基本正确，但有一个关键盲区，作为架构师必须先讲透：

## 先纠正一个认知：主动模式的成本大头不是"按钮"，而是"手机随时能被叫醒"

浏览器点按钮那一刻确实几乎零成本。但手机躲在运营商内网后面，**服务器无法主动"敲门"通知它**——手机必须养一个常驻监听通道，这才是主动模式真正的持续开销。你之前拍的"60 秒轮询"方案：命令最坏要等 60 秒才被手机发现。要做到你要的"技术最低值"，方案要升级为**长轮询（long-polling）**：

> 手机发起请求后服务器**不立即回复，把连接挂住最多 50 秒**；期间你一点按钮，命令入库，服务器立刻放手，手机瞬间收到——命令到达延迟从"最坏 60 秒"压到 **0~3 秒**。流量和 60 秒普通轮询几乎一样（每 ~50 秒一次心跳级请求）。

## 修订后的完整延迟/成本账（大白话）

**主动模式延迟拆解**（这就是纯 Termux 方案的技术下限）：

| 环节 | 耗时 |
|---|---|
| 按钮 → 命令到达手机（长轮询挂线，随到随回） | **0~3 秒** |
| GPS 锁星（室外） | 5~15 秒 |
| 上报 + 地图刷出 | 1~3 秒 |
| **合计：室外约 8~25 秒，室内降级网络定位最坏 ~40 秒** | |

再往下压到"秒级回传"只有原生 App + 厂商推送通道（小米推送/FCM）能做到——那是 Phase A 的活，Termux 阶段到此为止。

**每月流量估算**：监听心跳 ~3–5MB + 被动 1 小时上报 ~1MB + 主动忽略不计 ≈ **5MB 以内**

**电量诚实告知**：无论被动还是主动，Termux 方案都必须开唤醒锁（不让 CPU 睡死），常驻监听额外多耗约 **1~3%/天**。这是 B 方案的平台税，Phase A 原生 App 才能消掉。

## 被动模式改 1 小时带来的连锁问题（已一并解决）

被动改 1 小时后，地图现在"15 分钟没上报就标红失联"的逻辑会天天误报。解法：**监听心跳本身当"活着"的信号**——手机每 ~50 秒必然碰一次服务器，地图上"在线/失联"改看心跳，"最后轨迹点时间"单独显示。这样手机哪怕 6 小时才报一次位置，你照样实时知道它"活着、有网、能被叫醒"。

## 最终修订方案

| 模式 | 最终形态 |
|---|---|
| **被动** | cron **默认 1 小时**（setup 可改 30min~6h），只留稀疏轨迹当"活动日记" |
| **主动** | 点按钮 → **8~25 秒**出最新位置，地图自动居中 |
| **在线判定** | 改用 daemon 心跳（~50s 粒度），与被动间隔解耦 |
| **地图刷新** | 任意秒数自选 + URL 参数 + 记忆（上一版已含） |

**变更清单**（对上一版计划的增量）：
1. `schema.sql`：+`devices` 心跳表（设备名、最后心跳、最后上报）；+`commands` 表 + 原子领取 RPC
2. `api/poll.js`：升级长轮询（`wait` 参数，内部 2 秒查一次库，挂 50 秒），每次顺手更新心跳；`vercel.json` 配 `maxDuration: 60`
3. `api/command.js` 新增：POST 排队 / GET 查状态+心跳
4. `report.js`：微改支持 `cmd_id` 销单
5. `map.html`：在线判定切心跳；显示"心跳 x 秒前 / 最后轨迹点 x 分钟前"两行；获取位置按钮按 8~25 秒预期优化提示与超时
6. `agent.sh`：长轮询循环 + crond 看门狗；`setup.sh` 被动默认 60 分钟、启动 daemon；保活清单/README 同步
7. 测试：命令全生命周期 + 心跳更新 + 长轮询即时返回路径

计划就绪，回复"开始"即进入实施。

---

