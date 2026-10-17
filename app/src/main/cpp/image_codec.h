// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace photograin {

/** Color spaces, in the order of the Kotlin ColorProfile enum. */
enum class Profile : int { kSrgb = 0, kDisplayP3 = 1, kAdobeRgb = 2 };

/** 8-bit RGBA pixels (alpha ignored), rows [stride] bytes apart. */
struct Pixels {
    const uint8_t* data;
    int width;
    int height;
    size_t stride;
};

struct EncodeOptions {
    /** 0..100, like JPEG quality. For JPEG XL, 100 means mathematically lossless. */
    int quality = 90;
    Profile profile = Profile::kSrgb;
    /** Worker threads; 0 picks one per core. */
    int threads = 0;
    /** EXIF in TIFF form (starting with "II*\0" or "MM\0*"), or empty. */
    std::vector<uint8_t> exif;
    /** ICC profile, used by AVIF for spaces that have no CICP code (Adobe RGB). */
    std::vector<uint8_t> icc;
};

bool encodeJxl(const Pixels& in, const EncodeOptions& options, std::vector<uint8_t>* out, std::string* error);
bool encodeAvif(const Pixels& in, const EncodeOptions& options, std::vector<uint8_t>* out, std::string* error);

/** Upright size of a JPEG XL image and the space decodeJxl() will deliver it in. */
struct JxlInfo {
    int width = 0;
    int height = 0;
    Profile profile = Profile::kSrgb;
};

/** True if [data] starts like a JPEG XL codestream or container. */
bool isJxl(const uint8_t* data, size_t size);

bool probeJxl(const uint8_t* data, size_t size, JxlInfo* info, std::string* error);

/**
 * Decodes to upright 8-bit RGBA in [profile]: sRGB images stay sRGB, anything else is converted
 * to Display P3 (see probeJxl). [out] receives width x height pixels, rows [stride] bytes apart.
 */
bool decodeJxl(const uint8_t* data, size_t size, Profile profile, uint8_t* out, size_t stride,
               size_t outSize, std::string* error);

}  // namespace photograin
