// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import io.github.steve_linux.photograin.model.HighlightGain
import io.github.steve_linux.photograin.model.ColorProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

@kotlinx.serialization.Serializable
enum class ExportFormat(
    val mimeType: String,
    val extension: String,
    val label: String,
    val lossy: Boolean,
    /** Rendered and stored with 16 bits per channel instead of 8. */
    val deep: Boolean = false,
    /** Keeps transparency, such as a background removed with a cutout; the others turn it white. */
    val alpha: Boolean = false,
) {
    JPEG("image/jpeg", "jpg", "JPEG", lossy = true),
    PNG("image/png", "png", "PNG", lossy = false, alpha = true),
    WEBP("image/webp", "webp", "WebP", lossy = true, alpha = true),
    AVIF("image/avif", "avif", "AVIF", lossy = true),
    /** At quality 100 JPEG XL is mathematically lossless. */
    JXL("image/jxl", "jxl", "JPEG XL", lossy = true),
    /** 16 bits per channel, for further editing or printing: large files, no rounding to 8 bits. */
    TIFF("image/tiff", "tif", "TIFF 16 bit", lossy = false, deep = true);

    val supportsSizeLimit: Boolean get() = this == JPEG || this == WEBP
}

@kotlinx.serialization.Serializable
data class ExportOptions(
    val format: ExportFormat = ExportFormat.JPEG,
    val quality: Int = 92,
    /** Null exports at full resolution. */
    val maxLongEdge: Int? = null,
    /** 2 or 4 enlarges the photo that many times (then [maxLongEdge] is ignored); 1 keeps its size. */
    val upscale: Int = 1,
    /** Enlarge with Real-ESRGAN instead of bicubic interpolation: slower, but it restores detail. */
    val aiUpscale: Boolean = false,
    /** Location is dropped by default so shared photos do not reveal where they were taken. */
    val keepLocation: Boolean = false,
    /** sRGB suits the web and most viewers; wider spaces keep more color for printing or wide-gamut screens. */
    val colorProfile: ColorProfile = ColorProfile.SRGB,
    /** Camera, lens, exposure and date tags; off strips everything but what viewers need to show the pixels. */
    val keepMetadata: Boolean = true,
    /** Kinds of metadata left out even when [keepMetadata] is on; location follows [keepLocation]. */
    val dropped: Set<MetadataGroup> = emptySet(),
    val maxFileSizeKb: Int? = null,
    val metadataEdits: ExportMetadataEdits = ExportMetadataEdits(),
    val ultraHdr: Boolean = false,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val printPpi: Int? = null,
    val outputSharpen: Float = 0f,
    val batchPrefix: String = "",
    val batchStart: Int = 1,
    val hdrHighlightStops: Float = HighlightGain.DEFAULT_STOPS,
    val hdrHighlightThreshold: Float = HighlightGain.DEFAULT_THRESHOLD,
    val watermark: ExportWatermark? = null,
) {
    /** Only formats with a tunable Android encoder support a byte limit. */
    val effectiveMaxBytes: Long?
        get() = maxFileSizeKb?.takeIf { it > 0 && format.supportsSizeLimit }?.times(1000L)

    /** The kinds of metadata this export carries. */
    val keptGroups: List<MetadataGroup>
        get() = if (!keepMetadata) {
            emptyList()
        } else {
            MetadataGroup.entries.filter { it !in dropped && (it != MetadataGroup.LOCATION || keepLocation) }
        }
}

/** User-supplied EXIF values for the exported copy. Blank fields preserve the export defaults. */
@kotlinx.serialization.Serializable
data class ExportMetadataEdits(
    val author: String = "",
    val copyright: String = "",
    val description: String = "",
    val date: String = "",
) {
    internal fun applyTo(target: ExifInterface) {
        if (author.isNotBlank()) target.setAttribute(ExifInterface.TAG_ARTIST, author.trim())
        if (copyright.isNotBlank()) target.setAttribute(ExifInterface.TAG_COPYRIGHT, copyright.trim())
        if (description.isNotBlank()) target.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, description.trim())
        if (date.isNotBlank()) {
            val stamp = java.time.LocalDateTime.parse(date).format(EXIF_DATE)
            target.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
            target.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
            target.setAttribute(ExifInterface.TAG_DATETIME, stamp)
            target.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, null)
            target.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, null)
            target.setAttribute(ExifInterface.TAG_OFFSET_TIME, null)
            target.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, null)
            target.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, null)
            target.setAttribute(ExifInterface.TAG_SUBSEC_TIME, null)
        }
    }

    companion object {
        private val EXIF_DATE = java.time.format.DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", java.util.Locale.ROOT)

        fun validDate(text: String): Boolean = text.isBlank() || runCatching {
            text.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}")) &&
                java.time.LocalDateTime.parse(text).year in 1..9999
        }.getOrDefault(false)
    }
}

