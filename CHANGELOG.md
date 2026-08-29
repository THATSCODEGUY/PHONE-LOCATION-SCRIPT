# 更新日志

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
