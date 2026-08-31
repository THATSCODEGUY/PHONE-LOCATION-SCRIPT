# 更新日志

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
