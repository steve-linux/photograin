// SPDX-License-Identifier: GPL-3.0-or-later
#include "raw_decoder.h"

#include <cstring>

namespace photograin {

namespace {

/** Converts a float to binary16 with round-to-nearest; inputs here are always in 0..1. */
uint16_t toHalf(float f) {
    uint32_t x;
    std::memcpy(&x, &f, sizeof x);
    const uint32_t sign = (x >> 16) & 0x8000u;
    const int exp = static_cast<int>((x >> 23) & 0xffu) - 127 + 15;
    uint32_t mant = x & 0x7fffffu;
    if (exp <= 0) {
        if (exp < -10) return static_cast<uint16_t>(sign);
        mant |= 0x800000u;
        const int shift = 14 - exp;
        uint32_t h = mant >> shift;
        if ((mant >> (shift - 1)) & 1u) ++h;
        return static_cast<uint16_t>(sign | h);
    }
    if (exp >= 31) return static_cast<uint16_t>(sign | 0x7c00u);
    uint32_t h = sign | (static_cast<uint32_t>(exp) << 10) | (mant >> 13);
    if (mant & 0x1000u) ++h;  // carries into the exponent correctly when the mantissa overflows
    return static_cast<uint16_t>(h);
}

/** 16-bit sample -> half float of sample / 65535, built once. */
const std::vector<uint16_t>& halfTable() {
    static const std::vector<uint16_t> table = [] {
        std::vector<uint16_t> t(65536);
        for (size_t i = 0; i < t.size(); ++i) t[i] = toHalf(static_cast<float>(i) / 65535.0f);
        return t;
    }();
    return table;
}

}  // namespace

RawDecoder::RawDecoder() : raw_(new LibRaw(LIBRAW_OPTIONS_NONE)) {}

RawDecoder::~RawDecoder() { releaseImage(); }

void RawDecoder::releaseImage() {
    if (image_ != nullptr) {
        LibRaw::dcraw_clear_mem(image_);
        image_ = nullptr;
    }
}

int RawDecoder::openFile(const char* path) { return raw_->open_file(path); }

int RawDecoder::openBuffer(const void* data, size_t size) { return raw_->open_buffer(data, size); }

int RawDecoder::probe(RawInfo* info) {
    raw_->imgdata.params.user_flip = 0;
    int flip = raw_->imgdata.sizes.flip;
    int ret = raw_->adjust_sizes_info_only();
    if (ret != LIBRAW_SUCCESS) return ret;
    info->width = raw_->imgdata.sizes.iwidth;
    info->height = raw_->imgdata.sizes.iheight;
    info->flip = flip;
    return LIBRAW_SUCCESS;
}

int RawDecoder::develop(bool halfSize) {
    releaseImage();
    libraw_output_params_t& p = raw_->imgdata.params;
    p.user_flip = 0;        // orientation is applied later, on the GPU
    p.output_bps = 16;
    p.output_color = 7;     // DCI-P3 primaries, D65 white: with the sRGB curve below this is Display P3
    p.gamm[0] = 1 / 2.4;    // sRGB transfer curve
    p.gamm[1] = 12.92;
    p.use_camera_wb = 1;
    p.half_size = halfSize ? 1 : 0;
    p.user_qual = 3;        // AHD demosaicing
    p.highlight = 2;        // blend clipped channels instead of tinting them magenta
    // Auto brightness clips the brightest 0.2% instead of 1%, keeping more highlight detail.
    p.auto_bright_thr = 0.002f;

    int ret = raw_->unpack();
    if (ret != LIBRAW_SUCCESS) return ret;
    ret = raw_->dcraw_process();
    if (ret != LIBRAW_SUCCESS) return ret;
    image_ = raw_->dcraw_make_mem_image(&ret);
    if (image_ == nullptr) return ret != LIBRAW_SUCCESS ? ret : LIBRAW_UNSPECIFIED_ERROR;
    // The intermediate buffers are no longer needed; free them before the caller allocates its copy.
    raw_->recycle();
    if (image_->type != LIBRAW_IMAGE_BITMAP || image_->bits != 16 ||
        (image_->colors != 1 && image_->colors != 3)) {
        releaseImage();
        return LIBRAW_FILE_UNSUPPORTED;
    }
    return LIBRAW_SUCCESS;
}

int RawDecoder::developedWidth() const { return image_ != nullptr ? image_->width : 0; }

int RawDecoder::developedHeight() const { return image_ != nullptr ? image_->height : 0; }

void RawDecoder::copyRgbaHalf(uint16_t* dst, size_t stride) const {
    if (image_ == nullptr) return;
    const std::vector<uint16_t>& half = halfTable();
    const int w = image_->width;
    const int h = image_->height;
    const int colors = image_->colors;
    const uint16_t one = toHalf(1.0f);
    const auto* src = reinterpret_cast<const uint16_t*>(image_->data);
    for (int y = 0; y < h; ++y) {
        auto* row = reinterpret_cast<uint16_t*>(reinterpret_cast<uint8_t*>(dst) + stride * y);
        for (int x = 0; x < w; ++x, src += colors, row += 4) {
            row[0] = half[src[0]];
            row[1] = half[src[colors == 3 ? 1 : 0]];
            row[2] = half[src[colors == 3 ? 2 : 0]];
            row[3] = one;
        }
    }
}

int RawDecoder::thumbnail(std::vector<uint8_t>* jpeg) {
    int ret = raw_->unpack_thumb();
    if (ret != LIBRAW_SUCCESS) return ret;
    if (raw_->imgdata.thumbnail.tformat != LIBRAW_THUMBNAIL_JPEG) return LIBRAW_NO_THUMBNAIL;
    libraw_processed_image_t* thumb = raw_->dcraw_make_mem_thumb(&ret);
    if (thumb == nullptr) return ret != LIBRAW_SUCCESS ? ret : LIBRAW_NO_THUMBNAIL;
    jpeg->assign(thumb->data, thumb->data + thumb->data_size);
    LibRaw::dcraw_clear_mem(thumb);
    return LIBRAW_SUCCESS;
}

const char* RawDecoder::errorMessage(int code) { return libraw_strerror(code); }

}  // namespace photograin
