package com.thatscodeguy.phonelocation

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import kotlin.concurrent.thread

class TrackerService : Service() {

    companion object {
        const val CHANNEL_ID = "tracker"
        const val NOTIF_ID = 1
        const val POLL_HANG_SEC = 45
        const val WATCHDOG_MIN = 15L
        const val LOW_BATTERY_PCT = 20

        @Volatile
        var aliveSince: Long = 0L

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, TrackerService::class.java))
            } catch (_: Exception) {
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
    }

    @Volatile
    private var running = true
    private var loopThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val locator by lazy { Locator(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("启动中…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        holdWakeLock()
        aliveSince = System.currentTimeMillis()
        scheduleWatchdog(this)
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent?.getBooleanExtra("stop_now", false) == true -> {
                cancelWatchdog(this)
                stopSelf()
                return START_NOT_STICKY
            }
            intent?.getBooleanExtra("test_now", false) == true -> {
                thread(name = "test-report") { locateAndReport(null) }
            }
        }
        scheduleWatchdog(this)
        if (loopThread?.isAlive != true) startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        aliveSince = 0L
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

    /* ---------------- 主循环: 补传 → 被动到点上报 → 长轮询挂线 ---------------- */

    private fun loop() {
        while (running && Prefs.configured(this)) {
            try {
                val remaining = Outbox.flush(this, Prefs.apiBase(this), Prefs.deviceKey(this))

                val now = System.currentTimeMillis()
                if (now - Prefs.lastReportAt(this) >= passiveIntervalMs()) {
                    locateAndReport(null)
                }

                val device = URLEncoder.encode(Prefs.deviceName(this), "UTF-8")
                val r = Http.get(
                    Prefs.apiBase(this) + "/api/poll?device=$device&wait=$POLL_HANG_SEC",
                    mapOf("X-Device-Key" to Prefs.deviceKey(this)),
                    timeoutMs = (POLL_HANG_SEC + 15) * 1000,
                )
                Prefs.setPollCount(this, Prefs.pollCount(this) + 1)

                if (r.ok && !r.body.isNullOrBlank()) {
                    val cmd = JSONObject(r.body)
                    locateAndReport(cmd.optLong("id", -1L).takeIf { it > 0 })
                } else if (r.code == -1) {
                    Thread.sleep(15_000)
                }

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
        val fix = locator.get()
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
        if (batt != null && batt <= LOW_BATTERY_PCT && charging == false) min *= 4
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
