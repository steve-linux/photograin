// SPDX-License-Identifier: GPL-3.0-or-later
// U^2-Net-p subject segmentation with ncnn. Portable C++: no Android dependency.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace ncnn {
class Net;
}

namespace photograin {

class Segmenter {
public:
    /** The model always sees the photo squeezed to this square. */
    static constexpr int kSize = 320;

    Segmenter();
    ~Segmenter();

    /** Loads an ncnn model from memory. It runs on the CPU in fp32: the model is small, and fp16 shifts the mask. */
    bool load(const std::string& param, std::vector<unsigned char> model, std::string* error);

    /**
     * Finds the main subject of an RGBA image. [out] receives kSize x kSize values, 0 for
     * background to 255 for subject, stretched so the strongest pixel is 255.
     */
    bool run(const uint8_t* rgba, int w, int h, int stride, uint8_t* out, std::string* error);

private:
    std::unique_ptr<ncnn::Net> net_;
    std::vector<unsigned char> model_;  // ncnn reads weights in place, so they must outlive net_
};

}  // namespace photograin
