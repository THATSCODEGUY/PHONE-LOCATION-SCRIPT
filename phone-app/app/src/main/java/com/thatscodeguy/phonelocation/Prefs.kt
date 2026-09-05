package com.thatscodeguy.phonelocation

import android.content.Context

object Prefs {
    private const val FILE = "prefs"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun apiBase(ctx: Context): String =
        sp(ctx).getString("api_base", "")!!.trim().trimEnd('/')

    fun setApiBase(ctx: Context, v: String) =
        sp(ctx).edit().putString("api_base", v.trim().trimEnd('/')).apply()

    fun deviceKey(ctx: Context): String =
        sp(ctx).getString("device_key", "")!!

    fun setDeviceKey(ctx: Context, v: String) =
        sp(ctx).edit().putString("device_key", v.trim()).apply()

    fun deviceName(ctx: Context): String =
        sp(ctx).getString("device_name", "primary")!!

    fun setDeviceName(ctx: Context, v: String) =
        sp(ctx).edit().putString("device_name", v.trim().ifEmpty { "primary" }).apply()

    fun passiveMin(ctx: Context): Int {
        val v = sp(ctx).getInt("passive_min", 60)
        return if (v in 5..1440) v else 60
    }

    fun setPassiveMin(ctx: Context, v: Int) =
        sp(ctx).edit().putInt("passive_min", v.coerceIn(5, 1440)).apply()

    fun configured(ctx: Context): Boolean =
        deviceKey(ctx).length >= 8 && apiBase(ctx).startsWith("http")

    fun lastReportAt(ctx: Context): Long = sp(ctx).getLong("last_report_at", 0L)

    fun setLastReportAt(ctx: Context, v: Long) =
        sp(ctx).edit().putLong("last_report_at", v).apply()

    fun lastResult(ctx: Context): String =
        sp(ctx).getString("last_result", "")!!

    fun setLastResult(ctx: Context, v: String) =
        sp(ctx).edit().putString("last_result", v.take(200)).apply()

    fun pollCount(ctx: Context): Long = sp(ctx).getLong("poll_count", 0L)

    fun setPollCount(ctx: Context, v: Long) =
        sp(ctx).edit().putLong("poll_count", v).apply()

    fun lastPowerGuardAt(ctx: Context): Long = sp(ctx).getLong("power_guard_at", 0L)

    fun setLastPowerGuardAt(ctx: Context, v: Long) =
        sp(ctx).edit().putLong("power_guard_at", v).apply()
}
