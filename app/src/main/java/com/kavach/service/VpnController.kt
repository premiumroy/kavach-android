package com.kavach.service

import android.content.Context
import android.content.Intent
import android.net.VpnService

/** Helpers to prepare, start, and stop the DNS VPN. */
object VpnController {

    /** Returns a consent Intent if the user must approve the VPN, else null. */
    fun prepare(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, KavachVpnService::class.java).setAction(KavachVpnService.ACTION_START)
        context.startService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, KavachVpnService::class.java).setAction(KavachVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
