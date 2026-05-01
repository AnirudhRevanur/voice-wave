package com.voicewave.handlers

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider

/**
 * Searches ALL files on the device using Android's MediaStore API —
 * the same index Kvaesitso uses under the hood.
 *
 * HOW MEDIASTORE WORKS:
 * Android maintains a database of every media/document file on your device.
 * We can query it like a SQL database: SELECT * WHERE name LIKE '%quarterly%'
 * It searches across internal storage and SD cards automatically.
 *
 * We search across four collections:
 *   - Documents (PDFs, DOCx, TXT, etc.)
 *   - Audio files
 *   - Video files
 *   - Images
 *
 * Then we open the best match using a file manager / appropriate app.
 */
object FileSearchHandler {

    data class FileResult(
        val name: String,
        val uri: Uri,
        val mimeType: String?,
        val size: Long
    )

    fun handle(context: Context, query: String): Boolean {
        val results = searchFiles(context, query)
        if (results.isEmpty()) return false

        // Open the best (first) result
        val best = results.first()
        return openFile(context, best)
    }

    /**
     * Returns all files matching [query], sorted by relevance:
     * exact name matches first, then partial matches.
     */
    fun searchFiles(context: Context, query: String): List<FileResult> {
        val results = mutableListOf<FileResult>()

        results += searchCollection(
            context,
            MediaStore.Files.getContentUri("external"),
            query,
            arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE,
            ),
            MediaStore.Files.FileColumns.DISPLAY_NAME
        )

        // Sort: exact matches first, then by name length (shorter = more specific)
        return results.sortedWith(compareByDescending<FileResult> { result ->
            result.name.lowercase().contains(query.lowercase())
        }.thenBy { it.name.length })
    }

    private fun searchCollection(
        context: Context,
        uri: Uri,
        query: String,
        projection: Array<String>,
        nameColumn: String
    ): List<FileResult> {
        val results = mutableListOf<FileResult>()

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            "$nameColumn LIKE ?",
            arrayOf("%$query%"),
            "$nameColumn ASC"
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = it.getColumnIndexOrThrow(nameColumn)
            val mimeCol = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val sizeCol = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: continue
                val mime = if (mimeCol >= 0) it.getString(mimeCol) else null
                val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                val fileUri = ContentUris.withAppendedId(uri, id)

                results.add(FileResult(name, fileUri, mime, size))
            }
        }

        return results
    }

    private fun openFile(context: Context, file: FileResult): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.mimeType ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
