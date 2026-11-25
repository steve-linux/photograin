// SPDX-License-Identifier: GPL-3.0-or-later
// Merges bracketed exposures of one scene into a single photo: exposure fusion after
// T. Mertens, J. Kautz, F. Van Reeth, "Exposure Fusion" (Pacific Graphics 2007), with
// translation alignment by median threshold bitmaps after G. Ward, "Fast, Robust Image
// Registration for Compositing High Dynamic Range Photographs from Handheld Exposures"
// (Journal of Graphics Tools, 2003). Plain C++ with no Android dependency.
#pragma once

#include <cstdint>
#include <functional>
#include <vector>

namespace photograin {

/** One exposure: RGBA pixels, 8 bits per channel or half floats (0..1), rows [stride] bytes apart. */
struct HdrSource {
    const void* pixels = nullptr;
    int width = 0;
    int height = 0;
    int stride = 0;
    bool half = false;
};

struct HdrOptions {
    /** Finds and removes the shift between handheld frames (up to 127 pixels on photos of 2 MP and more). */
    bool align = true;
    /** Keeps moving things (people, branches, waves) from the reference frame only. */
    bool deghost = true;
};

struct HdrResult {
    /** The area every aligned frame covers, in reference-frame pixels; the merge has this size. */
    int x = 0, y = 0, width = 0, height = 0;
    /** The frame with the middle brightness, which the others are aligned and compared to. */
    int reference = 0;
    /** Per frame: where its pixels sit relative to the reference's. */
    std::vector<int> dx, dy;
};

/**
 * Reports progress from 0 to 1; returning false cancels the merge.
 */
using HdrProgress = std::function<bool(float)>;

/**
 * Picks the reference frame and aligns every frame to it; fills everything in [result].
 * All sources must have the same size. Returns false if cancelled.
 */
bool alignExposures(const std::vector<HdrSource>& sources, const HdrOptions& options, HdrResult* result,
                    const HdrProgress& progress);

/**
 * Fuses the aligned [sources] into [out]: RGBA, 8 bits per channel, opaque, [result] width x
 * height, rows [outStride] bytes apart. Returns false if cancelled.
 */
bool fuseExposures(const std::vector<HdrSource>& sources, const HdrOptions& options, const HdrResult& result,
                   uint8_t* out, int outStride, const HdrProgress& progress);

/** Median threshold bitmap shift of [moving] against [fixed], both [w] x [h] gray planes. */
void mtbShift(const std::vector<uint8_t>& fixed, const std::vector<uint8_t>& moving, int w, int h,
              int levels, int* dx, int* dy);

}  // namespace photograin
