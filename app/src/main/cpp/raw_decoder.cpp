// SPDX-License-Identifier: GPL-3.0-or-later
#include "raw_decoder.h"

namespace photograin {

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
    p.output_bps = 8;
    p.output_color = 1;     // sRGB primaries
    p.gamm[0] = 1 / 2.4;    // sRGB transfer curve
    p.gamm[1] = 12.92;
    p.use_camera_wb = 1;
    p.half_size = halfSize ? 1 : 0;
    p.user_qual = 3;        // AHD demosaicing

    int ret = raw_->unpack();
    if (ret != LIBRAW_SUCCESS) return ret;
    ret = raw_->dcraw_process();
    if (ret != LIBRAW_SUCCESS) return ret;
    image_ = raw_->dcraw_make_mem_image(&ret);
    if (image_ == nullptr) return ret != LIBRAW_SUCCESS ? ret : LIBRAW_UNSPECIFIED_ERROR;
    // The intermediate buffers are no longer needed; free them before the caller allocates its copy.
    raw_->recycle();
    if (image_->type != LIBRAW_IMAGE_BITMAP || image_->bits != 8 ||
        (image_->colors != 1 && image_->colors != 3)) {
        releaseImage();
        return LIBRAW_FILE_UNSUPPORTED;
    }
    return LIBRAW_SUCCESS;
}

int RawDecoder::developedWidth() const { return image_ != nullptr ? image_->width : 0; }

int RawDecoder::developedHeight() const { return image_ != nullptr ? image_->height : 0; }

void RawDecoder::copyRgba(uint8_t* dst, size_t stride) const {
    if (image_ == nullptr) return;
    const int w = image_->width;
    const int h = image_->height;
    const int colors = image_->colors;
    const uint8_t* src = image_->data;
    for (int y = 0; y < h; ++y) {
        uint8_t* row = dst + stride * y;
        for (int x = 0; x < w; ++x, src += colors, row += 4) {
            row[0] = src[0];
            row[1] = src[colors == 3 ? 1 : 0];
            row[2] = src[colors == 3 ? 2 : 0];
            row[3] = 0xff;
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
