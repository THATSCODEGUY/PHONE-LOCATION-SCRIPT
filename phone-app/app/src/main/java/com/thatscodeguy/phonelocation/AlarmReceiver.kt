package com.thatscodeguy.phonelocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Prefs.configured(ctx)) return
        // v2.4.5: 同一接收器承载两种闹钟——探活(1002+probe extra, 睡眠期10分钟)与看门狗(1001, 15分钟兜底)
        // 幂等: 服务已在运行时仅重复一次 onStartCommand, 停了则拉起; 触发痕迹写入状态页
        val probe = intent.getBooleanExtra("probe", false)
        Prefs.setLastResult(ctx, "${if (probe) "探活闹钟" else "看门狗闹钟"}触发 ${LocalTime.now().withNano(0)}")
        TrackerService.start(ctx)
        TrackerService.scheduleWatchdog(ctx)
    }
}
