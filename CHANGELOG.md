# 更新日志

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
