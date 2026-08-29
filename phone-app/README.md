# Phase A · Kotlin 工业版驻留 App（规格）

> 交付条件：phone-termux（B 方案）已稳定运行 ≥ 1 周，确认服务端链路无问题后，本目录生成完整可编译工程。B 方案与 A 方案共用同一套 API，可无缝切换/并存。

## 与 Termux 版的差异（为什么要升级）

| 项 | Termux (B) | Kotlin App (A) |
|---|---|---|
| 驻留 | 依赖用户保活设置 | 前台服务 + WorkManager 双保险，系统级 |
| 定位 | termux-location 单点 | FusedLocationProvider 融合定位 |
| 断网 | 落盘 outbox 补传 | Room 数据库 + 自动补传 |
| 电量策略 | 无 | 低电量自动降频(5min→30min)，充电恢复 |
| 响铃指令 | 无 | 服务端下发指令，手机最大音量响铃 |
| 换 SIM | 无 | 检测 SIM 变更上报，附带新号码 |

## 工程范围（届时生成）

- Kotlin + minSdk 29，目标 Redmi Note 15 Pro (HyperOS)
- 前台常驻服务（通知栏）+ WorkManager 周期定位双通道
- 断网 Room 缓存补传，时间窗 30 天对齐服务端校验
- 开机自启（RECEIVE_BOOT_COMPLETED + 前台服务）
- 深度电池优化引导页（引导用户白名单，等于内置保活清单）
- 构建：Android Studio 打开即编译，无第三方密钥依赖
