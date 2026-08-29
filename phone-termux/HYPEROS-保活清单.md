# HyperOS 保活清单 · Redmi Note 15 Pro

> Termux 方案的最大敌人是系统杀后台。以下每项都必须完成，缺一项就可能出现"息屏后不再上报"。
> 按顺序逐项打勾。菜单名称基于 HyperOS 中文版，个别版本措辞略有差异，找不到时在设置内搜索关键词。

## 0. 前置条件

- [ ] 手机已开启 **位置信息**（下拉控制中心 → 位置信息 = 开）
- [ ] 定位模式选择 **高精度**（设置 → 位置信息 → 高精度定位：GPS + 网络）
- [ ] SIM 卡已开通流量，且 Termux 联网权限允许 **WLAN 与数据网络**（设置 → 应用管理 → Termux → 联网控制）
- [ ] 已从 **F-Droid** 安装三件套并各打开一次：
  - Termux、Termux:API、Termux:Boot
  - ⚠ 必须 F-Droid 版（Google Play 版 Termux 已停更且无 API 权限）

## 1. 权限设置（决定定位能否成功）

- [ ] 设置 → 隐私保护 → 权限管理 → **Termux** → 位置 → **始终允许**
  - 关闭"仅使用期间允许"，必须"始终允许"，否则息屏后拿不到定位
- [ ] 权限管理 → Termux → **通知** → 允许（常驻通知是保活的一部分）
- [ ] Termux:API、Termux:Boot 同样允许通知和自启动

## 2. 防杀后台（决定脚本能活着）

- [ ] 最近任务界面 → 找到 Termux 卡片 → **长按 → 锁定**（出现挂锁图标）
- [ ] 设置 → 应用设置 → 应用管理 → Termux → **省电策略 → 无限制**
- [ ] 设置 → 应用设置 → 应用管理 → Termux → **自启动 → 开**
- [ ] 设置 → 应用设置 → 应用管理 → Termux → **关联启动 → 开**（被其他 App 唤醒时不被拦）
- [ ] 设置 → 电量与性能 → 应用智能省电 → Termux → **无限制**（若有此项）
- [ ] 设置 → 电池 → 关闭"锁屏后清理后台"类选项（若有）
- [ ] 开发者选项 → 正在运行的服务 → 确认存在 **agent.sh / bash** 进程（验证 agent 活着）

## 3. 唤醒锁（防止 CPU 休眠跳过上报与轮询）

- [ ] Termux 内执行 `termux-wake-lock`，通知栏出现 "Termux 已获取唤醒锁" 常驻通知
- [ ] 该命令已由 setup.sh 自动执行并写入开机脚本，这里只需确认通知存在

## 4. 开机自启验证

- [ ] 已安装 Termux:Boot 并**打开过一次**（必须，否则收不到开机广播）
- [ ] 重启手机 → 不打开任何 App → 等 3 分钟 → `tail phone-loc/agent.log` 应有"守护启动"，`tail phone-loc/report.log` 应有被动上报记录

## 5. 终极验收（模拟丢失场景）

- [ ] 锁屏放置 **30 分钟** → 浏览器打开 /map → 心跳仍显示"在线"（秒级/分钟级刷新）
- [ ] 关闭 WiFi 只走流量 → 重复上面测试
- [ ] 换个地方出门携带 → 地图轨迹连续移动
- [ ] **主动模式验收**：锁屏状态下点地图 **[获取位置]** 按钮 → 8~25 秒内出新点并自动居中

## 6. 常见故障速查

| 症状 | 原因 | 处理 |
|---|---|---|
| 日志"定位彻底失败" | 位置权限非"始终允许" / 位置开关关 | 清单 §1 |
| 息屏后断更、亮屏恢复 | 省电策略未无限制 / 未锁定后台 | 清单 §2 |
| 重启后不再上报 | Termux:Boot 未打开过 / agent 未起 | 清单 §4，手动 `nohup bash ~/phone-loc/agent.sh >/dev/null 2>&1 &` |
| 每次都比预期晚很多上报 | 系统静置过久，GPS 锁星慢 | 正常现象，agent 每 15 分钟看门狗自查 |
| 每次都降级 network | 室内 GPS 信号弱 | 正常现象，到室外验证 gps |
| "API app not installed" | 装了 Play 版或没装 Termux:API | F-Droid 重装三件套 |
| outbox 持续增长 | 服务器/网络故障 | 检查 Vercel 部署与手机流量 |

## 7. Windows 传输脚本后的换行符修复

在 Windows 上编辑/解压过的 .sh 传到手机后若报错 `bad interpreter`，在 Termux 执行：

```bash
pkg install dos2unix
dos2unix ~/phone-loc/*.sh
```

## 8. agent 统一守护（被动+主动）专项检查

`agent.sh` 是唯一常驻进程：长轮询命令通道（[获取位置] 按钮响应）+ 时间制被动上报。**cron 只做看门狗**（每 15 分钟检查 agent，死了自动拉起）。必须确认它活着：

- [ ] Termux 执行 `pgrep -f agent.sh` → 有 PID 输出
- [ ] `tail -n 20 ~/phone-loc/agent.log` → 能看到"守护启动"记录，无异常刷屏
- [ ] `tail -n 5 ~/phone-loc/watchdog.log` → 无频繁"已重启"刷屏（有则说明 agent 老被杀）
- [ ] 点一次地图 [获取位置] → agent.log 出现"收到命令 #N ... 完成"
- [ ] 手动杀掉测试自愈：`pkill -f agent.sh` → 等 16 分钟看 watchdog.log 被拉起（或手动 `nohup bash ~/phone-loc/agent.sh >/dev/null 2>&1 &`）

| 症状 | 原因 | 处理 |
|---|---|---|
| 点[获取位置]超时(75秒) | agent 被系统杀 / 手机断网 | 上表检查 + 清单 §2 |
| 按钮转圈但心跳"在线" | agent 活着但 GPS 锁星慢/失败 | 到窗边重试，看 report.log 是否降级 network |
| watchdog.log 频繁"已重启" | 系统反复杀 agent | 强化清单 §2，考虑开启 Vercel 地图页做热备份 |
| agent.log 大量 HTTP 000 | 网络不稳/服务器故障 | 检查流量与 Vercel 状态 |
| agent.log 增长过快 | 异常循环刷日志 | `> agent.log` 清空并重启 agent 排查 |
