package com.uplants.app.data.network

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * What the app knows about the connection and the device (requirements 23 and 24).
 *
 * @property isOnline the device has a working internet connection.
 * @property isMetered mobile data or another paid connection.
 * @property dataSaver the user turned on Android's Data Saver for this app.
 * @property lowRamDevice a low-memory phone.
 */
data class NetworkStatus(
    val isOnline: Boolean = true,
    val isMetered: Boolean = false,
    val dataSaver: Boolean = false,
    val lowRamDevice: Boolean = false,
) {
    /** Lighter mode: images only on demand, media not preloaded, photos compressed before upload. */
    val lowFidelity: Boolean get() = isMetered || dataSaver || lowRamDevice
}

/**
 * Fails requests at once while the device is offline, instead of letting them hang until a timeout, so screens fall
 * back to saved content immediately (requirements 22, 23). Added first, so it doesn't mark services as down.
 */
class OfflineInterceptor(private val monitor: NetworkMonitor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!monitor.status.value.isOnline) throw DeviceOfflineException()
        return chain.proceed(chain.request())
    }
}

/** The device itself has no connection (as opposed to one service not answering). */
class DeviceOfflineException : IOException("Device is offline")

/** Watches the default network and publishes [status]. */
class NetworkMonitor(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val lowRam = context.getSystemService(ActivityManager::class.java)?.let {
        it.isLowRamDevice || it.memoryClass < LOW_MEMORY_CLASS_MB
    } ?: false

    private val _status = MutableStateFlow(read(connectivity?.activeNetwork))
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    init {
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _status.value = read(network)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _status.value = read(caps)
            }

            override fun onLost(network: Network) {
                _status.value = _status.value.copy(isOnline = false)
            }
        })
    }

    private fun read(network: Network?): NetworkStatus = read(network?.let { connectivity?.getNetworkCapabilities(it) })

    private fun read(caps: NetworkCapabilities?): NetworkStatus {
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val dataSaver = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        return NetworkStatus(isOnline = online, isMetered = online && metered, dataSaver = dataSaver, lowRamDevice = lowRam)
    }

    private companion object {
        /** Apps get less than this much heap on budget phones. */
        const val LOW_MEMORY_CLASS_MB = 128
    }
}
