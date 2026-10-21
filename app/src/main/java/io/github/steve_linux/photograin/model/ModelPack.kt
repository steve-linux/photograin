// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.model

/**
 * A larger AI model that does not ship with the app: the user downloads its file from the
 * project's releases with the browser and imports it from Settings, so the app needs no
 * internet access. The file is a zip of [files], recognized by its [sha256] alone; docs/MODELS.md
 * says how each pack is made.
 */
data class ModelPack(
    val id: String,
    /** The name of the file in the releases. */
    val fileName: String,
    /** SHA-256 of the whole file, lowercase hex. */
    val sha256: String,
    /** The size of the file, in bytes. */
    val bytes: Long,
    /** What the zip holds, by name. */
    val files: List<String>,
) {
    companion object {
        /** Where the packs are published. */
        const val RELEASES_URL = "https://github.com/steve-linux/photograin/releases/tag/models-1"

        /** IS-Net: cuts out the main subject with finer edges than the bundled U^2-Net-p, slowly. */
        val ISNET = ModelPack(
            id = "isnet",
            fileName = "photograin-isnet.zip",
            sha256 = "bfbde8053f32c2fdcd5115a0d7818ee1b2d0c49f3ec935028e7453ce6b180e73",
            bytes = 88_095_161,
            files = listOf("isnet.ncnn.param", "isnet.ncnn.bin"),
        )

        val all = listOf(ISNET)

        /** The pack whose file has checksum [sha256], or null if the file is not a known pack. */
        fun bySha256(sha256: String): ModelPack? = all.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }

        /** [bytes] as lowercase hex. */
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
