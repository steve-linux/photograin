// SPDX-License-Identifier: GPL-3.0-or-later
#include "selector.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "net.h"

namespace photograin {

namespace {

constexpr int kEmbed = 256;
constexpr int kFeatures = kEmbed / 2;
constexpr int kGrid = 64;  // the encoder's output is kGrid x kGrid cells
constexpr float kPi = 3.14159265358979f;
constexpr float kMean[3] = {123.675f, 116.28f, 103.53f};
constexpr float kStd[3] = {58.395f, 57.12f, 57.375f};

std::unique_ptr<ncnn::Net> newNet() {
    auto net = std::make_unique<ncnn::Net>();
    net->opt.use_vulkan_compute = false;
    net->opt.use_fp16_packed = false;
    net->opt.use_fp16_storage = false;
    net->opt.use_fp16_arithmetic = false;
    net->opt.use_bf16_storage = false;
    return net;
}

bool loadNet(ncnn::Net* net, const std::string& param, const std::vector<unsigned char>& model, std::string* error) {
    if (net->load_param_mem(param.c_str()) != 0) {
        *error = "Cannot read the model description";
        return false;
    }
    if (net->load_model(model.data()) == 0) {
        *error = "Cannot read the model weights";
        return false;
    }
    return true;
}

}  // namespace

Selector::Selector() = default;

Selector::~Selector() {
    embedding_.release();
    encoder_.reset();
    decoder_.reset();
}

bool Selector::load(const std::string& encoderParam, std::vector<unsigned char> encoderModel,
                    const std::string& decoderParam, std::vector<unsigned char> decoderModel,
                    const std::vector<unsigned char>& prompt, std::string* error) {
    if (prompt.size() != (2 * kFeatures + 5 * kEmbed) * sizeof(float)) {
        *error = "The prompt weights have the wrong size";
        return false;
    }
    frequencies_.resize(2 * kFeatures);
    embeddings_.resize(5 * kEmbed);
    std::memcpy(frequencies_.data(), prompt.data(), frequencies_.size() * sizeof(float));
    std::memcpy(embeddings_.data(), prompt.data() + frequencies_.size() * sizeof(float), embeddings_.size() * sizeof(float));

    encoderModel_ = std::move(encoderModel);
    decoderModel_ = std::move(decoderModel);
    encoder_ = newNet();
    decoder_ = newNet();
    if (!loadNet(encoder_.get(), encoderParam, encoderModel_, error)) return false;
    if (!loadNet(decoder_.get(), decoderParam, decoderModel_, error)) return false;

    // The position encoding of the center of every cell, as the decoder was traced to take it.
    densePe_.create(kGrid, kGrid, kEmbed);
    for (int y = 0; y < kGrid; y++) {
        for (int x = 0; x < kGrid; x++) {
            float u = 2.f * (x + 0.5f) / kGrid - 1.f;
            float v = 2.f * (y + 0.5f) / kGrid - 1.f;
            for (int f = 0; f < kFeatures; f++) {
                float a = 2.f * kPi * (u * frequencies_[f] + v * frequencies_[kFeatures + f]);
                densePe_.channel(f)[y * kGrid + x] = std::sin(a);
                densePe_.channel(kFeatures + f)[y * kGrid + x] = std::cos(a);
            }
        }
    }
    return true;
}

bool Selector::encode(const uint8_t* rgba, int w, int h, int stride, std::string* error) {
    if (!encoder_) {
        *error = "Model not loaded";
        return false;
    }
    if (w < 1 || h < 1 || w > kSize || h > kSize) {
        *error = "The image has the wrong size";
        return false;
    }
    // Normalized as the model was trained, in the top left corner; zero (the mean) elsewhere.
    ncnn::Mat in(kSize, kSize, 3);
    in.fill(0.f);
    for (int c = 0; c < 3; c++) {
        float* p = in.channel(c);
        for (int y = 0; y < h; y++) {
            const uint8_t* row = rgba + static_cast<size_t>(y) * stride;
            for (int x = 0; x < w; x++) p[y * kSize + x] = (row[x * 4 + c] - kMean[c]) / kStd[c];
        }
    }
    ncnn::Extractor ex = encoder_->create_extractor();
    ex.input("in0", in);
    ncnn::Mat out;
    if (ex.extract("out0", out) != 0 || out.w != kGrid || out.h != kGrid || out.c != kEmbed) {
        embedding_.release();
        *error = "Inference failed";
        return false;
    }
    embedding_ = out.clone();
    return true;
}

bool Selector::select(const float* coords, const int* labels, int n, float* out, std::string* error) {
    if (embedding_.empty()) {
        *error = "No image encoded";
        return false;
    }
    if (n < 1) {
        *error = "No taps";
        return false;
    }
    // One token per tap, then one for "no tap": the model was trained to see it without a box.
    ncnn::Mat tokens(kEmbed, n + 1);
    for (int i = 0; i < n; i++) {
        float* t = tokens.row(i);
        float u = 2.f * (coords[2 * i] + 0.5f) / kSize - 1.f;
        float v = 2.f * (coords[2 * i + 1] + 0.5f) / kSize - 1.f;
        const float* kind = embeddings_.data() + (labels[i] == 1 ? 1 : 0) * kEmbed;
        for (int f = 0; f < kFeatures; f++) {
            float a = 2.f * kPi * (u * frequencies_[f] + v * frequencies_[kFeatures + f]);
            t[f] = std::sin(a) + kind[f];
            t[kFeatures + f] = std::cos(a) + kind[kFeatures + f];
        }
    }
    std::copy_n(embeddings_.data() + 4 * kEmbed, kEmbed, static_cast<float*>(tokens.row(n)));

    ncnn::Extractor ex = decoder_->create_extractor();
    ex.input("in0", embedding_);
    ex.input("in1", tokens);
    ex.input("in2", densePe_);
    ncnn::Mat masks, scores;
    if (ex.extract("out0", masks) != 0 || ex.extract("out1", scores) != 0 ||
        masks.w != kMaskSize || masks.h != kMaskSize || masks.c != 4 || scores.w != 4) {
        *error = "Inference failed";
        return false;
    }
    // One tap is ambiguous (a shirt or the whole person): the three answers for it are scored,
    // and the best one wins. With more taps the single answer for them is better.
    int best = 0;
    if (n == 1) {
        best = 1;
        for (int i = 2; i < 4; i++) if (scores[i] > scores[best]) best = i;
    }
    std::copy_n(static_cast<const float*>(masks.channel(best)), kMaskSize * kMaskSize, out);
    return true;
}

}  // namespace photograin
