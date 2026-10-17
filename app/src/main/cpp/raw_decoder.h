// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

#include "libraw/libraw.h"

namespace photograin {

/** Size of the developed image as stored, before orientation, plus the LibRaw flip code. */
struct RawInfo {
    int width = 0;
    int height = 0;
    /** LibRaw orientation: 0 none, 3 = 180 degrees, 5 = 90 counter-clockwise, 6 = 90 clockwise. */
    int flip = 0;
};

/**
 * One camera RAW file. Pixels are developed to 8-bit sRGB with the camera white balance and are
 * left unrotated: callers apply [RawInfo::flip] themselves, like an EXIF orientation.
 * Each call opens the file afresh, so probe(), develop() and thumbnail() are independent.
 */
class RawDecoder {
public:
    RawDecoder();
    ~RawDecoder();
    RawDecoder(const RawDecoder&) = delete;
    RawDecoder& operator=(const RawDecoder&) = delete;

    /** Every method returns 0 on success or a LibRaw error code; see errorMessage(). */
    int openFile(const char* path);
    int openBuffer(const void* data, size_t size);

    /** Reads the output size without decoding pixels. Call right after open. */
    int probe(RawInfo* info);

    /** Decodes and demosaics. halfSize skips demosaicing and halves each side, for previews. */
    int develop(bool halfSize);
    int developedWidth() const;
    int developedHeight() const;
    /** Writes the developed image as RGBA, rows [stride] bytes apart. */
    void copyRgba(uint8_t* dst, size_t stride) const;

    /** Extracts the embedded JPEG preview, if the file has one. */
    int thumbnail(std::vector<uint8_t>* jpeg);

    static const char* errorMessage(int code);

private:
    void releaseImage();

    std::unique_ptr<LibRaw> raw_;
    libraw_processed_image_t* image_ = nullptr;
};

}  // namespace photograin
