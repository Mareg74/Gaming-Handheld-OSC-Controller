package com.mareg74.osccontroller.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Copy OSC address (no args) to the system clipboard, with optional dedup. */
object OscAddressClipboard {
    enum class Result { COPIED, DEDUPED, SKIPPED }

    /** IN/OUT with a non-empty address only. */
    fun canCopy(dir: MonitorDir, address: String): Boolean =
        address.isNotEmpty() && (dir == MonitorDir.IN || dir == MonitorDir.OUT)

    /** True when [address] differs from the current plain-text clipboard. */
    fun shouldSet(currentPlainText: String?, address: String): Boolean =
        currentPlainText != address

    fun copyAddress(context: Context, dir: MonitorDir, address: String): Result {
        if (!canCopy(dir, address)) return Result.SKIPPED
        // Application context: Presentation ContextThemeWrapper can be flaky for clipboard/Toast.
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val current = readPlainText(cm, appCtx)
        if (!shouldSet(current, address)) return Result.DEDUPED
        return try {
            cm.setPrimaryClip(ClipData.newPlainText("OSC", address))
            Result.COPIED
        } catch (_: SecurityException) {
            Result.SKIPPED
        }
    }

    private fun readPlainText(cm: ClipboardManager, context: Context): String? {
        return try {
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0).coerceToText(context)?.toString()
        } catch (_: SecurityException) {
            // Android 10+ may deny read when not focused — treat as empty and allow write.
            null
        }
    }
}
