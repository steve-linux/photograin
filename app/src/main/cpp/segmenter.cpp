// SPDX-License-Identifier: GPL-3.0-or-later
#include "segmenter.h"

#include <algorithm>
#include <cmath>

#include "net.h"

namespace photograin {

Segmenter::Segmenter(int size, const float mean[3], const float deviation[3]) : size_(size) {
    // ImageNet normalization, as U^2-Net-p was trained.
    const float imageNetMean[3] = {0.485f, 0.456f, 0.406f};
    const float imageNetStd[3] = {0.229f, 0.224f, 0.225f};
    for (int c = 0; c < 3; c++) {
        mean_[c] = mean ? mean[c] : imageNetMean[c];
        deviation_[c] = deviation ? deviation[c] : imageNetStd[c];
    }
}

Segmenter::~Segmenter() {
    net_.reset();
}

bool Segmenter::load(const std::string& param, std::vector<unsigned char> model, std::string* error) {
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

bool Segmenter::run(const uint8_t* rgba, int w, int h, int stride, uint8_t* out, std::string* error) {
    if (!net_) {
        *error = "Model not loaded";
        return false;
    }
    ncnn::Mat in = ncnn::Mat::from_pixels_resize(rgba, ncnn::Mat::PIXEL_RGBA2RGB, w, h, stride, size_, size_);
    // The normalization the model was trained with: (v / 255 - mean) / std. Done by hand:
    // ncnn's substract_mean_normalize() needs its Scale and Bias layers, which are not built.
    for (int c = 0; c < 3; c++) {
        float* p = in.channel(c);
        const float a = 1 / (255.f * deviation_[c]);
        const float b = -mean_[c] / deviation_[c];
        for (int i = 0; i < size_ * size_; i++) p[i] = p[i] * a + b;
    }

    ncnn::Extractor ex = net_->create_extractor();
    ex.input("in0", in);
    ncnn::Mat result;
    if (ex.extract("out0", result) != 0 || result.w != size_ || result.h != size_) {
        *error = "Inference failed";
        return false;
    }
    const float* p = result.channel(0);
    const int n = size_ * size_;
    const auto [lo, hi] = std::minmax_element(p, p + n);
    const float range = std::max(*hi - *lo, 1e-6f);
    for (int i = 0; i < n; i++) {
        out[i] = static_cast<uint8_t>(std::lround(std::clamp((p[i] - *lo) / range, 0.f, 1.f) * 255.f));
    }
    return true;
}

}  // namespace photograin
