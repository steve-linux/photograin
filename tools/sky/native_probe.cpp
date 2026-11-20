// SPDX-License-Identifier: GPL-3.0-or-later
// Host-only integration probe for the actual portable Segmenter used by Android.
// Usage: native_probe model.param model.bin input.rgba output.mask
// input.rgba contains exactly 384*384 RGBA pixels in upright orientation.
#include "segmenter.h"
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>

int main(int argc, char** argv) {
    if (argc != 5) return 2;
    std::ifstream input(argv[3], std::ios::binary);
    std::vector<uint8_t> rgba((std::istreambuf_iterator<char>(input)), {});
    if (rgba.size() != 384 * 384 * 4) return 3;
    photograin::Segmenter model(384, nullptr, nullptr, false);
    std::string error;
    if (!model.loadFiles(argv[1], argv[2], &error)) { std::cerr << error; return 4; }
    std::vector<uint8_t> mask(384 * 384);
    if (!model.run(rgba.data(), 384, 384, 384 * 4, mask.data(), &error)) { std::cerr << error; return 5; }
    // Android loads assets from memory: check that path against the file-loading reference.
    std::ifstream paramFile(argv[1]);
    std::string param((std::istreambuf_iterator<char>(paramFile)), {});
    std::ifstream weightsFile(argv[2], std::ios::binary);
    std::vector<unsigned char> weights((std::istreambuf_iterator<char>(weightsFile)), {});
    photograin::Segmenter fromMemory(384, nullptr, nullptr, false);
    std::vector<uint8_t> memoryMask(mask.size());
    if (!fromMemory.load(param, std::move(weights), &error) ||
        !fromMemory.run(rgba.data(), 384, 384, 384 * 4, memoryMask.data(), &error)) {
        std::cerr << error; return 7;
    }
    if (memoryMask != mask) { std::cerr << "Memory/file inference mismatch"; return 8; }
    std::ofstream output(argv[4], std::ios::binary);
    output.write(reinterpret_cast<const char*>(mask.data()), static_cast<std::streamsize>(mask.size()));
    return output ? 0 : 6;
}
