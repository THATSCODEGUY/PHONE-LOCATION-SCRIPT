# 更新日志

## v2.4.3 — 地图「运行控制」改折叠浮层（修复遮挡定位信息卡）

**问题**：v2.4.2 的设置面板复用 `.panel` 样式（绝对定位左上角），加载成功即自动显示，正好盖住同位置的定位信息状态卡。

**修复（仅 `map.html`，纯网页端，App/Supabase 零改动、不发 APK）**
- 地图默认版面与旧版完全一致，面板不再自动弹出
- 右侧按钮列新增「⚙ 运行控制」入口：点击展开浮层（z-index 1100，临时盖住状态卡）→ 保存成功或点「✕ 收起」自动收起
- 配置改为打开时按需拉取；时间输入框适配暗色主题（color-scheme: dark）
- 升级方式：push → Vercel 自动部署 → 浏览器 Ctrl+F5 即见

## v2.4.2 — 地图端远程控制（总开关 + 窗口全可调，最后一次重装）

**目标**：把控制权交给地图。改窗口/开关不再改代码重装 APK——地图点几下，手机 32 秒内（休眠期 ≤10 分钟）照办。

**新增能力（地图「运行控制」面板）**
- **总开关**：关=手机停止一切定位/上报/轨迹，只留 10 分钟探活；开=一键恢复
- **时段**：开始/结束时间 00:00~23:59 任选（暂不支持跨午夜，跑整夜用 00:00~23:59）
- **运行日**：周一~周日 7 勾选任意组合（周末想开就开）
- 地图加载自动回显当前配置；保存后 toast 提示生效时限

**实现（手机复用每 32 秒既有的轮询通道捎带配置）**
- Supabase 新表 `phonelocation_config`（enabled/work_start/work_end/work_days/version，带约束，幂等）
- 新端点 `api/config.js`（GET/POST，ACCESS_TOKEN 鉴权，严格校验：HH:MM、start<end、星期 1~7 集合）
- `api/poll.js`：客户端携带 `cfgver` 且服务端版本更新时应答捎带 `cfg`；**不带参数=行为与历史逐字节一致，旧 App 零影响**
- App（versionCode 6）：窗口判定/开关改读远程配置；收到新配置立即生效并写入「最近结果」与通知栏；窗口外/停用时每 10 分钟 wait=0 探活一次（收新配置+紧急定位命令→**夜间/停用期点[获取位置] ≤10 分钟也能应答**）；状态页新增「版本/定位开关/工作窗口」三行
- 地图失联阈值 5→12 分钟（适配探活粒度）；修复隐患：poll 应答仅含 `id` 才触发定位（cfg 应答不再误触发）

**配额影响**：探活每 10 分钟 1 次 wait=0 ≈ +4 GB-Hrs/月 + 0.5h CPU + ~4K 调用（各约 1%），合计 ~84 GB-Hrs/月（23%）🟢

**升级指引**：① Supabase 重跑 `schema.sql`（幂等，新增 config 表）② push → Vercel 自动部署 ③ 打 tag `v2.4.2` → Release 下载 APK 覆盖安装（**本版之后调窗口/开关全部在地图操作，永久免重装**）。测试 47 → **58 项**全过（config 校验/cfg 捎带/旧客户端兼容/命令+cfg 同帧下发）

## v2.4.1 — App 端省费落地 + 移除 Termux 备胎（单一手机端收敛）

**背景**：v2.4.0 只修了服务端与 Termux 端，但实际在跑的唯一手机端是 **PhoneLocation App**——其 `TrackerService` 挂线 45s 且 204 后立即重连，服务端钳制后占空比仍 ~75%，月耗 ~380 GB-Hrs 依旧超标。本版补齐 App 侧修复，并按决策删除弃用的 Termux 备胎方案（仓库清洁；躺在仓库里本不耗额度，真凶始终是"正在运行的客户端"）。

**PhoneLocation App（versionCode 5）**
- `TrackerService.kt`：挂线 `POLL_HANG_SEC` 45→**5s**（与服务端硬限一致）
- 新增轮询间隙 `POLL_GAP_MS=25s`（正常返回后强制休眠）→ **占空比 ~15%**（原 ~100%）
- 新增**工作窗口**：仅周一~五 **08:00~20:00** 运行；窗口外循环休眠（每 10 分钟醒来看表），通知栏显示"窗口外休眠"，同时暂停被动上报/补传/电量守护
- 窗口常量集中在 companion object（`WORK_START_MIN`/`WORK_END_MIN`），改窗口只动一处

