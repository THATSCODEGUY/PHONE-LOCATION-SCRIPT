package com.thatscodeguy.phonelocation

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.UserManager
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.util.Calendar
import kotlin.concurrent.thread

class TrackerService : Service() {

    companion object {
        const val CHANNEL_ID = "tracker"
        const val NOTIF_ID = 1
        // v2.4.1 免费额度省费: 短轮询 + 客户端间隙 → 占空比 ~15% (原 45s 死挂 ≈ 100%)
        const val POLL_HANG_SEC = 5
        const val POLL_GAP_MS = 25_000L
        // v2.4.2: 工作窗口改由地图端远程配置 (Prefs cfg_*), 此处仅保留探活节奏
        const val SLEEP_CHUNK_MS = 10 * 60_000L
        const val WATCHDOG_MIN = 15L
        const val LOW_BATTERY_PCT = 20
        const val POWER_GUARD_PCT = 5
        const val POWER_GUARD_REPEAT_MS = 6L * 3600_000

        @Volatile
        var aliveSince: Long = 0L

        fun hasLocationPermission(ctx: Context): Boolean =
            ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context): Boolean {
            if (!Prefs.configured(ctx) || !hasLocationPermission(ctx)) return false
            return try {
                ctx.startForegroundService(Intent(ctx, TrackerService::class.java))
                true
            } catch (_: Exception) {
                false
            }
        }

