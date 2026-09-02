package com.thatscodeguy.phonelocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Prefs.configured(ctx)) return
        // 幂等: 服务已在运行时仅重复一次 onStartCommand, 死了则拉起
        TrackerService.start(ctx)
        TrackerService.scheduleWatchdog(ctx)
    }
}
