package com.thatscodeguy.phonelocation

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        const val DEFAULT_API_BASE = "https://phone-location-script.vercel.app"
    }

    private var pendingStart = false

    private lateinit var etApi: EditText
    private lateinit var etKey: EditText
    private lateinit var etName: EditText
    private lateinit var etMin: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // adb 自动配置口: am start --es device_key ... 零操作写入并启动
        val dk = intent.getStringExtra("device_key")
        if (!dk.isNullOrBlank()) {
            val api = intent.getStringExtra("api_base")
            if (!api.isNullOrBlank()) Prefs.setApiBase(this, api)
            else if (Prefs.apiBase(this).isEmpty()) Prefs.setApiBase(this, DEFAULT_API_BASE)
            Prefs.setDeviceKey(this, dk)
            intent.getStringExtra("device_name")?.let { Prefs.setDeviceName(this, it) }
            val pm = intent.getIntExtra("passive_min", 0)
            if (pm > 0) Prefs.setPassiveMin(this, pm)
            if (TrackerService.hasLocationPermission(this)) {
                TrackerService.start(this)
                Toast.makeText(this, "配置完成，定位服务已启动", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    this,
                    "配置已写入，但缺少定位权限：设置→应用管理→PhoneLocation→权限→位置→始终允许，然后在App里点重启服务",
                    Toast.LENGTH_LONG,
                ).show()
            }
            showStatus()
            return
        }

        if (Prefs.configured(this)) showStatus() else showConfig()
    }

    /* ---------------- 配置页 ---------------- */

    private fun showConfig() {
        pendingStart = false
        val wrap = col()

        wrap.addView(head("PhoneLocation 配置"))
        wrap.addView(hint("首次使用: 填写两项密钥类信息, 权限弹窗逐个允许即可"))

        wrap.addView(label("服务端地址 API_BASE"))
        etApi = EditText(this).apply {
            val saved = Prefs.apiBase(this@MainActivity)
            setText(if (saved.isEmpty()) DEFAULT_API_BASE else saved)
            textSize = 14f
            paint()
        }
        wrap.addView(etApi)

        wrap.addView(label("设备密钥 DEVICE_KEY"))
        etKey = EditText(this).apply {
            setText(Prefs.deviceKey(this@MainActivity))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            paint()
        }
        wrap.addView(etKey)

        wrap.addView(label("设备名称 DEVICE_NAME"))
        etName = EditText(this).apply {
            setText(Prefs.deviceName(this@MainActivity))
            textSize = 14f
            paint()
        }
        wrap.addView(etName)

        wrap.addView(label("被动上报间隔(分钟, 5~1440)"))
        etMin = EditText(this).apply {
            setText(Prefs.passiveMin(this@MainActivity).toString())
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER
            paint()
        }
        wrap.addView(etMin)

        wrap.addView(
            button("保存并启动") {
                onSave()
            },
        )

        wrap.addView(hint("电脑装配: 运行仓库 phone-app/setup-phone.bat 可跳过以上全部手动步骤"))

        setContentView(scroll(wrap))
    }

    private fun onSave() {
        val api = etApi.text.toString().trim()
        val key = etKey.text.toString().trim()
        val name = etName.text.toString().trim().ifEmpty { "redmi-note15pro" }
        val min = etMin.text.toString().toIntOrNull() ?: 60
        if (!api.startsWith("http")) {
            toast("API_BASE 须以 http 开头")
            return
        }
        if (key.length < 8) {
            toast("DEVICE_KEY 至少 8 位")
            return
        }
        Prefs.setApiBase(this, api)
        Prefs.setDeviceKey(this, key)
        Prefs.setDeviceName(this, name)
        Prefs.setPassiveMin(this, min)
        pendingStart = true
        startPermissionChain()
    }

    /* ---------------- 权限链: 精确位置 → 后台位置 → 通知 → 电池白名单 ---------------- */

    private fun startPermissionChain() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                11,
            )
            return
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 12)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 13)
            return
        }
        askBatteryExemption()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!pendingStart) return
        when (requestCode) {
            11, 12, 13 -> startPermissionChain()
        }
    }

    private fun askBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
        pendingStart = false
        TrackerService.start(this)
        showStatus()
        toast("服务已启动; 建议在系统设置中把本应用省电策略设为无限制")
    }

    /* ---------------- 状态页 ---------------- */

    private fun showStatus() {
        pendingStart = false
        val wrap = col()

        wrap.addView(head("PhoneLocation"))

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFE6EBF5.toInt())
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(0, dp(12), 0, dp(16))
        }
        wrap.addView(statusText)

        val row1 = row()
        row1.addView(button("测试上报") {
            startService(Intent(this, TrackerService::class.java).putExtra("test_now", true))
            toast("已触发测试上报")
        }, weight())
        row1.addView(button("重启服务") {
            val ok = TrackerService.start(this)
            toast(if (ok) "服务已重启" else "缺少定位权限：设置→应用管理→PhoneLocation→权限 开启后重试")
        }, weight())
        wrap.addView(row1)

        val row2 = row()
        row2.addView(button("停止服务") {
            startService(Intent(this, TrackerService::class.java).putExtra("stop_now", true))
            toast("服务已停止")
        }, weight())
        row2.addView(button("重新配置") {
            showConfig()
        }, weight())
        wrap.addView(row2)

        if (isDeviceOwner()) {
            wrap.addView(button("解除设备所有者(恢复可卸载/可强停)") {
                releaseOwner()
            })
        }

        wrap.addView(
            hint(
                "HyperOS 加固(电脑装配已自动处理, 手动安装才需要):\n" +
                    "1. 设置→应用管理→PhoneLocation→自启动→开\n" +
                    "2. 省电策略→无限制\n" +
                    "3. 最近任务卡片长按→锁定",
            ),
        )

        setContentView(scroll(wrap))
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::statusText.isInitialized) {
            if (Prefs.configured(this) && TrackerService.aliveSince == 0L &&
                TrackerService.hasLocationPermission(this)
            ) {
                TrackerService.start(this)
                android.os.Handler(mainLooper).postDelayed({ updateStatus() }, 1500)
            }
            updateStatus()
        }
    }

    private fun updateStatus() {
        if (!Prefs.configured(this)) return
        val alive = TrackerService.aliveSince > 0L
        val last = Prefs.lastReportAt(this)
        statusText.text = buildString {
            append("服务: ").append(if (alive) "运行中" else "未运行").append('\n')
            append("版本: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("定位开关: ").append(if (Prefs.cfgEnabled(this@MainActivity)) "开" else "关（地图可一键开启）").append('\n')
            run {
                val ws = Prefs.cfgWorkStart(this@MainActivity)
                val we = Prefs.cfgWorkEnd(this@MainActivity)
                append("工作窗口: ").append("%02d:%02d~%02d:%02d".format(ws / 60, ws % 60, we / 60, we % 60))
                    .append(" 运行日 ").append(Prefs.cfgWorkDays(this@MainActivity)).append('\n')
            }
            append("服务端: ").append(Prefs.apiBase(this@MainActivity)).append('\n')
            append("设备名: ").append(Prefs.deviceName(this@MainActivity)).append('\n')
            append("被动间隔: ").append(Prefs.passiveMin(this@MainActivity)).append(" 分钟\n")
            append("最后上报: ").append(if (last == 0L) "无" else ago(last)).append('\n')
            append("最近结果: ").append(Prefs.lastResult(this@MainActivity).ifEmpty { "-" }).append('\n')
            append("轮询次数: ").append(Prefs.pollCount(this@MainActivity)).append('\n')
            append("待补传: ").append(Outbox.size(this@MainActivity)).append(" 条\n")
            append("设备所有者: ").append(if (isDeviceOwner()) "已启用(系统级保护)" else "未启用")
        }
    }

    private fun dpm(): DevicePolicyManager =
        getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun isDeviceOwner(): Boolean =
        try {
            dpm().isDeviceOwnerApp(packageName)
        } catch (_: Exception) {
            false
        }

    @Suppress("DEPRECATION")
    private fun releaseOwner() {
        try {
            dpm().clearDeviceOwnerApp(packageName)
            toast("已解除设备所有者，App 恢复为普通应用")
        } catch (e: Exception) {
            toast("解除失败: ${e.message}")
        }
        updateStatus()
    }

    /* ---------------- UI 小件 ---------------- */

    private fun col(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(36), dp(20), dp(20))
        setBackgroundColor(0xFF0F1420.toInt())
    }

    private fun scroll(child: LinearLayout): ScrollView = ScrollView(this).apply {
        addView(child)
        setBackgroundColor(0xFF0F1420.toInt())
        setFillViewport(true)
    }

    private fun head(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 24f
        setTextColor(0xFFE6EBF5.toInt())
    }

    private fun label(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(0xFF8B97AD.toInt())
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun hint(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(0xFF64748B.toInt())
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(16), 0, 0)
    }

    private fun button(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        setAllCaps(false)
        textSize = 13f
        setOnClickListener { onClick() }
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun EditText.paint() {
        setTextColor(0xFFE6EBF5.toInt())
        setHintTextColor(0xFF64748B.toInt())
        setBackgroundColor(0xFF1A2236.toInt())
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun ago(ts: Long): String {
        val s = (System.currentTimeMillis() - ts) / 1000
        return when {
            s < 60 -> "$s 秒前"
            s < 3600 -> "${s / 60} 分钟前"
            s < 86400 -> "${s / 3600} 小时前"
            else -> "${s / 86400} 天前"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
