// SPDX-License-Identifier: GPL-3.0-or-later
#include "upscaler.h"

#include <algorithm>

#include "net.h"
#if NCNN_VULKAN
#include "gpu.h"
#endif

namespace photograin {

namespace {
constexpr int kTile = 200;  // input pixels per tile side, before padding
constexpr int kPad = 10;    // context around each tile, cut away from the result

/** Multiplies every value of [m] by [factor]. Done by hand: ncnn's helper needs its Scale layer. */
void scale(ncnn::Mat& m, float factor) {
    for (int c = 0; c < m.c; c++) {
        float* p = m.channel(c);
        const int n = m.w * m.h;
        for (int i = 0; i < n; i++) p[i] *= factor;
    }
}
}  // namespace

Upscaler::Upscaler() = default;

Upscaler::~Upscaler() {
    net_.reset();
}

bool Upscaler::load(const std::string& param, std::vector<unsigned char> model, bool gpu, std::string* error) {
    net_ = std::make_unique<ncnn::Net>();
    model_ = std::move(model);
#if NCNN_VULKAN
    gpu_ = gpu && ncnn::get_gpu_count() > 0;
#else
    (void)gpu;
    gpu_ = false;
#endif
    net_->opt.use_vulkan_compute = gpu_;
    net_->opt.use_fp16_packed = true;
    net_->opt.use_fp16_storage = true;
    net_->opt.use_fp16_arithmetic = false;  // keeps PReLU and the residual add accurate
    if (net_->load_param_mem(param.c_str()) != 0) {
        *error = "Cannot read the model description";
        return false;
    }
    if (net_->load_model(model_.data()) == 0) {
        *error = "Cannot read the model weights";
        return false;
    }
    return true;
}

bool Upscaler::run(const uint8_t* rgba, int w, int h, int stride, uint8_t* out, int outStride, std::string* error) {
    if (!net_) {
        *error = "Model not loaded";
        return false;
    }
    std::vector<uint8_t> tileOut;
    for (int ty = 0; ty < h; ty += kTile) {
        for (int tx = 0; tx < w; tx += kTile) {
            const int tw = std::min(kTile, w - tx);
            const int th = std::min(kTile, h - ty);
            const int x0 = std::max(0, tx - kPad);
            const int y0 = std::max(0, ty - kPad);
            const int x1 = std::min(w, tx + tw + kPad);
            const int y1 = std::min(h, ty + th + kPad);

            ncnn::Mat in = ncnn::Mat::from_pixels(rgba + y0 * stride + x0 * 4, ncnn::Mat::PIXEL_RGBA2RGB,
                                                  x1 - x0, y1 - y0, stride);
            scale(in, 1 / 255.f);
            ncnn::Extractor ex = net_->create_extractor();
            ex.input("in0", in);
            ncnn::Mat result;
            if (ex.extract("out0", result) != 0) {
                *error = "Inference failed";
                return false;
            }
            scale(result, 255.f);

            const int rw = result.w;
            tileOut.resize(static_cast<size_t>(rw) * result.h * 4);
            result.to_pixels(tileOut.data(), ncnn::Mat::PIXEL_RGB2RGBA, rw * 4);

            // Copy the tile without its padding.
            const int ox = (tx - x0) * kScale;
            const int oy = (ty - y0) * kScale;
            for (int y = 0; y < th * kScale; y++) {
                const uint8_t* src = tileOut.data() + (static_cast<size_t>(oy + y) * rw + ox) * 4;
                uint8_t* dst = out + static_cast<size_t>(ty * kScale + y) * outStride + tx * kScale * 4;
                std::copy(src, src + tw * kScale * 4, dst);
            }
        }
    }
    return true;
}

}  // namespace photograin
