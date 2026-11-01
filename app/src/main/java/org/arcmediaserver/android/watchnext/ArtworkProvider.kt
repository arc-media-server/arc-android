package org.arcmediaserver.android.watchnext

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import okhttp3.Request
import org.arcmediaserver.android.ArcApplication
import org.arcmediaserver.android.data.ArcLog
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Artwork for Google TV's "Continue watching" row. Arc's pictures need the TV's sign-in, which
 * the launcher can't send, so the launcher reads them from here: the app fetches the picture with
 * its token, keeps a copy in its cache, and hands the launcher the file. Only title and
 * collection pictures can be read this way, nothing else of Arc's.
 */
class ArtworkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read only")
        val path = arcPath(uri) ?: throw FileNotFoundException("not artwork")
        val app = context?.applicationContext as? ArcApplication ?: throw FileNotFoundException("no app")
        val container = app.container
        val dir = File(app.cacheDir, "watch-next").apply { mkdirs() }
        val file = File(dir, hash(path))
        if (!file.exists() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            val api = container.api() ?: throw FileNotFoundException("signed out")
            val request = Request.Builder().url(api.absolute(path)).build()
            container.mediaClient.newCall(request).execute().use { res ->
                if (!res.isSuccessful) {
                    ArcLog.Android.debug("watch next artwork: HTTP ${res.code}")
                    throw FileNotFoundException("HTTP ${res.code}")
                }
                val part = File(dir, file.name + ".part")
                part.outputStream().use { out -> res.body.byteStream().copyTo(out) }
                part.renameTo(file)
            }
            prune(dir)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/*"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    /** Keeps the cache to the newest pictures. */
    private fun prune(dir: File) {
        val files = dir.listFiles()?.filter { !it.name.endsWith(".part") }?.sortedByDescending { it.lastModified() } ?: return
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    companion object {
        const val AUTHORITY = "org.arcmediaserver.android.artwork"
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_FILES = 60
        private val ALLOWED = Regex("^/api/(v1/)?(items|collections)/[A-Za-z0-9_-]+/images/[a-z_]+$")

        /** The provider address for an Arc picture path (with its query, which says which version). */
        fun uri(arcPath: String): Uri {
            val parsed = Uri.parse(arcPath)
            return Uri.Builder().scheme("content").authority(AUTHORITY).path(parsed.path).encodedQuery(parsed.encodedQuery).build()
        }

        /** The Arc path [uri] stands for, or null when it isn't a picture this provider gives out. */
        fun arcPath(uri: Uri): String? {
            if (uri.authority != AUTHORITY) return null
            val path = uri.path ?: return null
            if (!ALLOWED.matches(path)) return null
            return path + (uri.encodedQuery?.let { "?$it" } ?: "")
        }

        private fun hash(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