**移除 Termux 备胎**
- 删除整个 `phone-termux/`（agent/setup/report/watchdog/install.sh + HYPEROS-保活清单）
- `.gitignore`/`.gitattributes` 同步清理；根 README 重写为单一 App 方案（架构图/部署/调优表/升级流程/边界表）；phase0 清单 L1 行更新
- docs/技术实现全记录.md 中的 Termux 提及为历史演进记录，保留不动

**地图页**
- [获取位置] 超时 75→**90s**（给轮询间隙留余量）；两处"8~25 秒"文案改为"约 0.5~1 分钟"并注明夜间/周末休眠期无响应

**用量测算（周一~五 08:00~20:00）**
- 窗口 264h/月 × ~15% 占空比 × 2GB ≈ **~80 GB-Hrs/月**（上限 360，22%）🟢
- Active CPU ≈ 2.5h/4h 🟢；Invocations ≈ 30K/1M 🟢

**升级指引**：push 后打 tag `v2.4.1` → CI 自动发 Release 挂 APK → 手机浏览器下载覆盖安装（同签名配置保留，`MY_PACKAGE_REPLACED` 自动重启服务）。验收：窗口外通知栏显示"窗口外休眠"；Vercel Usage 曲线夜间躺平。

## v2.4.0 — 免费额度省费（短轮询占空比 + 工作窗口，解除 Vercel Fluid 超标警报）

**背景**：免费版 Fluid Provisioned Memory 达 492.4 / 360 GB-Hrs（136.8% 🔴）、Active CPU 3h26m / 4h（85.8% 🟡）。根因：手机端 agent 长轮询挂线 50s 且 204 后 1 秒内立即重连，函数 7×24 常驻；29K 次调用 × 平均 ~61s ≈ 246 小时存活 × 2GB = 492 GB-Hrs，与账单分毫吻合。**注意：Hobby 版函数内存固定 2GB 不可配置**（vercel.json 写 memory 会被忽略并警告），故省费只能压"挂线时长"。

**服务端**
- `api/poll.js`：`wait` 上限 55→**5 秒**（默认 50→5）——服务端硬限，旧手机端未升级也立即止血
- `api/poll.js`：过期命令清扫加 **10 分钟时间门控**（暖实例级），不再每次 poll 都全表 PATCH，Active CPU 大降；心跳 upsert 保持每次必刷（在线判定不受影响）
- `vercel.json`：`poll.js` maxDuration 60→**15**

**手机端（agent.sh v2.4）**
- 轮询节奏：挂线 `POLL_WAIT`(≤5s) → 客户端间隙 `POLL_GAP`(默认 25s) → **占空比 ~17%**（原 ~100%）
- **工作窗口**：仅 `WORK_DAYS`(默认 1-5 周一到周五) 的 `WORK_START~WORK_END`(默认 0800~1800) 运行；窗口外进程不退出、每 10 分钟醒来看表，到点自动恢复（watchdog / 开机自启无需任何改动）
- 旧配置 `AGENT_INTERVAL` 自动废弃并记日志；`setup.sh` 新增 5 个可调项（POLL_WAIT / POLL_GAP / WORK_START / WORK_END / WORK_DAYS）

**行为变化（预期）**
- 主动定位：8~25 秒 → **约 0.5~1 分钟**（多等一个间隙周期）；定位精度、被动上报逻辑不变
- 夜间/周末手机休眠不上报，地图显示"失联"属预期；窗口开启后自动补一次被动上报

**用量测算（默认配置，工作日 8:00~18:00）**
- Provisioned Memory：~220h 窗口 × 17% 占空比 × 2GB ≈ **~75 GB-Hrs/月**（上限 360，21%）🟢
- Active CPU：~2 次调用/分 × 清扫门控 ≈ **~2.2h/月**（上限 4h，55%）🟢
- Invocations：~26K/月（上限 1M）🟢；最坏情况（窗口拉满 7:30~20:00）≈ 92 GB-Hrs + 2.75h CPU，仍安全

**⚠ 升级指引（两步都要做）**：① push 后 Vercel 自动重部署（服务端钳制立即生效）；② **手机必须重跑 `bash install.sh` 或 `setup.sh` 更新 agent.sh**——旧 agent 仍会以 50s 挂线+立即重连方式轮询，仅靠服务端钳制占空比仍高达 ~70%，压不回安全线。测试 44 → **47 项**全过（新增 wait 钳制/清扫门控/心跳保持用例）

## v2.3.4 — 地图按钮样式修复

- 「获取位置」「Google 导航」弃用 primary 特殊样式，统一为与其他按钮一致的标准样式（修复部分环境下文字不可见）

## v2.3.3 — 自愈提速 + 地图双点导航（Google 直跳）