class ExportSizeLimitException : IOException("The encoded image cannot fit the requested size")

/** Encodes a rendered bitmap, carries over useful metadata and writes it to a user-chosen document. */
object ExportWriter {
    fun write(context: Context, bitmap: Bitmap, original: Uri, destination: Uri, options: ExportOptions) {
        options.watermark?.takeIf { it.active }?.draw(android.graphics.Canvas(bitmap), bitmap.width, bitmap.height)
        if (options.format == ExportFormat.TIFF) {
            writeTiff(context, bitmap, original, destination, options)
            return
        }
        if (options.format == ExportFormat.AVIF || options.format == ExportFormat.JXL) {
            val exif = runCatching { exifPayload(context, original, options) }.getOrNull()
            // AVIF names sRGB and Display P3 with CICP codes; Adobe RGB needs its ICC profile.
            val icc = if (options.format == ExportFormat.AVIF && options.colorProfile == ColorProfile.ADOBE_RGB) {
                iccProfile(ColorSpaces.of(options.colorProfile))
            } else {
                null
            }
            val data = ImageCodecs.encode(options.format, bitmap, options.quality, options.colorProfile, exif, icc)
            val out = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Cannot write $destination")
            out.use { it.write(data) }
            return
        }
        val tmp = File.createTempFile("export", ".${options.format.extension}", context.cacheDir)
        try {
            fun encode(image: Bitmap, quality: Int) {
                val gain = if (options.ultraHdr && options.format == ExportFormat.JPEG && Build.VERSION.SDK_INT >= 34) {
                    attachHighlightGainMap(image, options)
                } else null
                try {
                    tmp.outputStream().use { out ->
                        check(image.compress(compressFormat(options.format), quality, out)) { "Encoding failed" }
                    }
                } finally {
                    if (Build.VERSION.SDK_INT >= 34 && gain != null) image.setGainmap(null)
                    gain?.recycle()
                }
                runCatching { copyMetadata(context, original, tmp, options) }
                if (gain != null && Build.VERSION.SDK_INT >= 34) {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                    val decoded = android.graphics.BitmapFactory.decodeFile(tmp.path, bounds)
                    try {
                        check(decoded?.hasGainmap() == true) { "JPEG gain map was lost during encoding" }
                    } finally {
                        decoded?.recycle()
                    }
                }
            }

            val limit = options.effectiveMaxBytes
            if (limit == null) {
                encode(bitmap, options.quality)
            } else {
                encodeWithinLimit(bitmap, options.quality, limit, tmp, ::encode)
            }
            val out = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Cannot write $destination")
            out.use { stream -> tmp.inputStream().use { it.copyTo(stream) } }
        } finally {
            tmp.delete()
        }
    }

    private fun encodeWithinLimit(
        original: Bitmap,
        quality: Int,
        limit: Long,
        file: File,
        encode: (Bitmap, Int) -> Unit,
    ) {
        val topQuality = quality.coerceIn(0, 100)
        val bottomQuality = minOf(topQuality, 45)
        var image = original
        try {
            while (true) {
                encode(image, topQuality)
                if (file.length() <= limit) return

                if (bottomQuality < topQuality) encode(image, bottomQuality)
                val smallest = file.length()
                if (smallest <= limit) {
                    var best = bottomQuality
                    var upper = topQuality - 1
                    var current = bottomQuality
                    while (best < upper) {
                        current = (best + upper + 1) / 2
                        encode(image, current)
                        if (file.length() <= limit) best = current else upper = current - 1
                    }
                    if (current != best) encode(image, best)
                    if (file.length() > limit) throw ExportSizeLimitException()
                    return
                }

                if (image.width == 1 && image.height == 1) throw ExportSizeLimitException()
                val scale = (kotlin.math.sqrt(limit.toDouble() / smallest) * 0.95).coerceIn(0.1, 0.8)
                val width = (image.width * scale).toInt().coerceAtLeast(1)
                val height = (image.height * scale).toInt().coerceAtLeast(1)
                val resized = Bitmap.createScaledBitmap(original, width, height, true)
                if (image !== original) image.recycle()
                image = resized
            }
        } finally {
            if (image !== original) image.recycle()
        }
    }

