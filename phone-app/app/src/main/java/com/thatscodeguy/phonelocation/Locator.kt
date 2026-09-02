package com.thatscodeguy.phonelocation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Locator(private val ctx: Context) {

    class Fix(
        val lat: Double,
        val lng: Double,
        val accuracy: Double?,
        val provider: String,
        val speed: Double?,
        val bearing: Double?,
        val altitude: Double?,
    )

    /** GPS -> 网络 -> 最近已知(10分钟内) 三级降级 */
    @SuppressLint("MissingPermission")
    fun get(gpsTimeoutSec: Int = 15, netTimeoutSec: Int = 10): Fix? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listenOnce(lm, LocationManager.GPS_PROVIDER, gpsTimeoutSec)?.let { return toFix(it) }
        listenOnce(lm, LocationManager.NETWORK_PROVIDER, netTimeoutSec)?.let { return toFix(it) }
        return lastKnown(lm, 10 * 60 * 1000L)?.let { toFix(it) }
    }

    private fun toFix(l: Location) = Fix(
        l.latitude,
        l.longitude,
        if (l.hasAccuracy() && l.accuracy > 0f) l.accuracy.toDouble() else null,
        l.provider ?: "unknown",
        if (l.hasSpeed() && l.speed > 0f) l.speed.toDouble() else null,
        if (l.hasBearing()) l.bearing.toDouble() else null,
        if (l.hasAltitude()) l.altitude else null,
    )

    @SuppressLint("MissingPermission")
    private fun listenOnce(lm: LocationManager, provider: String, timeoutSec: Int): Location? {
        try {
            lm.getProvider(provider) ?: return null
        } catch (_: Exception) {
            return null
        }
        val latch = CountDownLatch(1)
        var result: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (result == null) {
                    result = location
                    latch.countDown()
                }
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (_: Exception) {
            return null
        }
        try {
            latch.await(timeoutSec.toLong(), TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
        }
        try {
            lm.removeUpdates(listener)
        } catch (_: Exception) {
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager, maxAgeMs: Long): Location? {
        var best: Location? = null
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            val l = try {
                lm.getLastKnownLocation(p)
            } catch (_: Exception) {
                null
            } ?: continue
            if (System.currentTimeMillis() - l.time <= maxAgeMs) {
                if (best == null || l.time > best!!.time) best = l
            }
        }
        return best
    }
}
