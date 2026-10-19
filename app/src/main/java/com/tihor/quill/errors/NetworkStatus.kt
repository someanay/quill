package com.tihor.quill.errors

import android.content.Context
import android.net.ConnectivityManager

/** Only declare offline when Android reports no active network; other failures need a real request. */
object NetworkStatus {
    fun isOffline(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        manager.activeNetwork == null
    }.getOrDefault(false)
}
