// SPDX-License-Identifier: GPL-3.0-or-later
// Host test of the portable HDR merge (app/src/main/cpp/hdr_merge.cpp): synthetic brackets of a
// scene too contrasty for one exposure, one frame shifted as if handheld, one with a moving object.
//   g++ -O2 -std=c++17 -pthread -I../../app/src/main/cpp hdr_test.cpp ../../app/src/main/cpp/hdr_merge.cpp -o hdr_test && ./hdr_test
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <vector>

#include "hdr_merge.h"

using namespace photograin;

namespace {
int failures = 0;
void check(bool ok, const char* what) {
    std::printf("%s %s\n", ok ? "ok  " : "FAIL", what);
    if (!ok) ++failures;
}

const int W = 640, H = 480;

/** Irregular texture: random rectangles of many sizes, so frames can be aligned like real photos. */
std::vector<float> makeTexture() {
    std::vector<float> t(W * H, 0.0f);
    unsigned seed = 12345;
    auto rnd = [&] { seed = seed * 1103515245u + 12345u; return (seed >> 8) % 100000 / 100000.0f; };
    for (int i = 0; i < 1500; ++i) {
        int size = 4 + static_cast<int>(rnd() * 60);
        int x0 = static_cast<int>(rnd() * W), y0 = static_cast<int>(rnd() * H);
        float v = rnd() - 0.5f;
        for (int y = y0; y < std::min(H, y0 + size); ++y)
            for (int x = x0; x < std::min(W, x0 + size); ++x) t[y * W + x] += v;
    }
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            float v = t[y * W + x] + 0.4f * std::sin(x * 0.045f + y * 0.031f);  // no flat plateaus
            t[y * W + x] = std::exp(0.6f * std::fmax(-1.5f, std::fmin(1.5f, v)));
        }
    }
    return t;
}
const std::vector<float> texture = makeTexture();

/** Scene radiance: three bands, deep shadow, midtones and very bright, all textured. */
float radiance(int x, int y) {
    float base = x < W / 3 ? 0.01f : (x < 2 * W / 3 ? 0.2f : 3.0f);
    return base * texture[y * W + x];
}

float encode(float linear) {
    linear = std::fmin(1.0f, std::fmax(0.0f, linear));
    return linear <= 0.0031308f ? 12.92f * linear : 1.055f * std::pow(linear, 1 / 2.4f) - 0.055f;
}

/** One exposure, shifted by (sx, sy), optionally with a black gray square at [bx, by]. */
std::vector<uint8_t> shoot(float ev, int sx, int sy, int bx = -1, int by = -1) {
    std::vector<uint8_t> px(W * H * 4);
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            // This frame's pixel (x, y) sees the scene at (x - sx, y - sy).
            int u = std::min(W - 1, std::max(0, x - sx)), v = std::min(H - 1, std::max(0, y - sy));
            float l = radiance(u, v) * std::pow(2.0f, ev);
            bool object = bx >= 0 && u >= bx && u < bx + 60 && v >= by && v < by + 60;
            float val = object ? 0.5f : encode(l);
            for (int c = 0; c < 3; ++c) px[(y * W + x) * 4 + c] = static_cast<uint8_t>(val * 255 + 0.5f);
            px[(y * W + x) * 4 + 3] = 255;
        }
    }
    return px;
}

double stddev(const std::vector<uint8_t>& px, int stride, int x0, int y0, int x1, int y1) {
    double s = 0, s2 = 0;
    int n = 0;
    for (int y = y0; y < y1; ++y) {
        for (int x = x0; x < x1; ++x) {
            double v = px[y * stride + 4 * x + 1];
            s += v;
            s2 += v * v;
            ++n;
        }
    }
    double m = s / n;
    return std::sqrt(std::max(0.0, s2 / n - m * m));
}

double mean(const std::vector<uint8_t>& px, int stride, int x0, int y0, int x1, int y1) {
    double s = 0;
    int n = 0;
    for (int y = y0; y < y1; ++y) for (int x = x0; x < x1; ++x, ++n) s += px[y * stride + 4 * x + 1];
    return s / n;
}

HdrSource source(const std::vector<uint8_t>& px) {
    HdrSource s;
    s.pixels = px.data();
    s.width = W;
    s.height = H;
    s.stride = W * 4;
    return s;
}
}  // namespace

