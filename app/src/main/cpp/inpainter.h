// SPDX-License-Identifier: GPL-3.0-or-later
// MI-GAN object removal with ncnn. Portable C++: no Android dependency.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace ncnn {
class Net;
}

namespace photograin {

class Inpainter {
public:
    /** The model works on squares of this size. */
    static constexpr int kSize = 512;

    Inpainter();
    ~Inpainter();

    /** Loads an ncnn model from memory. It runs on the CPU in fp32, like the segmenter. */
    bool load(const std::string& param, std::vector<unsigned char> model, std::string* error);

    /**
     * Fills the holes of a kSize x kSize RGBA image. [mask] has kSize x kSize values, nonzero
     * where the image is to be replaced. [out] receives the RGBA result (opaque), whose
     * pixels outside the holes are the model's own reconstruction.
     */
    bool run(const uint8_t* rgba, const uint8_t* mask, uint8_t* out, std::string* error);

private:
    std::unique_ptr<ncnn::Net> net_;
    std::vector<unsigned char> model_;  // ncnn reads weights in place, so they must outlive net_
};

}  // namespace photograin
