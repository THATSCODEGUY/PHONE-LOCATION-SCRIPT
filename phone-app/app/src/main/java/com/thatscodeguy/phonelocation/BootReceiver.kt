package com.thatscodeguy.phonelocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        val isUpdate = action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isBoot && !isUpdate) return
        if (!Prefs.configured(ctx)) return
        Prefs.setLastResult(ctx, "自启触发(${if (isUpdate) "应用更新" else "开机"}) ${LocalTime.now().withNano(0)}")
        TrackerService.start(ctx)
        TrackerService.scheduleWatchdog(ctx)
    }
}
