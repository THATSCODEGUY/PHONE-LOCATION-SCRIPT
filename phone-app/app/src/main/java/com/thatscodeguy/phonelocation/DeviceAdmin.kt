package com.thatscodeguy.phonelocation

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * 设备所有者接收器 (L3 加固, 由 setup-phone.bat 通过
 * `adb shell dpm set-device-owner` 启用)。
 * 启用后 App 获得系统级保护: 无法卸载/强停, 开机必自启。
 * 解除方式: 恢复出厂设置, 或 `adb shell dpm remove-active-admin`。
 */
class DeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {}

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "停用后手机将失去防卸载与自启保护，确定停用？"
}
