package com.kavach.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kavach.KavachApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Restarts filtering after reboot, if the user enabled it and consent is granted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = KavachApp.from(context)
        val settings = runBlocking { app.settings.flow.first() }
        if (settings.startOnBoot && VpnController.prepare(context) == null) {
            VpnController.start(context)
        }
    }
}
