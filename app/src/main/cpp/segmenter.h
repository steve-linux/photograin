// SPDX-License-Identifier: GPL-3.0-or-later
// Subject segmentation with ncnn: U^2-Net-p, or IS-Net from a model pack. Portable C++: no
// Android dependency.
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
    /** U^2-Net-p, the bundled model, sees the photo squeezed to this square. */
    static constexpr int kSize = 320;

    /**
     * A model that sees the photo squeezed to a [size] square, with each channel c normalized
     * as (v / 255 - mean[c]) / deviation[c]. The default is U^2-Net-p.
     */
    Segmenter(int size = kSize, const float mean[3] = nullptr, const float deviation[3] = nullptr);
    ~Segmenter();

    /** Loads an ncnn model from memory. It runs on the CPU in fp32: the model is small, and fp16 shifts the mask. */
    bool load(const std::string& param, std::vector<unsigned char> model, std::string* error);

    int size() const { return size_; }

    /**
     * Finds the main subject of an RGBA image. [out] receives size() x size() values, 0 for
     * background to 255 for subject, stretched so the strongest pixel is 255.
     */
    bool run(const uint8_t* rgba, int w, int h, int stride, uint8_t* out, std::string* error);

private:
    int size_;
    float mean_[3];
    float deviation_[3];
    std::unique_ptr<ncnn::Net> net_;
    std::vector<unsigned char> model_;  // ncnn reads weights in place, so they must outlive net_
};

}  // namespace photograin