int main() {
    // Bright frame first: the reference must be found by brightness, not by order.
    auto bright = shoot(+3, 7, -4);
    auto mid = shoot(0, 0, 0);
    auto dark = shoot(-3, -5, 6, 280, 300);  // something walked through the dark frame
    std::vector<HdrSource> sources{source(bright), source(mid), source(dark)};

    HdrOptions options;
    HdrResult r;
    check(alignExposures(sources, options, &r, nullptr), "align runs");
    check(r.reference == 1, "middle exposure is the reference");
    std::printf("     shifts: bright (%d,%d) dark (%d,%d), crop %d,%d %dx%d\n", r.dx[0], r.dy[0], r.dx[2], r.dy[2],
                r.x, r.y, r.width, r.height);
    check(r.dx[0] == 7 && r.dy[0] == -4, "bright frame shift found");
    check(r.dx[2] == -5 && r.dy[2] == 6, "dark frame shift found");
    check(r.width == W - 12 && r.height == H - 10, "crop covers only shared pixels");

    std::vector<uint8_t> out(r.width * r.height * 4);
    int calls = 0;
    check(fuseExposures(sources, options, r, out.data(), r.width * 4, [&](float) { ++calls; return true; }),
          "fuse runs");
    check(calls == 4, "progress reported per frame and at the end");

    // Dark side: the mid frame is nearly black and flat; the merge must show its texture.
    int s = r.width * 4;
    double midDark = stddev(mid, W * 4, 40, 100, 180, 200);
    double fusedDark = stddev(out, s, 40 - r.x, 100 - r.y, 180 - r.x, 200 - r.y);
    std::printf("     dark-side texture: mid %.1f, fused %.1f\n", midDark, fusedDark);
    check(fusedDark > midDark * 1.5, "shadow detail recovered");
    double midBright = stddev(mid, W * 4, 460, 100, 600, 200);
    double fusedBright = stddev(out, s, 460 - r.x, 100 - r.y, 600 - r.x, 200 - r.y);
    std::printf("     bright-side texture: mid %.1f, fused %.1f\n", midBright, fusedBright);
    check(fusedBright > midBright * 1.5, "highlight detail recovered");
    double brightClip = mean(out, s, 460 - r.x, 100 - r.y, 600 - r.x, 200 - r.y);
    check(brightClip < 250, "highlights not blown");

    // The gray square (scene 280..340, 300..360) is only in the dark frame, in the midtone band
    // that the reference shows well. Compare with a merge without deghosting, where the square leaves a dark smear.
    HdrOptions noGhost;
    noGhost.deghost = false;
    std::vector<uint8_t> ghosty(out.size());
    fuseExposures(sources, noGhost, r, ghosty.data(), s, nullptr);
    double ghostWith = mean(out, s, 290 - r.x, 310 - r.y, 330 - r.x, 350 - r.y);
    double ghostWithout = mean(ghosty, s, 290 - r.x, 310 - r.y, 330 - r.x, 350 - r.y);
    // Without the object, the dark frame's texture there is what both merges should show.
    auto cleanDark = shoot(-3, -5, 6);
    std::vector<HdrSource> cleanSources{source(bright), source(mid), source(cleanDark)};
    std::vector<uint8_t> reference(out.size());
    fuseExposures(cleanSources, options, r, reference.data(), s, nullptr);
    double clean = mean(reference, s, 290 - r.x, 310 - r.y, 330 - r.x, 350 - r.y);
    std::printf("     moving object area: deghost %.1f, without %.1f, clean area %.1f\n", ghostWith, ghostWithout, clean);
    check(ghostWithout > clean + 5, "without deghosting the object leaves a ghost");
    check(std::fabs(ghostWith - clean) < 0.25 * std::fabs(ghostWithout - clean), "deghosting removes most of the ghost");

    // Single frame: merging one photo gives back that photo.
    std::vector<HdrSource> one{source(mid)};
    HdrResult r1;
    alignExposures(one, options, &r1, nullptr);
    std::vector<uint8_t> same(W * H * 4);
    fuseExposures(one, options, r1, same.data(), W * 4, nullptr);
    int maxDiff = 0;
    for (size_t i = 0; i < same.size(); ++i) maxDiff = std::max(maxDiff, std::abs(same[i] - mid[i]));
    check(maxDiff <= 1, "one frame merges to itself");

    // Half-float input reads like the 8-bit one.
    std::vector<uint16_t> half(W * H * 4);
    for (size_t i = 0; i < half.size(); ++i) {
        float v = mid[i] / 255.0f;
        // binary16 for v in [0,1]
        if (v == 0) { half[i] = 0; continue; }
        int e;
        float m = std::frexp(v, &e);  // v = m * 2^e, m in [0.5, 1)
        int exp16 = e - 1 + 15;
        int mant = static_cast<int>(std::lround((m * 2 - 1) * 1024));
        if (mant == 1024) { mant = 0; ++exp16; }
        half[i] = exp16 <= 0 ? static_cast<uint16_t>(std::lround(v * 16777216.0)) : static_cast<uint16_t>((exp16 << 10) | mant);
    }
    HdrSource hs;
    hs.pixels = half.data();
    hs.width = W;
    hs.height = H;
    hs.stride = W * 8;
    hs.half = true;
    std::vector<HdrSource> oneHalf{hs};
    HdrResult rh;
    alignExposures(oneHalf, options, &rh, nullptr);
    std::vector<uint8_t> fromHalf(W * H * 4);
    fuseExposures(oneHalf, options, rh, fromHalf.data(), W * 4, nullptr);
    maxDiff = 0;
    for (size_t i = 0; i < same.size(); ++i) maxDiff = std::max(maxDiff, std::abs(fromHalf[i] - mid[i]));
    check(maxDiff <= 1, "half-float frames read correctly");

    // Cancelling stops the merge.
    check(!fuseExposures(sources, options, r, out.data(), s, [](float) { return false; }), "cancel stops");

    std::printf("%s\n", failures ? "FAILED" : "all passed");
    return failures ? 1 : 0;
}