**App（versionCode 4）**
- 新增「应用更新完成」接收器（MY_PACKAGE_REPLACED）：覆盖安装完成瞬间自动重启服务+重挂闹钟，升级后秒级恢复，告别 0~15 分钟空窗
- 看门狗闹钟/开机/更新自启触发时写入状态页「最近结果」——自愈过程肉眼可见
- 打开 App 自动拉起未运行的服务（免点按钮）

**地图**
- 「我的位置」按钮：浏览器定位（米级）优先，拒绝授权自动退回 IP 粗定位，绿色圆点标注
- 状态卡新增「与我相距」：目标手机与我的直线距离实时显示
- 「Google 导航」按钮：一键唤起 Google Maps（App/网页）导航至目标最新位置，起点由 Google 自动定位

## v2.3.2 — 用语吉利化 + 技术全记录文档

- **全库统一中性用语**：电量守护上报（原"临终遗言"）、系统级保护（原"防杀防卸载"）、后台被清理（原"后台被杀"）、异常退出（原"崩溃/崩进程"）；代码常量与偏好键同步更名（DYING_\* → POWER_GUARD_\*，纯重命名零逻辑变化）
- App 状态页「设备所有者」显示文字、地图超时提示语同步更新
- 新增 `docs/技术实现全记录.md`——大白话全流程文档（术语表 / 四阶段实现 / 日常运维手册 / 17 个关键卡点与解法 / 排查决策树）
- versionCode 3

## v2.3.1 — 手机端加固（更稳/更准/更省）

- **自解除按钮**：App 状态页新增「解除设备所有者」（仅 Owner 状态时显示）——手机上一键恢复普通应用身份，封死"恢复出厂"极端路径
- **权限预检 + 异常兜底**：服务启动前检查定位权限；startForeground 失败不再异常退出进程，状态页明示「缺少定位权限」及处理路径
- **定位降级兜底**：主动命令定位失败时用「最近已知位置」(24h 内)兜底上报，[获取位置] 按钮告别空转
- **电量守护上报**：电量 ≤5% 未充电时每 6 小时强制刷新最后已知位置；被动间隔 ≤20% ×4、≤5% ×8 深度省电
- **失联阈值 3→5 分钟**（服务端 map.html），消灭定位窗口期误报
- **默认设备名改为 primary**（与地图默认一致，永绝改名错位）；versionCode 2

## v2.3.0 — Phase A：原生 App 工业版（手机端零维护）

**新增 phone-app/ 完整可编译工程**
- Kotlin 前台服务 App（minSdk 29 / target 34，**零第三方依赖**，纯系统 API，国行无 GMS 完全兼容）
- 三层保活：前台常驻服务(START_STICKY) + 15 分钟精确闹钟看门狗（无精确闹钟权限自动降级 setAlarmClock）+ 开机自启广播
- 复用服务端同一套已验收 API（report/poll）：GPS→网络→最近已知三级定位降级，断网 outbox 落盘补传
- 上报 battery/charging/ssid 完整环境信息；低电量(≤20%)被动间隔自动×4，充电恢复
- **adb 零操作配置口**：`setup-phone.bat` 一条脚本完成 安装→权限授予(免弹窗)→电池白名单→精确闹钟→写入配置→启动服务
- L3 设备所有者：bat 第 7 步 `dpm set-device-owner` 防卸载/防强停/开机必自启（恢复出厂或 remove-active-admin 可解除）
- 固定签名密钥入库（PKCS12，30 年），CI 构建的 APK 永远可覆盖升级不丢配置

**CI 构建链**
- GitHub Actions 云端编译（无需本地 Android Studio）；push 自动构建，打 `v*` tag 自动发 Release 挂 APK，手机浏览器直接下载

**文档**
- `phone-app/README.md` 重写（装配流程/三层保活/排查）；根 README 手机端改为 A 主推 + B 备胎双方案

**服务端零改动**（复用 9/9 线上验收的 API 链路）

## v2.2.2 — Supabase 共用库表前缀（phonelocation_）

- **共用数据库适配**：本系统所有表/视图/RPC/索引一律 `phonelocation_` 小写前缀（`phonelocation_locations` / `phonelocation_commands` / `phonelocation_devices` / `phonelocation_v_latest_location` / `phonelocation_claim_next_command()`），与其他项目共用同一 Supabase 项目互不干扰（小写免引号，规避 Postgres 大小写折叠坑）
- 服务端 4 个 API 的数据库 REST 路径全量同步（report/poll/command/locations，含 RPC 调用）
- **修复**：`locations.js` 的 select 字段漏掉 `charging`/`ssid`，导致地图状态卡永远显示不出充电/Wi-Fi（v2.2.0 遗留 bug）
- 测试 43 → **44 项**（新增 select 字段回归用例）
- ⚠ 若曾用旧版 schema 建过无前缀表，重跑新 schema 后旧表不会自动删除，可手动清理

