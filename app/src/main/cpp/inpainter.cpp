// SPDX-License-Identifier: GPL-3.0-or-later
#include "inpainter.h"

#include <algorithm>
#include <cmath>

#include "net.h"

namespace photograin {

Inpainter::Inpainter() = default;

Inpainter::~Inpainter() {
    net_.reset();
}

bool Inpainter::load(const std::string& param, std::vector<unsigned char> model, std::string* error) {
    net_ = std::make_unique<ncnn::Net>();
    model_ = std::move(model);
    net_->opt.use_vulkan_compute = false;
    net_->opt.use_fp16_packed = false;
    net_->opt.use_fp16_storage = false;
    net_->opt.use_fp16_arithmetic = false;
    net_->opt.use_bf16_storage = false;
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

bool Inpainter::run(const uint8_t* rgba, const uint8_t* mask, uint8_t* out, std::string* error) {
    if (!net_) {
        *error = "Model not loaded";
        return false;
    }
    // The input the model was trained on: 0.5 - hole, then the RGB image in -1..1 with the
    // holes blanked out.
    const int n = kSize * kSize;
    ncnn::Mat in(kSize, kSize, 4);
    float* m = in.channel(0);
    for (int i = 0; i < n; i++) m[i] = mask[i] ? -0.5f : 0.5f;
    for (int c = 0; c < 3; c++) {
        float* p = in.channel(c + 1);
        for (int i = 0; i < n; i++) p[i] = mask[i] ? 0.f : rgba[i * 4 + c] / 127.5f - 1.f;
    }

    ncnn::Extractor ex = net_->create_extractor();
    ex.input("in0", in);
    ncnn::Mat result;
    if (ex.extract("out0", result) != 0 || result.w != kSize || result.h != kSize || result.c != 3) {
        *error = "Inference failed";
        return false;
    }
    for (int c = 0; c < 3; c++) {
        const float* p = result.channel(c);
        for (int i = 0; i < n; i++) {
            out[i * 4 + c] = static_cast<uint8_t>(std::lround(std::clamp((p[i] + 1.f) * 127.5f, 0.f, 255.f)));
        }
    }
    for (int i = 0; i < n; i++) out[i * 4 + 3] = 255;
    return true;
}

}  // namespace photograin