    /** Creates an SDR-to-HDR highlight expansion map from the rendered JPEG base. */
    @androidx.annotation.RequiresApi(34)
    private fun attachHighlightGainMap(image: Bitmap, options: ExportOptions): Bitmap {
        val width = ((image.width + 7) / 8).coerceAtLeast(1)
        val height = ((image.height + 7) / 8).coerceAtLeast(1)
        val reduced = Bitmap.createScaledBitmap(image, width, height, true)
        val pixels = IntArray(width * height)
        try {
            reduced.getPixels(pixels, 0, width, 0, 0, width, height)
        } finally {
            if (reduced !== image) reduced.recycle()
        }
        for (index in pixels.indices) {
            val value = HighlightGain.encode(pixels[index], options.hdrHighlightThreshold)
            pixels[index] = android.graphics.Color.rgb(value, value, value)
        }
        val contents = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val ratio = HighlightGain.ratio(options.hdrHighlightStops)
        val map = android.graphics.Gainmap(contents).apply {
            setRatioMin(1f, 1f, 1f)
            setRatioMax(ratio, ratio, ratio)
            setGamma(1f, 1f, 1f)
            setMinDisplayRatioForHdrTransition(1f)
            setDisplayRatioForFullHdr(ratio)
        }
        image.setGainmap(map)
        return contents
    }

    private fun copyMetadata(context: Context, original: Uri, file: File, options: ExportOptions) {
        val source = runCatching {
            context.contentResolver.openInputStream(original)?.use { ExifInterface(it) }
        }.getOrNull()
        val target = ExifInterface(file)
        val tags = options.keptGroups.flatMap { it.tags }
        for (tag in tags) source?.getAttribute(tag)?.let { target.setAttribute(tag, it) }
        if (options.keepMetadata) options.metadataEdits.applyTo(target)
        val latitude = options.gpsLatitude
        val longitude = options.gpsLongitude
        if (MetadataGroup.LOCATION in options.keptGroups && latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            for (tag in MetadataGroup.LOCATION.tags) target.setAttribute(tag, null)
            target.setLatLong(latitude, longitude)
        }
        options.printPpi?.takeIf { it in 1..2400 }?.let {
            target.setAttribute(ExifInterface.TAG_X_RESOLUTION, "$it/1")
            target.setAttribute(ExifInterface.TAG_Y_RESOLUTION, "$it/1")
            target.setAttribute(ExifInterface.TAG_RESOLUTION_UNIT, "2")
        }
        target.setAttribute(ExifInterface.TAG_SOFTWARE, "Photograin")
        // EXIF can only name sRGB; anything else is "uncalibrated" and described by the embedded ICC profile.
        target.setAttribute(
            ExifInterface.TAG_COLOR_SPACE,
            if (options.colorProfile == ColorProfile.SRGB) ExifInterface.COLOR_SPACE_S_RGB.toString()
            else ExifInterface.COLOR_SPACE_UNCALIBRATED.toString(),
        )
        // Pixels are already rotated, so viewers must not rotate them again.
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        target.saveAttributes()
    }

    /**
     * Writes [bitmap] as a 16-bit TIFF. A half-float bitmap keeps the precision of the render;
     * an 8-bit one (no half-float rendering on this GPU) is widened.
     */
    private fun writeTiff(context: Context, bitmap: Bitmap, original: Uri, destination: Uri, options: ExportOptions) {
        val exif = runCatching { exifPayload(context, original, options) }.getOrNull()
        val icc = bitmap.colorSpace?.let(::iccProfile)
        val w = bitmap.width
        val deep = bitmap.config == Bitmap.Config.RGBA_F16
        // Rows are copied out a band at a time: a full copy of a large half-float photo would double its memory.
        val band = maxOf(1, (4 shl 20) / (w * 8))
        var bandTop = -1
        var bandRows = 0
        val buffer = ByteBuffer.allocateDirect(w * band * (if (deep) 8 else 4)).order(ByteOrder.nativeOrder())
        var halves = buffer.asShortBuffer()
        val tmp = File.createTempFile("export", ".tif", context.cacheDir)
        try {
            TiffWriter.write(tmp, w, bitmap.height, icc, exif, "Photograin", ppi = options.printPpi ?: 72) { y, samples ->
                if (bandTop < 0 || y >= bandTop + bandRows) {
                    bandTop = y
                    bandRows = minOf(band, bitmap.height - y)
                    val part = Bitmap.createBitmap(bitmap, 0, y, w, bandRows)
                    buffer.clear()
                    part.copyPixelsToBuffer(buffer)
                    if (part !== bitmap) part.recycle()
                    // The copy leaves the position at the end, and a view starts at the position.
                    buffer.rewind()
                    halves = buffer.asShortBuffer()
                }
                val r = y - bandTop
                if (deep) {
                    for (x in 0 until w) for (c in 0 until 3) {
                        val v = HalfFloats.toFloat(halves.get((r * w + x) * 4 + c))
                        samples[x * 3 + c] = (v.coerceIn(0f, 1f) * 65535f + 0.5f).toInt().toShort()
                    }
                } else {
                    for (x in 0 until w) for (c in 0 until 3) {
                        val v = buffer.get((r * w + x) * 4 + c).toInt() and 0xFF
                        samples[x * 3 + c] = (v * 257).toShort()
                    }
                }
            }
            val out = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Cannot write $destination")
            out.use { stream -> tmp.inputStream().use { it.copyTo(stream) } }
        } finally {
            tmp.delete()
        }
    }

