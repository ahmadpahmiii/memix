package app.memix.platform.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.memix.core.domain.sound.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/**
 * Online means the phone's default network can reach the internet. Not "validated": that flag is false behind a blocked
 * connectivity check, and Memix would then call a working connection offline. A download that fails anyway shows its
 * own Failed look.
 */
internal class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val manager: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

    override fun observeOnline(): Flow<Boolean> {
        val connectivity = manager ?: return flowOf(true)
        return callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                }

                // Another network may take over, so ask the phone instead of assuming "offline".
                override fun onLost(network: Network) {
                    trySend(isOnline(connectivity))
                }
            }
            trySend(isOnline(connectivity))
            connectivity.registerDefaultNetworkCallback(callback)
            awaitClose { connectivity.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    private fun isOnline(connectivity: ConnectivityManager): Boolean =
        connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
