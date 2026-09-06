package com.thatscodeguy.phonelocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Prefs.configured(ctx)) return
        // 幂等: 服务已在运行时仅重复一次 onStartCommand, 停了则拉起; 触发痕迹写入状态页
        Prefs.setLastResult(ctx, "看门狗闹钟触发 ${LocalTime.now().withNano(0)}")
        TrackerService.start(ctx)
        TrackerService.scheduleWatchdog(ctx)
    }
}
