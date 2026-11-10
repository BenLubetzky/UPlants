package com.uplants.app.data.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class DeviceLocation(val latitude: Double, val longitude: Double, val name: String?)

/** GPS / network location for live recordings (requirement 18). */
class LocationReader(private val context: Context) {

    fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** A fresh fix (up to [timeoutMs]), or the most recent known one. Null without permission or location turned off. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 15_000): DeviceLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        // A fix from the last couple of minutes is good enough and avoids waiting for the GPS.
        val recent = lastKnown(manager, providers)?.takeIf { System.currentTimeMillis() - it.time < RECENT_FIX_MS }
        if (recent != null) return DeviceLocation(recent.latitude, recent.longitude, placeName(recent))

        val fresh = providers.firstOrNull()?.let { provider ->
            withTimeoutOrNull(timeoutMs) { currentFix(manager, provider) }
        }
        val location = fresh ?: lastKnown(manager, providers) ?: return null
        return DeviceLocation(location.latitude, location.longitude, placeName(location))
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (cont.isActive) cont.resume(location)
            }
        }

    /** Town or region name for the coordinates, if the device has a geocoder and network. */
    private suspend fun placeName(location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION") // the async API needs API 33; this runs off the main thread anyway
            Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
    }

    private companion object {
        const val RECENT_FIX_MS = 2 * 60 * 1000L
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
