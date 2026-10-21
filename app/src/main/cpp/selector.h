// SPDX-License-Identifier: GPL-3.0-or-later
// MobileSAM object selection with ncnn. Portable C++: no Android dependency.
#pragma once

#include <memory>
#include <string>
#include <vector>

#include "mat.h"

namespace ncnn {
class Net;
}

namespace photograin {

class Selector {
public:
    /** The long edge of the image the encoder sees; it is padded to a square of this side. */
    static constexpr int kSize = 1024;
    /** The side of the square of mask values the decoder returns, over the padded image. */
    static constexpr int kMaskSize = 256;

    Selector();
    ~Selector();

    /**
     * Loads the encoder and decoder from memory, with [prompt] the weights that turn taps into
     * the decoder's input: the 2 x 128 random frequencies of the position encoding, then the
     * 256 values for a left-out tap, an included one, two box corners and no tap, as
     * little-endian floats. They run on the CPU in fp32.
     */
    bool load(const std::string& encoderParam, std::vector<unsigned char> encoderModel,
              const std::string& decoderParam, std::vector<unsigned char> decoderModel,
              const std::vector<unsigned char>& prompt, std::string* error);

    /**
     * Reads an RGBA image whose long edge is at most kSize, upright; later [select] calls
     * look at it. This is the slow part: seconds on a phone.
     */
    bool encode(const uint8_t* rgba, int w, int h, int stride, std::string* error);

    /**
     * The object at the [n] taps of [coords] (x, y pairs in pixels of the encoded image),
     * included where [labels] is 1 and left out where it is 0. [out] receives kMaskSize
     * squared values, positive on the object.
     */
    bool select(const float* coords, const int* labels, int n, float* out, std::string* error);

private:
    std::unique_ptr<ncnn::Net> encoder_;
    std::unique_ptr<ncnn::Net> decoder_;
    // ncnn reads weights in place, so they must outlive the nets.
    std::vector<unsigned char> encoderModel_;
    std::vector<unsigned char> decoderModel_;
    std::vector<float> frequencies_;  // 2 x 128
    std::vector<float> embeddings_;   // 5 x 256
    ncnn::Mat densePe_;               // the position encoding of every embedding cell
    ncnn::Mat embedding_;             // the encoded image, empty until encode()
};

}  // namespace photograin
