package com.streamshell.core.browser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hands links to other apps (mail, phone, maps, store, app links). Only schemes NavigationPolicy allowed reach here. */
interface ExternalNavigator {
    fun open(uri: String): Boolean
}

class AndroidExternalNavigator(private val appContext: Context) : ExternalNavigator {
    private val allowed = setOf("https", "http", "mailto", "tel", "sms", "smsto", "geo", "market")

    override fun open(uri: String): Boolean {
        val intent = if (uri.startsWith("intent:", ignoreCase = true)) {
            // Same hardening as Chrome: only browsable activities, no explicit component or selector.
            try {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                    component = null
                    selector = null
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            } catch (_: Exception) {
                return false
            }
        } else {
            val parsed = Uri.parse(uri)
            if (parsed.scheme?.lowercase() !in allowed) return false
            Intent(Intent.ACTION_VIEW, parsed).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

class AndroidNetworkMonitor(appContext: Context) : NetworkMonitor {
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val online = MutableStateFlow(cm?.activeNetwork != null)
    override val isOnline: StateFlow<Boolean> = online.asStateFlow()

    init {
        try {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online.value = true
                }

                override fun onLost(network: Network) {
                    online.value = false
                }
            })
        } catch (_: SecurityException) {
            // Without ACCESS_NETWORK_STATE we cannot know; assume online and let WebView report errors.
        }
    }
}
