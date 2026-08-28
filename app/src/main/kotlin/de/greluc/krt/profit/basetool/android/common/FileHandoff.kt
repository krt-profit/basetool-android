/*
 * Basetool Android — native companion app of the Profit Basetool.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package de.greluc.krt.profit.basetool.android.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.greluc.krt.profit.basetool.android.core.common.KrtLog
import de.greluc.krt.profit.basetool.android.core.network.DownloadedFile
import java.io.File

/**
 * Hands a downloaded report to whatever the member wants to open it with.
 *
 * **App-private storage, then a share sheet.** The file lands in the app's own `cache/reports`
 * directory and is offered through a `FileProvider` grant that lasts for the one intent. Nothing is
 * written to shared storage, so no storage permission is asked for and nothing else on the device
 * can read it; the app's data is excluded from cloud backup and device transfer already, and a
 * bank statement is exactly the sort of thing that rule exists for.
 *
 * The cache is the right place rather than `filesDir`: the member has already been handed the file,
 * and a copy the app keeps for ever is a copy that can leak later. Android may reclaim it, which is
 * the correct behaviour for something that can always be fetched again.
 */
object FileHandoff {
    /** Where reports land — app-private, and swept with the rest of the cache. */
    private const val DIRECTORY = "reports"

    /** What to call a file the server did not name. */
    private const val FALLBACK_NAME = "basetool-report"

    /** Log subsystem. A file name is logged; its contents never are. */
    private const val LOG_TAG = "files"

    /**
     * Writes the file and returns the intent that offers it.
     *
     * @param context used for the cache directory and the provider authority.
     * @param file what was downloaded.
     * @return an intent to start, or `null` when the file could not be written — in which case the
     *   caller says so rather than starting nothing and looking broken.
     */
    fun shareIntent(
        context: Context,
        file: DownloadedFile,
    ): Intent? {
        val target =
            runCatching {
                val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
                // The server's own name, stripped of anything that could climb out of the
                // directory: a `Content-Disposition` is a header, and a header is input.
                val name = file.fileName?.substringAfterLast('/')?.substringAfterLast('\\')
                File(directory, name?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME)
                    .apply { writeBytes(file.bytes) }
            }.getOrElse {
                KrtLog.w(LOG_TAG, it) { "a report could not be written to the cache" }
                return null
            }
        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        return Intent(Intent.ACTION_SEND).apply {
            type = file.mediaType ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
