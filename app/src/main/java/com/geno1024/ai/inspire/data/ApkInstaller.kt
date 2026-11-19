package com.geno1024.ai.inspire.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

/** Hands a finished APK to the system installer. */
object ApkInstaller {

    fun uriFor(context: Context, file: java.io.File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun install(context: Context, uri: Uri) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(view)
            return
        } catch (_: ActivityNotFoundException) {
            // Some OEMs only advertise the legacy action.
        }
        val legacy = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(legacy)
    }
}
