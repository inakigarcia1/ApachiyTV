package com.nuvio.tv.core.sync.androidtv

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/**
 * Serves cached TV launcher artwork to the system TvProvider / launcher process.
 */
class TvLauncherArtContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = requireNotNull(context)
        val file = resolveFile(context, uri)
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String {
        val extension = uri.lastPathSegment?.substringAfterLast('.', "") ?: return "image/jpeg"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        fun uriFor(context: Context, fileName: String): Uri {
            return Uri.parse("content://${context.packageName}.tvlauncherart/$fileName")
        }

        fun artDirectory(context: Context): File {
            return File(context.cacheDir, "tv_launcher_art")
        }

        fun resolveFile(context: Context, uri: Uri): File {
            val fileName = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
            if (fileName.contains("..") || fileName.contains('/')) {
                throw FileNotFoundException(uri.toString())
            }
            return File(artDirectory(context), fileName)
        }
    }
}