    /**
     * The EXIF block (TIFF form) the export would carry. ExifInterface cannot write AVIF or JPEG XL,
     * so the tags are written into a 1x1 JPEG and its APP1 segment is lifted out.
     */
    private fun exifPayload(context: Context, original: Uri, options: ExportOptions): ByteArray? {
        val tmp = File.createTempFile("exif", ".jpg", context.cacheDir)
        try {
            val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            tmp.outputStream().use { pixel.compress(Bitmap.CompressFormat.JPEG, 50, it) }
            pixel.recycle()
            copyMetadata(context, original, tmp, options)
            return jpegExif(tmp.readBytes())
        } finally {
            tmp.delete()
        }
    }

    /** The TIFF data of the first "Exif" APP1 segment of a JPEG file. */
    internal fun jpegExif(jpeg: ByteArray): ByteArray? {
        var i = 2
        while (i + 4 <= jpeg.size && jpeg[i] == 0xFF.toByte()) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break  // image data follows: no more metadata
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val start = i + 4
            val end = i + 2 + length
            if (end > jpeg.size) break
            if (marker == 0xE1 && end - start > 6 && String(jpeg, start, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000") {
                return jpeg.copyOfRange(start + 6, end)
            }
            i = end
        }
        return null
    }

    /**
     * ICC profile bytes for [space], as Android embeds them: taken from a 1x1 PNG's iCCP chunk, or
     * for sRGB, which PNG names with a chunk of its own instead, from a 1x1 JPEG.
     */
    private fun iccProfile(space: ColorSpace): ByteArray? = pngIcc(space) ?: runCatching {
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888, false, space)
        val jpeg = ByteArrayOutputStream().also { pixel.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        pixel.recycle()
        jpegIcc(jpeg)
    }.getOrNull()

    /** The ICC profile in a JPEG's "ICC_PROFILE" APP2 segments, joined in order. */
    internal fun jpegIcc(jpeg: ByteArray): ByteArray? {
        val chunks = sortedMapOf<Int, ByteArray>()
        var i = 2
        while (i + 4 <= jpeg.size && jpeg[i] == 0xFF.toByte()) {
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) break
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val start = i + 4
            val end = i + 2 + length
            if (end > jpeg.size) break
            // "ICC_PROFILE\0", then the chunk's number and the chunk count.
            if (marker == 0xE2 && end - start > 14 && String(jpeg, start, 12, Charsets.ISO_8859_1) == "ICC_PROFILE\u0000") {
                chunks[jpeg[start + 12].toInt() and 0xFF] = jpeg.copyOfRange(start + 14, end)
            }
            i = end
        }
        if (chunks.isEmpty()) return null
        return chunks.values.reduce { acc, b -> acc + b }
    }

    private fun pngIcc(space: ColorSpace): ByteArray? = runCatching {
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888, false, space)
        val png = ByteArrayOutputStream().also { pixel.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        pixel.recycle()
        var i = 8
        while (i + 8 <= png.size) {
            val length = ((png[i].toInt() and 0xFF) shl 24) or ((png[i + 1].toInt() and 0xFF) shl 16) or
                ((png[i + 2].toInt() and 0xFF) shl 8) or (png[i + 3].toInt() and 0xFF)
            if (String(png, i + 4, 4, Charsets.ISO_8859_1) == "iCCP") {
                // Profile name, a zero byte, the compression method (zlib), then the profile.
                var p = i + 8
                while (png[p] != 0.toByte()) p++
                val inflater = Inflater()
                inflater.setInput(png, p + 2, i + 8 + length - (p + 2))
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n == 0 && inflater.needsInput()) break
                    out.write(buffer, 0, n)
                }
                inflater.end()
                return@runCatching out.toByteArray()
            }
            i += 12 + length
        }
        null
    }.getOrNull()

    private fun compressFormat(format: ExportFormat): Bitmap.CompressFormat = when (format) {
        ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
        ExportFormat.PNG -> Bitmap.CompressFormat.PNG
        ExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        ExportFormat.AVIF, ExportFormat.JXL, ExportFormat.TIFF -> throw IllegalArgumentException("$format is encoded natively")
    }
}
