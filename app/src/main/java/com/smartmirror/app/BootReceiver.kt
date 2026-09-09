package com.smartmirror.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 监听 [Intent.ACTION_BOOT_COMPLETED]，开机后自动启动 [MainActivity]。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmartMirrorBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.i(TAG, "Boot completed, launching MainActivity")

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MainActivity on boot", e)
        }
    }
}
