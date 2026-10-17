// SPDX-License-Identifier: GPL-3.0-or-later
// Real-ESRGAN 4x super-resolution with ncnn. Portable C++: no Android dependency.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace ncnn {
class Net;
}

namespace photograin {

class Upscaler {
public:
    static constexpr int kScale = 4;

    Upscaler();
    ~Upscaler();

    /** Loads an ncnn model from memory; [gpu] asks for Vulkan, used only if a device exists. */
    bool load(const std::string& param, std::vector<unsigned char> model, bool gpu, std::string* error);

    /** Whether inference runs on the GPU. */
    bool gpu() const { return gpu_; }

    /**
     * Enlarges an opaque RGBA image [kScale] times into [out] (RGBA, kScale*w x kScale*h).
     * Works in overlapping tiles so memory stays bounded whatever the image size.
     */
    bool run(const uint8_t* rgba, int w, int h, int stride, uint8_t* out, int outStride, std::string* error);

private:
    std::unique_ptr<ncnn::Net> net_;
    std::vector<unsigned char> model_;  // ncnn reads weights in place, so they must outlive net_
    bool gpu_ = false;
};

}  // namespace photograin