## v2.2.1 — 仓库地址固化（首次上线）

- `install.sh` 的 `REPO_BASE` 写入真实仓库 `THATSCODEGUY/PHONE-LOCATION-SCRIPT`，手机一键安装命令开箱即用
- `README.md` 部署流程同步替换占位符为真实 raw 直链
- 纯配置/文档变更，无代码逻辑改动

## v2.2.0 — 单一守护加固（消灭 Doze 定时隐患 + 环境信息 + 一键安装）

**守护架构重构**
- `agent.sh` 升级为**唯一常驻进程**：长轮询命令通道 + **时间制被动上报**（活跃循环驱动，天然不受安卓 Doze 定时器推迟影响）
- **cron 降级为纯看门狗**：`watchdog.sh` 每 15 分钟检查 agent，死了自动拉起（自愈冗余保留）
- 移除 cron 定时上报依赖 → 少一套保活、少一份 Doze-vs-定时顾虑

**环境信息上报（看护场景）**
- `report.sh` 新增上报**充电状态**（charging）+ **Wi-Fi SSID**（ssid），Wi-Fi 关闭时优雅容错绝不卡死
- `locations` 表新增 `charging`/`ssid` 列（幂等 ALTER，老库重跑 schema 即升级）
- `report.js` 校验并入库新字段（可选、类型安全、长度限制）
- 地图状态卡新增"充电 / Wi-Fi"两行，气泡同步显示

**地图轨迹清晰化**
- 新增 **[隐藏低精度]** 开关：精度 >150m 的轨迹段以灰色虚线弱化/隐藏，看护轨迹不毛刺

**一键安装（公有 GitHub 直链）**
- 新增 `install.sh`：手机 Termux 一条命令下载全套脚本 + 保活清单并自动安装（SHA 由 TLS + 自身仓库保证，不含任何密钥）
- `setup.sh`/`HYPEROS-保活清单.md` 同步更新（关联启动、开发者选项运行服务验证、watchdog 专项）

**其他**
- 仓库卫生：`session-ses_fceb.md` 已移出版本控制并 gitignore（公开仓库必做）
- 测试 36 → **43 项**（新增 charging/ssid 校验与入库用例）

**升级指引**：Supabase 重跑 schema.sql（幂等）→ `vercel --prod` → 手机重跑 `install.sh` 或 `setup.sh`

## v2.0.0 — 双模式定位（被动省电 + 主动秒级）

**被动模式（省电重构）**
- 被动上报间隔默认 5 分钟 → **60 分钟**（setup 可调 5~1440 分钟），日常仅留稀疏轨迹，流量约 1MB/月
- 地图"在线/失联"判定与被动间隔解耦：改用 agent 心跳（~50 秒粒度），手机 6 小时不报位置照样实时知道"活着、有网、能被叫醒"

**主动模式（新增）**
- 地图新增 **[获取位置]** 按钮 → 长轮询命令通道 → **8~25 秒**出最新位置并自动居中（纯 Termux 方案技术下限）
- `agent.sh` 常驻守护：挂线长轮询（`FOR UPDATE SKIP LOCKED` 原子领取，防双领）+ crond 看门狗
- 命令 10 分钟未领取自动 expired，懒清理不占定时任务

**地图面板**
- 自动刷新间隔可自选任意秒数（预设 5/10/15/30/60 + 自定义 1~300），URL `&interval=` 参数 + localStorage 记忆
- 状态卡新增"心跳"行；原"最后上报"语义改为"最后轨迹点"
- toast 反馈：命令排队 / 获取成功 / 手机未响应提示

**其他**
- 新表：`commands`（命令队列）、`devices`（心跳）；新 RPC：`claim_next_command()`
- 新端点：`api/poll.js`（长轮询领取，maxDuration 60s）、`api/command.js`（创建命令/查状态/心跳）
- `report.js` 支持 `cmd_id` 销单 + 上报刷新 `last_report` 心跳
- `.gitattributes` 强制 `.sh` LF 行尾，杜绝 CRLF 破坏 Termux 脚本
- 测试 22 → **36 项**全过（新增命令全生命周期/长轮询即时命中/心跳链路）

**升级指引**：Supabase 重跑 schema.sql（幂等）→ `vercel --prod` → 手机重跑 `bash setup.sh`

## v1.0.0 — 被动定时上报定位系统

- Termux 定时上报（GPS→网络降级、outbox 断网补传、电量）
- Vercel 双 API（report/locations）+ Supabase RLS 全锁 + Leaflet 地图面板
- HyperOS 保活清单、Phase 0 厂商兜底清单、丢机应急 SOP
