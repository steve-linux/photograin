// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.data

import android.graphics.ColorSpace
import io.github.steve_linux.photograin.model.ColorProfile

/** Bridges [ColorProfile] and Android's [ColorSpace]. */
object ColorSpaces {
    fun of(profile: ColorProfile): ColorSpace = ColorSpace.get(
        when (profile) {
            ColorProfile.SRGB -> ColorSpace.Named.SRGB
            ColorProfile.DISPLAY_P3 -> ColorSpace.Named.DISPLAY_P3
            ColorProfile.ADOBE_RGB -> ColorSpace.Named.ADOBE_RGB
        },
    )

    /**
     * The space to edit a photo encoded in [encoded] in. sRGB photos stay as they are; any other
     * embedded profile (Display P3, Adobe RGB, ProPhoto…) is converted by the decoder to Display P3,
     * which keeps most of their extra colors while sharing the sRGB transfer curve the shaders use.
     */
    fun workingSpaceFor(encoded: ColorSpace?): ColorProfile =
        if (encoded == null || encoded.isSrgb || encoded.model != ColorSpace.Model.RGB) {
            ColorProfile.SRGB
        } else {
            ColorProfile.DISPLAY_P3
        }

    /** RAW files are developed by LibRaw straight into Display P3 (see raw_decoder.cpp). */
    val RAW_WORKING_SPACE = ColorProfile.DISPLAY_P3
}
