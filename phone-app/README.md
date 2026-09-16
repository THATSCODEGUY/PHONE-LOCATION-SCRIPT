# Phase A · 原生 App 工业版（手机端零维护）

> Termux（方案 B）的保活天花板是用户空间 App，HyperOS 一次后台清理就停。本方案是**系统级前台服务**：常驻通知划不掉、被回收系统自动重拉、开机自启，配合看门狗闹钟三层保险。**电脑装配一次，此后手机端永不操作。**

## 与 Termux 方案对比

| 项 | Termux (B, 备胎) | 原生 App (A, 主推) |
|---|---|---|
| 驻留原理 | 用户空间脚本 + 保活清单 | 前台服务 + START_STICKY + 精确闹钟看门狗 + 开机广播 |
| 手机端操作 | 多（权限/锁定/省电逐项） | **电脑一条 bat，手机零点击** |
| 定位 | termux-location | 系统 LocationManager（GPS→网络→最近已知 三级降级） |
| 断网 | outbox.jsonl | 同设计（应用私有目录落盘补传） |
| 防卸载/强停 | 无 | 设备所有者模式可封死 |
| 升级 | 重跑安装命令 | 下载新 APK 覆盖装（固定签名，配置不丢） |

## 电脑装配（推荐路径，10 分钟）

1. **下载 APK**：GitHub → Releases → 最新版 → `PhoneLocation-vX.Y.Z.apk` → 放到一个文件夹
2. **下载 adb**：搜 "Android platform-tools" 官方下载解压，把 adb.exe 所在目录加入 PATH（或丢进同一文件夹）
3. **手机开 USB 调试**：设置 → 我的设备 → 全部参数 → 连点「OS 版本」7 次 → 开发者选项 → USB 调试开
4. **数据线连电脑**，手机弹「允许 USB 调试」点允许
5. 把仓库 `phone-app/setup-phone.bat` + APK（改名 `PhoneLocation.apk`）放同一目录，**双击运行 bat**，按提示粘贴一次 DEVICE_KEY

bat 自动完成：安装 → 定位/通知权限授予（免弹窗）→ 电池白名单 → 精确闹钟 → 写入配置启动服务 → 设备所有者（防卸载/强停）。

6. 打开地图 `https://<你的域名>/map?token=<ACCESS_TOKEN>` → 心跳「在线」→ 点 [获取位置] 验收

## 手动安装（无电脑时的备选）

手机浏览器下载 APK 安装 → 打开 App → 填 API_BASE / DEVICE_KEY → 保存并启动 → 权限逐个允许（位置选**始终允许**）→ 手动做两件事：设置里开**自启动** + 省电策略**无限制**。

## 三层保活原理

| 层 | 机制 | 防什么 |
|---|---|---|
| 1 | 前台服务（location 类型）+ 常驻通知 | 普通后台清理、划卡片 |
| 2 | START_STICKY + 15 分钟精确闹钟看门狗（无精确闹钟权限自动降级 setAlarmClock） | 进程被回收后自愈 |
| 3 | BOOT_COMPLETED 开机广播 | 重启 |
| + | 设备所有者（L3，可选） | 强停/卸载/重启后自启全封死 |

## 主循环（与服务端已验收 API 完全一致）

```
while(true):
  0. 配置判定(地图端远程下发: 总开关+时段+星期, 版本号比对增量更新):
     窗口外/停用 → 每10分钟探活一次(wait=0 poll: 收新配置+紧急定位命令), 零轨迹零上报
  1. outbox 补传(失败上报逐条重发)
   2. 距上次上报 ≥ 被动间隔(60min, 低电量≤20%×4 / ≤5%×8 省电, ≤5%时每6h刷新末位) → 定位+上报
  3. 短轮询挂线 GET /api/poll?wait=5&cfgver=N (X-Device-Key) → 强制间隙25s (占空比~15%, v2.4.1 免费额度省费)
     └ 应答含 id → 立即定位+上报(带cmd_id销单) → 地图约 0.5~1 分钟出新点
     └ 应答含 cfg → 保存新配置立即生效(窗口/开关), 通知栏+状态页可见
```

## CI 构建（维护者用）

- push 到 main → GitHub Actions 自动编译，Artifacts 里取 APK
- 打 tag（`git tag v2.3.0 && git push --tags`）→ 自动创建 Release 挂 APK，手机浏览器直接下载
- 签名：`signing/app.p12`（固定入库，30 年有效期，密码 phonelocation）——升级包永远可覆盖安装

## 排查速查

| 症状 | 处理 |
|---|---|
| bat 找不到 adb | platform-tools 未加 PATH，或 adb.exe 没和 bat 同目录 |
| 设备所有者设置失败 | 手机退出全部账号（含小米账号）后重跑 bat；或跳过（代价：重启后手动开一次 App） |
| 心跳在线但定位慢 | 室内 GPS 弱，等网络定位兜底（10 秒）或到窗边 |
| 地图「失联」 | 看 App 状态页「最近结果」；飞机模式/欠费/服务器 Deploy 失败三类最常见 |
| 想解除设备所有者 | App 状态页一键「解除设备所有者」(v2.3.1+)；或电脑 `adb shell dpm remove-active-admin com.thatscodeguy.phonelocation/.DeviceAdmin` |