        fun scheduleWatchdog(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                ctx, 1001,
                Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = System.currentTimeMillis() + WATCHDOG_MIN * 60_000L
            try {
                if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, null), pi)
                }
            } catch (_: Exception) {
            }
        }

        fun cancelWatchdog(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                ctx, 1001,
                Intent(ctx, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.cancel(pi)
        }

        // v2.4.5: 工作日判断, 支持 "1-5" / "1,3,5" / "1-3,5,7" (1=周一 ... 7=周日) — 上移伴生对象供闹钟排程复用
        private fun dayInSet(dow: Int, spec: String): Boolean {
            for (part in spec.split(',')) {
                val p = part.trim()
                if (p.isEmpty()) continue
                if (p.contains('-')) {
                    val a = p.substringBefore('-').toIntOrNull() ?: continue
                    val b = p.substringAfter('-').toIntOrNull() ?: continue
                    if (dow in a..b) return true
                } else {
                    if (p.toIntOrNull() == dow) return true
                }
            }
            return false
        }

        // v2.4.5 睡眠期探活闹钟 (requestCode 1002): 取代 Thread.sleep(10分钟)——
        // 闹钟由内核触发, 灭屏深度休眠也能准时唤醒; 线程睡眠计时在 CPU 休眠下会冻结, 曾致探活停摆数小时
        private fun sleepProbePi(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, 1002,
                Intent(ctx, AlarmReceiver::class.java).putExtra("probe", true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun scheduleSleepProbe(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val at = nextWakeAtMs(ctx)
            try {
                if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, sleepProbePi(ctx))
                } else {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, null), sleepProbePi(ctx))
                }
            } catch (_: Exception) {
            }
        }

        fun cancelSleepProbe(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            am.cancel(sleepProbePi(ctx))
        }

        // v2.4.5: 下一唤醒时刻 = min(10分钟探活, 下个运行日窗口开始整点) — 窗口开始准点恢复轮询, 不再最多迟10分钟
        private fun nextWakeAtMs(ctx: Context): Long {
            val now = System.currentTimeMillis()
            val base = now + SLEEP_CHUNK_MS
            if (!Prefs.cfgEnabled(ctx)) return base
            val startMin = Prefs.cfgWorkStart(ctx)
            val cal = Calendar.getInstance()
            for (d in 0..7) {
                val c = cal.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, d)
                c.set(Calendar.HOUR_OF_DAY, startMin / 60)
                c.set(Calendar.MINUTE, startMin % 60)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis <= now) continue
                val dow = when (c.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.TUESDAY -> 2
                    Calendar.WEDNESDAY -> 3
                    Calendar.THURSDAY -> 4
                    Calendar.FRIDAY -> 5
                    Calendar.SATURDAY -> 6
                    Calendar.SUNDAY -> 7
                    else -> 1
                }
                if (dayInSet(dow, Prefs.cfgWorkDays(ctx))) return minOf(base, c.timeInMillis)
            }
            return base
        }
    }

    @Volatile
    private var running = true
    private var loopThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastGuardTraceAt = 0L

    private val locator by lazy { Locator(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(NOTIF_ID, buildNotification("启动中…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: Exception) {
            Prefs.setLastResult(this, "服务启动失败:缺少定位权限,请到 设置→应用管理→PhoneLocation→权限→位置→始终允许")
            aliveSince = 0L
            stopSelf()
            return
        }
        holdWakeLock()
        aliveSince = System.currentTimeMillis()
        scheduleWatchdog(this)
        startLoop()
        registerNetGuard()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.getBooleanExtra("stop_now", false) == true -> {
                cancelWatchdog(this)
                cancelSleepProbe(this)
                stopSelf()
                return START_NOT_STICKY
            }
            intent?.getBooleanExtra("test_now", false) == true -> {
                thread(name = "test-report") { locateAndReport(null) }
            }
        }
        scheduleWatchdog(this)
        reviveFrozenLoopIfNeeded()
        dispatchLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        aliveSince = 0L
        try {
            netCallback?.let {
                getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    private fun startLoop() {
        running = true
        loopThread = thread(name = "tracker-loop") { loop() }
    }

    // v2.4.5 循环调度: 窗口内跑轮询线程; 窗口外不驻线程, 只靠精确闹钟探活+看门狗兜底
    // (所有入口统一走这里: 开机/看门狗/探活闹钟/打开App)
    private fun dispatchLoop() {
        if (loopThread?.isAlive == true) return
        if (inWorkWindow()) {
            startLoop()
            return
        }
        updateNotif(offWindowText())
        thread(name = "probe") {
            networkGuardTick()   // v2.5.1: 探活前兜底巡检, 睡眠期也保持限制同步与断网留痕
            probePollThrottled()
            // poll 应答可能携带新配置改变了窗口判定, 故 poll 后再定夺
            if (inWorkWindow() && loopThread?.isAlive != true) {
                startLoop()
            } else {
                scheduleSleepProbe(this@TrackerService)
            }
        }
    }

    // v2.4.5: 看门狗(15min)与探活闹钟(10min)可能先后到点, 3分钟节流防重复 poll 耗配额
    private fun probePollThrottled() {
        val now = System.currentTimeMillis()
        if (now - Prefs.lastProbeAt(this) >= 3 * 60_000L) {
            Prefs.setLastProbeAt(this, now)
            pollOnce(0)
        }
    }

    // v2.4.5: 看门狗补盲区 — 线程"活着但冻住"(CPU休眠冻结Thread.sleep, isAlive仍为true)时强制换血。
    // 阈值8分钟: 大于窗口内一轮最长耗时(定位~25s+轮询~20s+补传若干), 小于看门狗15分钟
    private fun reviveFrozenLoopIfNeeded() {
        val t = loopThread ?: return
        if (!t.isAlive) return
        val last = Prefs.lastLoopAt(this)
        if (last > 0L && System.currentTimeMillis() - last > 8 * 60_000L) {
            Prefs.setLastResult(this, "看门狗: 循环疑似冻结, 已强制重启 ${java.time.LocalTime.now().withNano(0)}")
            try {
                t.interrupt()
            } catch (_: Exception) {
            }
            startLoop()
        }
    }

    /* ---------------- v2.5.1 网络守卫 (设备所有者特权) ---------------- */

    // 事实澄清: Android 无任何公开 API 允许 App(含设备所有者)强制重开被手动关闭的移动数据
    // (setMobileNetworksEnabled 不存在于 AOSP, v2.5.0 编译失败根因)。
    // 改为"防关"策略: 定位开启期间施加 DISALLOW_CONFIG_MOBILE_NETWORKS 用户限制,
    // 尽力阻断系统设置/快捷磁贴的移动网络开关入口(HyperOS 实际效果以真机为准);
    // 地图停用定位 → 解除限制, 这是机主自由关数据的唯一通道。
    private fun syncNetworkRestriction() {
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java) ?: return
            val admin = ComponentName(this, DeviceAdmin::class.java)
            if (!dpm.isDeviceOwnerApp(packageName)) return
            val want = Prefs.cfgEnabled(this)
            val has = dpm.getUserRestrictions(admin)
                .getBoolean(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
            if (want && !has) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
                Prefs.setLastResult(this, "网络守卫: 已限制改动移动网络设置 ${java.time.LocalTime.now().withNano(0)}")
            } else if (!want && has) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
                Prefs.setLastResult(this, "网络守卫: 已恢复移动网络自由(定位停用) ${java.time.LocalTime.now().withNano(0)}")
            }
        } catch (_: Exception) {
        }
    }

    // 网络状态巡检: 断网期间留痕可见(数据被关/无信号), 恢复瞬间由 onAvailable 立即探活秒级回绿
    private fun networkGuardTick() {
        syncNetworkRestriction()
        try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!online) {
                val now = System.currentTimeMillis()
                if (now - lastGuardTraceAt > 10 * 60_000L) {
                    lastGuardTraceAt = now
                    Prefs.setLastResult(this, "网络守卫: 当前断网(数据被关/无信号), 恢复后秒级上线 ${java.time.LocalTime.now().withNano(0)}")
                }
            }
        } catch (_: Exception) {
        }
    }

    // v2.5.1 网络监听: 网络恢复→立即探活刷新心跳(秒级回绿, 不等下个闹钟/轮询)
    private fun registerNetGuard() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java) ?: return
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    networkGuardTick()
                }

                override fun onAvailable(network: Network) {
                    thread(name = "net-recover") {
                        probePollThrottled()
                    }
                }
            }
            cm.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                cb,
            )
            netCallback = cb
        } catch (_: Exception) {
        }
    }

    /* ---------------- 主循环: 配置判定 → 补传 → 被动到点上报 → 短轮询挂线 ---------------- */

    // v2.4.2: 窗口判定读远程配置 (总开关 + 星期 + 起止), 地图端改完全局生效
    // (dayInSet 已上移伴生对象 v2.4.5)
    private fun inWorkWindow(): Boolean {
        if (!Prefs.cfgEnabled(this)) return false
        val now = LocalDateTime.now()
        if (!dayInSet(now.dayOfWeek.value, Prefs.cfgWorkDays(this))) return false
        val m = now.hour * 60 + now.minute
        return m >= Prefs.cfgWorkStart(this) && m < Prefs.cfgWorkEnd(this)
    }

    private fun windowText(): String {
        val s = Prefs.cfgWorkStart(this)
        val e = Prefs.cfgWorkEnd(this)
        return "%02d:%02d~%02d:%02d".format(s / 60, s % 60, e / 60, e % 60)
    }

    private fun offWindowText(): String =
        if (!Prefs.cfgEnabled(this)) "定位已停用 · 10分钟探活中 · 地图可一键开启"
        else "窗口外休眠 · ${windowText()} · 探活10分钟/次, 到点自动恢复"

    // v2.4.2: 应用地图端下发的新配置 (立即生效, 通知可见)
    private fun applyCfg(cfg: JSONObject) {
        val enabled = cfg.optBoolean("enabled", true)
        val start = cfg.optInt("work_start", 8 * 60).coerceIn(0, 1424)
        val end = cfg.optInt("work_end", 20 * 60).coerceIn(1, 1440)
        val days = cfg.optString("work_days", "1-5")
        val ver = cfg.optLong("version", 0L)
        Prefs.setCfgEnabled(this, enabled)
        Prefs.setCfgWorkStart(this, if (start < end) start else 8 * 60)
        Prefs.setCfgWorkEnd(this, if (start < end) end else 20 * 60)
        Prefs.setCfgWorkDays(this, days)
        Prefs.setCfgVersion(this, ver)
        Prefs.setLastResult(this, if (enabled) "窗口已更新: ${windowText()} ${daysLabel(days)}" else "定位已停用(地图可开启)")
        updateNotif(if (enabled) "配置已更新: ${windowText()}" else "远端已停用定位")
        syncNetworkRestriction()   // v2.5.1: 开关切换即时联动网络限制(开=防关, 关=解禁)
    }

    private fun daysLabel(spec: String): String {
        val names = arrayOf("", "一", "二", "三", "四", "五", "六", "日")
        val sb = StringBuilder()
        for (ch in spec) if (ch in '1'..'7') sb.append(names[ch - '0'])
        return if (sb.isEmpty()) spec else sb.toString()
    }

    // 统一轮询: 带 cfgver, 应答可含 cfg(配置) 与 id(命令); 返回 null=网络异常
    private fun pollOnce(waitSec: Int): Http.Resp? {
        return try {
            val device = URLEncoder.encode(Prefs.deviceName(this), "UTF-8")
            val r = Http.get(
                Prefs.apiBase(this) + "/api/poll?device=$device&wait=$waitSec&cfgver=${Prefs.cfgVersion(this)}",
                mapOf("X-Device-Key" to Prefs.deviceKey(this)),
                timeoutMs = (waitSec + 15) * 1000,
            )
            Prefs.setPollCount(this, Prefs.pollCount(this) + 1)
            if (r.ok && !r.body.isNullOrBlank()) {
                val obj = JSONObject(r.body)
                if (obj.has("cfg")) applyCfg(obj.getJSONObject("cfg"))
                if (obj.has("id")) locateAndReport(obj.optLong("id", -1L).takeIf { it > 0 })
            }
            r
        } catch (_: Exception) {
            null
        }
    }

    private fun loop() {
        // v2.4.5: 每轮刷新 lastLoopAt 供看门狗识别冻僵; 只认自己是当前循环线程, 被换血后旧线程自动退出
        while (running && Prefs.configured(this) && Thread.currentThread() === loopThread) {
            Prefs.setLastLoopAt(this, System.currentTimeMillis())
            try {
                networkGuardTick()   // v2.5.1: 每轮兜底巡检(限制同步+断网留痕), 回调之外的保险
                // v2.4.5: 窗口外/停用 → 探活一次后交给精确闹钟接管, 线程干净退出(服务保留前台通知)。
                // 旧写法 Thread.sleep(10分钟) 在灭屏休眠下计时冻结, 探活停摆数小时, 看门狗见线程"活着"也不救——本次根治
                if (!inWorkWindow()) {
                    updateNotif(offWindowText())
                    pollOnce(0)
                    Prefs.setLastProbeAt(this, System.currentTimeMillis())
                    scheduleWatchdog(this)
                    scheduleSleepProbe(this)
                    break
                }

                val remaining = Outbox.flush(this, Prefs.apiBase(this), Prefs.deviceKey(this))

                val now = System.currentTimeMillis()
                if (now - Prefs.lastReportAt(this) >= passiveIntervalMs()) {
                    locateAndReport(null)
                }

                // 电量守护上报: 电量≤5%且未充电时, 每6小时强制刷新一次最后已知位置
                val (batt, charging) = batteryState()
                if (batt != null && batt <= POWER_GUARD_PCT && charging == false &&
                    now - Prefs.lastPowerGuardAt(this) >= POWER_GUARD_REPEAT_MS
                ) {
                    Prefs.setLastPowerGuardAt(this, now)
                    locateAndReport(null)
                }

                val r = pollOnce(POLL_HANG_SEC)
                if (r == null || r.code == -1) {
                    Thread.sleep(15_000)
                }

                // v2.4.1 免费额度省费: 正常返回后强制间隙, 拉低占空比
                Thread.sleep(POLL_GAP_MS)
                updateNotif(notifText(remaining))
            } catch (e: InterruptedException) {
                break
            } catch (_: Exception) {
                try {
                    Thread.sleep(10_000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    /* ---------------- 定位 + 上报 (cmdId 非空则销单) ---------------- */

    private fun locateAndReport(cmdId: Long?) {
        val fix = locator.get(
            lastKnownMaxMs = if (cmdId != null) 24L * 3600_000 else 10L * 60_000,
        )
        if (fix == null) {
            Prefs.setLastResult(this, "定位失败(GPS+网络均无结果)")
            return
        }
        val (battery, charging) = batteryState()
        val body = JSONObject().apply {
            put("ts", Instant.now().toString())
            put("lat", fix.lat)
            put("lng", fix.lng)
            put("provider", fix.provider)
            put("device", Prefs.deviceName(this@TrackerService))
            fix.accuracy?.let { put("accuracy", it) }
            fix.speed?.let { put("speed", it) }
            fix.bearing?.let { put("bearing", it) }
            fix.altitude?.let { put("altitude", it) }
            battery?.let { put("battery", it) }
            charging?.let { put("charging", it) }
            currentSsid()?.let { put("ssid", it) }
            cmdId?.let { put("cmd_id", it) }
        }
        val r = Http.post(
            Prefs.apiBase(this) + "/api/report",
            body.toString(),
            mapOf("X-Device-Key" to Prefs.deviceKey(this)),
        )
        if (r.ok) {
            Prefs.setLastReportAt(this, System.currentTimeMillis())
            Prefs.setLastResult(this, "上报成功(cmd=${cmdId ?: "被动"})")
        } else {
            Outbox.push(this, body.toString())
            Prefs.setLastResult(this, "上报失败落盘(HTTP ${r.code})")
        }
    }

    /* ---------------- 电量/充电/Wi-Fi/被动间隔 ---------------- */

    private fun batteryState(): Pair<Int?, Boolean?> {
        return try {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else null
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_STATUS_FULL,
                -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                -> false
                else -> null
            }
            Pair(pct, charging)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun currentSsid(): String? {
        return try {
            val wm = getSystemService(WIFI_SERVICE) as WifiManager
            val ssid = wm.connectionInfo?.ssid ?: return null
            if (ssid.isBlank() || ssid == "<unknown ssid>") null
            else ssid.removeSurrounding("\"").take(64).ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun passiveIntervalMs(): Long {
        var min = Prefs.passiveMin(this).toLong()
        val (batt, charging) = batteryState()
        if (batt != null && charging == false) {
            if (batt <= POWER_GUARD_PCT) min *= 8
            else if (batt <= LOW_BATTERY_PCT) min *= 4
        }
        return min * 60_000
    }

    /* ---------------- 通知 / 唤醒锁 ---------------- */

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "定位守护", NotificationManager.IMPORTANCE_LOW).apply {
                description = "常驻定位上报服务"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneLocation 运行中")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    private fun updateNotif(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    private fun notifText(outboxRemaining: Int): String {
        val last = Prefs.lastReportAt(this)
        val agoMin = if (last == 0L) -1 else (System.currentTimeMillis() - last) / 60_000
        val base = when {
            agoMin < 0 -> "尚无上报"
            agoMin == 0L -> "刚刚上报"
            else -> "${agoMin} 分钟前上报"
        }
        return if (outboxRemaining > 0) "$base · 待补传 $outboxRemaining 条" else base
    }

    private fun holdWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonelocation:tracker").apply {
            setReferenceCounted(false)
            acquire()
        }
    }
}
