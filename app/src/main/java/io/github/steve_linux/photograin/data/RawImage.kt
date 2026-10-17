// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/** Camera RAW files, developed by LibRaw through the native photograin_raw library. */
object RawImage {
    /** Stored size before orientation, plus the EXIF orientation tag LibRaw found. */
    class Info(val width: Int, val height: Int, val exifOrientation: Int)

    private val EXTENSIONS = setOf(
        "3fr", "ari", "arw", "bay", "cr2", "cr3", "crw", "dcr", "dng", "erf", "fff", "gpr", "iiq",
        "k25", "kdc", "mdc", "mef", "mos", "mrw", "nef", "nrw", "orf", "pef", "ptx", "raf", "raw",
        "rw2", "rwl", "sr2", "srf", "srw", "x3f",
    )

    /** MIME types offered to the system picker alongside all images, for providers that report them. */
    val MIME_TYPES = arrayOf(
        "image/x-adobe-dng", "image/x-canon-cr2", "image/x-canon-cr3", "image/x-nikon-nef",
        "image/x-sony-arw", "image/x-fuji-raf", "image/x-olympus-orf", "image/x-panasonic-rw2",
        "image/x-pentax-pef", "image/x-samsung-srw",
    )

    private val loaded by lazy { runCatching { System.loadLibrary("photograin_raw") }.isSuccess }

    fun isRaw(mimeType: String?, displayName: String?): Boolean {
        if (mimeType != null && mimeType in MIME_TYPES) return true
        val ext = displayName?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in EXTENSIONS
    }

    fun probe(context: Context, uri: Uri): Info = withPath(context, uri) { path ->
        val v = nativeProbe(path) ?: throw IOException("Cannot read $uri")
        Info(v[0], v[1], flipToExif(v[2]))
    }

    /** Develops the whole file; [halfSize] halves each side and is several times faster. */
    fun develop(context: Context, uri: Uri, halfSize: Boolean): Bitmap = withPath(context, uri) { path ->
        val handle = nativeDevelop(path, halfSize)
        try {
            val bitmap = Bitmap.createBitmap(nativeWidth(handle), nativeHeight(handle), Bitmap.Config.ARGB_8888)
            nativeCopyTo(handle, bitmap)
            bitmap
        } finally {
            nativeRelease(handle)
        }
    }

    /** The camera's embedded JPEG preview, or null when the file has none. */
    fun embeddedPreview(context: Context, uri: Uri, maxLongEdge: Int): Bitmap? = runCatching {
        val jpeg = withPath(context, uri) { nativeThumbnail(it) } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongEdge) sample *= 2
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** LibRaw flip codes to the EXIF orientation tags [io.github.steve_linux.photograin.model.BaseOrientation] reads. */
    private fun flipToExif(flip: Int) = when (flip) {
        3 -> 3
        5 -> 8
        6 -> 6
        else -> 1
    }

    /**
     * LibRaw reads by path. A local document is reachable through /proc/self/fd; anything else
     * (a pipe from a cloud provider) is copied to the cache first.
     */
    private inline fun <T> withPath(context: Context, uri: Uri, block: (String) -> T): T {
        if (!loaded) throw IOException("RAW support is not available on this device")
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("Cannot open $uri")
        pfd.use {
            if (isRegularFile(it)) return block("/proc/self/fd/${it.fd}")
        }
        val copy = File.createTempFile("raw", null, context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                copy.outputStream().use { input.copyTo(it) }
            } ?: throw IOException("Cannot open $uri")
            return block(copy.path)
        } finally {
            copy.delete()
        }
    }

    private fun isRegularFile(pfd: ParcelFileDescriptor) =
        runCatching { OsConstants.S_ISREG(Os.fstat(pfd.fileDescriptor).st_mode) }.getOrDefault(false)

    @JvmStatic private external fun nativeProbe(path: String): IntArray?
    @JvmStatic private external fun nativeDevelop(path: String, halfSize: Boolean): Long
    @JvmStatic private external fun nativeWidth(handle: Long): Int
    @JvmStatic private external fun nativeHeight(handle: Long): Int
    @JvmStatic private external fun nativeCopyTo(handle: Long, bitmap: Bitmap)
    @JvmStatic private external fun nativeRelease(handle: Long)
    @JvmStatic private external fun nativeThumbnail(path: String): ByteArray?
}
