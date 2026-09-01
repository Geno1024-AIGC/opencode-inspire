package com.example.opencodeclient

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.getSystemService(Context.NOTIFICATION_SERVICE)?.let { service ->
            (service as? NotificationManager)?.cancel(1002)
        }
        val cancelIntent = Intent("com.example.opencodeclient.DOWNLOAD_CANCELLED")
        context.sendBroadcast(cancelIntent)
    }
}
