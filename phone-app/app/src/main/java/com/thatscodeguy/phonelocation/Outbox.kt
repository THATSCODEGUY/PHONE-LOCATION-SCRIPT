package com.thatscodeguy.phonelocation

import android.content.Context
import java.io.File

object Outbox {

    private fun file(ctx: Context): File = File(ctx.filesDir, "outbox.jsonl")

    fun push(ctx: Context, line: String) {
        try {
            file(ctx).appendText(line.trim() + "\n")
        } catch (_: Exception) {
        }
    }

    fun size(ctx: Context): Int = try {
        if (file(ctx).exists()) file(ctx).readLines().count { it.isNotBlank() } else 0
    } catch (_: Exception) {
        0
    }

    /** 逐条补传, 返回剩余条数 */
    fun flush(ctx: Context, apiBase: String, deviceKey: String): Int {
        val f = file(ctx)
        if (!f.exists() || f.length() == 0L) return 0
        val lines = try {
            f.readLines().filter { it.isNotBlank() }
        } catch (_: Exception) {
            return size(ctx)
        }
        if (lines.isEmpty()) return 0
        val remaining = ArrayList<String>()
        for (line in lines) {
            val r = Http.post(
                "$apiBase/api/report",
                line,
                mapOf("X-Device-Key" to deviceKey),
            )
            if (!r.ok) remaining.add(line)
        }
        try {
            if (remaining.isEmpty()) f.delete()
            else f.writeText(remaining.joinToString("\n") + "\n")
        } catch (_: Exception) {
        }
        return remaining.size
    }
}
