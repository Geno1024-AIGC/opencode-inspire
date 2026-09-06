package com.geno1024.ai.occ.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class UsageExportResult(
    val csvName: String,
    val jsonName: String,
)

@Serializable
data class UsageExportDoc(
    val exportedAt: String,
    val generatedBy: String,
    val dates: Map<String, TokenDay> = emptyMap(),
    val models: Map<String, TokenModelStats> = emptyMap(),
    val sessions: Map<String, Map<String, TokenDay>> = emptyMap(),
    val sessionTitles: Map<String, String> = emptyMap(),
)

fun buildUsageCsv(history: Map<String, TokenDay>): String = buildString {
    appendLine("date,input,output,reasoning,cacheRead,cacheWrite,msgs,msgsSent,msgsReceived,cost")
    for (date in history.keys.sorted()) {
        val d = history[date] ?: TokenDay()
        appendLine(listOf(
            date, d.input, d.output, d.reasoning, d.cacheRead, d.cacheWrite,
            d.msgs, d.msgsSent, d.msgsReceived, d.cost,
        ).joinToString(","))
    }
}

fun buildUsageJson(doc: UsageExportDoc): String = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}.encodeToString(UsageExportDoc.serializer(), doc)

fun saveTextFileToDownloads(context: Context, fileName: String, text: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, if (fileName.endsWith(".json")) "application/json" else "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val dst = context.contentResolver.insert(collection, values) ?: return false
            context.contentResolver.openOutputStream(dst)?.use { out ->
                out.write(text.toByteArray())
            } ?: return false
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "exports")
            dir.mkdirs()
            File(dir, fileName).writeText(text)
        }
        true
    } catch (_: Exception) {
        false
    }
}

fun saveBitmapToDownloads(context: Context, fileName: String, bitmap: Bitmap): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/png")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val dst = context.contentResolver.insert(collection, values) ?: return false
            context.contentResolver.openOutputStream(dst)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "exports")
            dir.mkdirs()
            val out = File(dir, fileName)
            context.contentResolver.openOutputStream(android.net.Uri.fromFile(out))?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            } ?: return false
        }
        true
    } catch (_: Exception) {
        false
    }
}