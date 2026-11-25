// SPDX-License-Identifier: GPL-3.0-or-later
#include "hdr_merge.h"

#include <algorithm>
#include <array>
#include <climits>
#include <cmath>
#include <cstring>
#include <numeric>
#include <thread>

namespace photograin {
namespace {

/** Runs [body](begin, end) over [0, n) split across the CPU cores. */
template <typename F>
void parallelFor(int n, F body) {
    int threads = static_cast<int>(std::max(1u, std::min(8u, std::thread::hardware_concurrency())));
    if (n < 64 || threads == 1) {
        body(0, n);
        return;
    }
    std::vector<std::thread> pool;
    int chunk = (n + threads - 1) / threads;
    for (int t = 0; t < threads; ++t) {
        int begin = t * chunk;
        int end = std::min(n, begin + chunk);
        if (begin >= end) break;
        pool.emplace_back([=, &body] { body(begin, end); });
    }
    for (auto& th : pool) th.join();
}

const std::vector<float>& halfTable() {
    static const std::vector<float> table = [] {
        std::vector<float> t(65536);
        for (int h = 0; h < 65536; ++h) {
            int sign = (h >> 15) & 1;
            int exponent = (h >> 10) & 0x1F;
            int mantissa = h & 0x3FF;
            float v;
            if (exponent == 0) {
                v = std::ldexp(static_cast<float>(mantissa), -24);
            } else if (exponent == 31) {
                v = mantissa == 0 ? INFINITY : 0.0f;  // NaN counts as black
            } else {
                v = std::ldexp(static_cast<float>(mantissa + 1024), exponent - 25);
            }
            t[h] = sign ? -v : v;
        }
        return t;
    }();
    return table;
}

/** Reads one pixel as RGB in 0..1. */
inline void readRgb(const HdrSource& s, int x, int y, float* rgb) {
    if (s.half) {
        const auto* p = reinterpret_cast<const uint16_t*>(static_cast<const uint8_t*>(s.pixels) + static_cast<size_t>(y) * s.stride) + 4 * x;
        const float* table = halfTable().data();
        for (int c = 0; c < 3; ++c) rgb[c] = std::min(1.0f, std::max(0.0f, table[p[c]]));
    } else {
        const uint8_t* p = static_cast<const uint8_t*>(s.pixels) + static_cast<size_t>(y) * s.stride + 4 * x;
        for (int c = 0; c < 3; ++c) rgb[c] = p[c] * (1.0f / 255.0f);
    }
}

inline float luma(const float* rgb) { return 0.2126f * rgb[0] + 0.7152f * rgb[1] + 0.0722f * rgb[2]; }

struct Plane {
    int w = 0, h = 0;
    std::vector<float> v;
    Plane() = default;
    Plane(int w_, int h_) : w(w_), h(h_), v(static_cast<size_t>(w_) * h_, 0.0f) {}
    float* row(int y) { return v.data() + static_cast<size_t>(y) * w; }
    const float* row(int y) const { return v.data() + static_cast<size_t>(y) * w; }
};

inline int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

/** Blurs with the 5-tap binomial kernel and keeps every other pixel. */
Plane reduce(const Plane& in) {
    static const float k[5] = {1 / 16.0f, 4 / 16.0f, 6 / 16.0f, 4 / 16.0f, 1 / 16.0f};
    int w2 = (in.w + 1) / 2, h2 = (in.h + 1) / 2;
    Plane tmp(w2, in.h);
    parallelFor(in.h, [&](int y0, int y1) {
        for (int y = y0; y < y1; ++y) {
            const float* src = in.row(y);
            float* dst = tmp.row(y);
            for (int x = 0; x < w2; ++x) {
                float sum = 0;
                for (int i = -2; i <= 2; ++i) sum += k[i + 2] * src[clampi(2 * x + i, 0, in.w - 1)];
                dst[x] = sum;
            }
        }
    });
    Plane out(w2, h2);
    parallelFor(h2, [&](int y0, int y1) {
        for (int y = y0; y < y1; ++y) {
            const float* r[5];
            for (int i = -2; i <= 2; ++i) r[i + 2] = tmp.row(clampi(2 * y + i, 0, in.h - 1));
            float* dst = out.row(y);
            for (int x = 0; x < w2; ++x) {
                float sum = 0;
                for (int i = 0; i < 5; ++i) sum += k[i] * r[i][x];
                dst[x] = sum;
            }
        }
    });
    return out;
}

/** Taps and weights that expand a coarse line of [coarse] samples to [fine] samples. */
struct Taps {
    std::vector<std::array<int, 3>> index;
    std::vector<std::array<float, 3>> weight;
};

Taps expandTaps(int fine, int coarse) {
    Taps t;
    t.index.resize(fine);
    t.weight.resize(fine);
    for (int x = 0; x < fine; ++x) {
        if (x % 2 == 0) {
            int j = x / 2;
            t.index[x] = {clampi(j - 1, 0, coarse - 1), clampi(j, 0, coarse - 1), clampi(j + 1, 0, coarse - 1)};
            t.weight[x] = {1 / 8.0f, 6 / 8.0f, 1 / 8.0f};
        } else {
            int j = (x - 1) / 2;
            t.index[x] = {clampi(j, 0, coarse - 1), clampi(j + 1, 0, coarse - 1), clampi(j + 1, 0, coarse - 1)};
            t.weight[x] = {0.5f, 0.5f, 0.0f};
        }
    }
    return t;
}

/** The value of [coarse] expanded to fine pixel (x, y). */
inline float expandAt(const Plane& coarse, const Taps& tx, const Taps& ty, int x, int y) {
    float sum = 0;
    for (int a = 0; a < 3; ++a) {
        float wy = ty.weight[y][a];
        if (wy == 0) continue;
        const float* r = coarse.row(ty.index[y][a]);
        sum += wy * (tx.weight[x][0] * r[tx.index[x][0]] + tx.weight[x][1] * r[tx.index[x][1]] +
                     tx.weight[x][2] * r[tx.index[x][2]]);
    }
    return sum;
}

std::vector<Plane> gaussianPyramid(Plane base, int levels) {
    std::vector<Plane> pyr;
    pyr.reserve(levels);
    pyr.push_back(std::move(base));
    for (int i = 1; i < levels; ++i) pyr.push_back(reduce(pyr.back()));
    return pyr;
}

/** Gray plane at 8 bits for alignment, as Ward's method uses. */
std::vector<uint8_t> grayPlane(const HdrSource& s) {
    std::vector<uint8_t> g(static_cast<size_t>(s.width) * s.height);
    parallelFor(s.height, [&](int y0, int y1) {
        float rgb[3];
        for (int y = y0; y < y1; ++y) {
            for (int x = 0; x < s.width; ++x) {
                readRgb(s, x, y, rgb);
                float v = (54 * rgb[0] + 183 * rgb[1] + 19 * rgb[2]) / 256.0f;
                g[static_cast<size_t>(y) * s.width + x] = static_cast<uint8_t>(std::lround(v * 255));
            }
        }
    });
    return g;
}

std::vector<uint8_t> halve(const std::vector<uint8_t>& in, int w, int h, int* w2, int* h2) {
    *w2 = std::max(1, w / 2);
    *h2 = std::max(1, h / 2);
    std::vector<uint8_t> out(static_cast<size_t>(*w2) * *h2);
    for (int y = 0; y < *h2; ++y) {
        for (int x = 0; x < *w2; ++x) {
            int x0 = std::min(2 * x, w - 1), x1 = std::min(2 * x + 1, w - 1);
            int y0 = std::min(2 * y, h - 1), y1 = std::min(2 * y + 1, h - 1);
            int sum = in[static_cast<size_t>(y0) * w + x0] + in[static_cast<size_t>(y0) * w + x1] +
                      in[static_cast<size_t>(y1) * w + x0] + in[static_cast<size_t>(y1) * w + x1];
            out[static_cast<size_t>(y) * *w2 + x] = static_cast<uint8_t>((sum + 2) / 4);
        }
    }
    return out;
}

void histogram(const std::vector<uint8_t>& g, double* cdf) {
    size_t hist[256] = {};
    for (uint8_t v : g) hist[v]++;
    size_t acc = 0;
    for (int i = 0; i < 256; ++i) cdf[i] = static_cast<double>(acc += hist[i]) / std::max<size_t>(1, g.size());
}

/** The value below which a [fraction] of the pixels lie. */
int percentile(const double* cdf, double fraction) {
    for (int i = 0; i < 256; ++i) {
        if (cdf[i] > fraction) return i;
    }
    return 255;
}

/**
 * Ward thresholds both frames at their median. A frame that is mostly blown out (or black)
 * has its median clipped, so both are thresholded instead at a share of pixels that is
 * well exposed in both: that share still splits the two frames at the same scene brightness.
 */
double commonFraction(const double* cdfA, const double* cdfB) {
    const int lowClip = 5, highClip = 250;
    double lo = std::max(cdfA[lowClip], cdfB[lowClip]);
    double hi = std::min(cdfA[highClip - 1], cdfB[highClip - 1]);
    if (hi <= lo) return 0.5;
    // The median, as Ward uses, whenever it is well exposed in both.
    if (lo < 0.45 && hi > 0.55) return 0.5;
    return (lo + hi) / 2;
}

/** Threshold (bit 0) and exclusion (bit 1) bitmaps of a gray plane, split at [threshold]. */
std::vector<uint8_t> bitmaps(const std::vector<uint8_t>& g, int threshold) {
    std::vector<uint8_t> b(g.size());
    for (size_t i = 0; i < g.size(); ++i) {
        b[i] = static_cast<uint8_t>((g[i] > threshold ? 1 : 0) | (std::abs(g[i] - threshold) > 4 ? 2 : 0));
    }
    return b;
}

/** Pixels whose threshold bits differ where both frames are reliable, with [m] shifted by (sx, sy). */
long long mtbError(const std::vector<uint8_t>& f, const std::vector<uint8_t>& m, int w, int h, int sx, int sy) {
    int x0 = std::max(0, -sx), x1 = std::min(w, w - sx);
    int y0 = std::max(0, -sy), y1 = std::min(h, h - sy);
    if (x1 <= x0 || y1 <= y0) return LLONG_MAX;
    std::vector<long long> partial(static_cast<size_t>(y1 - y0), 0);
    parallelFor(y1 - y0, [&](int a, int b) {
        for (int i = a; i < b; ++i) {
            int y = y0 + i;
            const uint8_t* rf = f.data() + static_cast<size_t>(y) * w;
            const uint8_t* rm = m.data() + static_cast<size_t>(y + sy) * w + sx;
            long long e = 0;
            for (int x = x0; x < x1; ++x) {
                uint8_t a1 = rf[x], b1 = rm[x];
                e += ((a1 ^ b1) & 1) & ((a1 & b1) >> 1);
            }
            partial[i] = e;
        }
    });
    return std::accumulate(partial.begin(), partial.end(), 0LL);
}

/** Maps reference values (0..255) to the frame's, so the reference predicts how the frame should look. */
std::array<std::array<float, 256>, 3> exposureMap(const HdrSource& ref, const HdrSource& frame, const HdrResult& r,
                                                  int dxRef, int dyRef, int dxFrame, int dyFrame) {
    std::array<std::array<double, 256>, 3> hr{}, hf{};
    int step = std::max(1, static_cast<int>(std::sqrt(static_cast<double>(r.width) * r.height / 250000.0)));
    float a[3], b[3];
    for (int y = 0; y < r.height; y += step) {
        for (int x = 0; x < r.width; x += step) {
            readRgb(ref, x + r.x + dxRef, y + r.y + dyRef, a);
            readRgb(frame, x + r.x + dxFrame, y + r.y + dyFrame, b);
            for (int c = 0; c < 3; ++c) {
                hr[c][static_cast<int>(a[c] * 255 + 0.5f)] += 1;
                hf[c][static_cast<int>(b[c] * 255 + 0.5f)] += 1;
            }
        }
    }
    std::array<std::array<float, 256>, 3> lut{};
    for (int c = 0; c < 3; ++c) {
        double total = 0;
        for (double v : hr[c]) total += v;
        std::array<double, 256> cf{};
        double acc = 0;
        for (int i = 0; i < 256; ++i) cf[i] = (acc += hf[c][i]) / total;
        acc = 0;
        int u = 0;
        for (int v = 0; v < 256; ++v) {
            double before = acc / total;
            acc += hr[c][v];
            double mid = (before + acc / total) / 2;
            while (u < 255 && cf[u] < mid) ++u;
            lut[c][v] = u / 255.0f;
        }
    }
    return lut;
}

/** Box blur of radius [r], run twice (close to a Gaussian). */
void blur(Plane& p, int r) {
    if (r <= 0) return;
    Plane tmp(p.w, p.h);
    for (int pass = 0; pass < 2; ++pass) {
        parallelFor(p.h, [&](int y0, int y1) {
            for (int y = y0; y < y1; ++y) {
                const float* s = p.row(y);
                float* d = tmp.row(y);
                double sum = 0;
                for (int i = -r; i <= r; ++i) sum += s[clampi(i, 0, p.w - 1)];
                for (int x = 0; x < p.w; ++x) {
                    d[x] = static_cast<float>(sum / (2 * r + 1));
                    sum += s[clampi(x + r + 1, 0, p.w - 1)] - s[clampi(x - r, 0, p.w - 1)];
                }
            }
        });
        parallelFor(p.w, [&](int x0, int x1) {
            for (int x = x0; x < x1; ++x) {
                double sum = 0;
                for (int i = -r; i <= r; ++i) sum += tmp.row(clampi(i, 0, p.h - 1))[x];
                for (int y = 0; y < p.h; ++y) {
                    p.row(y)[x] = static_cast<float>(sum / (2 * r + 1));
                    sum += tmp.row(clampi(y + r + 1, 0, p.h - 1))[x] - tmp.row(clampi(y - r, 0, p.h - 1))[x];
                }
            }
        });
    }
}

}  // namespace

void mtbShift(const std::vector<uint8_t>& fixed, const std::vector<uint8_t>& moving, int w, int h, int levels,
              int* dx, int* dy) {
    std::vector<std::vector<uint8_t>> pf{fixed}, pm{moving};
    std::vector<int> ws{w}, hs{h};
    for (int i = 0; i < levels; ++i) {
        int w2, h2;
        pf.push_back(halve(pf.back(), ws.back(), hs.back(), &w2, &h2));
        pm.push_back(halve(pm.back(), ws.back(), hs.back(), &w2, &h2));
        ws.push_back(w2);
        hs.push_back(h2);
    }
    int sx = 0, sy = 0;
    for (int level = levels; level >= 0; --level) {
        sx *= 2;
        sy *= 2;
        double cf[256], cm[256];
        histogram(pf[level], cf);
        histogram(pm[level], cm);
        double fraction = commonFraction(cf, cm);
        auto bf = bitmaps(pf[level], percentile(cf, fraction));
        auto bm = bitmaps(pm[level], percentile(cm, fraction));
        long long best = LLONG_MAX;
        int bx = sx, by = sy;
        for (int j = -1; j <= 1; ++j) {
            for (int i = -1; i <= 1; ++i) {
                long long e = mtbError(bf, bm, ws[level], hs[level], sx + i, sy + j);
                // Ties keep the smaller move.
                if (e < best || (e == best && std::abs(sx + i) + std::abs(sy + j) < std::abs(bx) + std::abs(by))) {
                    best = e;
                    bx = sx + i;
                    by = sy + j;
                }
            }
        }
        sx = bx;
        sy = by;
    }
    *dx = sx;
    *dy = sy;
}

bool alignExposures(const std::vector<HdrSource>& sources, const HdrOptions& options, HdrResult* result,
                    const HdrProgress& progress) {
    int n = static_cast<int>(sources.size());
    int w = sources[0].width, h = sources[0].height;

    // The reference is the frame of middle brightness: the others differ from it the least.
    std::vector<double> mean(n, 0);
    int step = std::max(1, static_cast<int>(std::sqrt(static_cast<double>(w) * h / 250000.0)));
    for (int k = 0; k < n; ++k) {
        double sum = 0;
        long count = 0;
        float rgb[3];
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                readRgb(sources[k], x, y, rgb);
                sum += luma(rgb);
                ++count;
            }
        }
        mean[k] = sum / std::max(1L, count);
    }
    std::vector<int> order(n);
    std::iota(order.begin(), order.end(), 0);
    std::stable_sort(order.begin(), order.end(), [&](int a, int b) { return mean[a] < mean[b]; });
    result->reference = order[n / 2];
    result->dx.assign(n, 0);
    result->dy.assign(n, 0);

    if (options.align && n > 1) {
        int levels = 0;
        // Up to 6 halvings, while the smallest level keeps at least 16 pixels: shifts up to 127 pixels.
        while (levels < 6 && std::min(w, h) >> (levels + 1) >= 16) ++levels;
        std::vector<uint8_t> ref = grayPlane(sources[result->reference]);
        int done = 0;
        for (int k = 0; k < n; ++k) {
            if (k == result->reference) continue;
            std::vector<uint8_t> g = grayPlane(sources[k]);
            mtbShift(ref, g, w, h, levels, &result->dx[k], &result->dy[k]);
            if (progress && !progress(static_cast<float>(++done) / (n - 1))) return false;
        }
    }

    int x0 = 0, y0 = 0, x1 = w, y1 = h;
    for (int k = 0; k < n; ++k) {
        x0 = std::max(x0, -result->dx[k]);
        y0 = std::max(y0, -result->dy[k]);
        x1 = std::min(x1, w - result->dx[k]);
        y1 = std::min(y1, h - result->dy[k]);
    }
    result->x = x0;
    result->y = y0;
    result->width = std::max(1, x1 - x0);
    result->height = std::max(1, y1 - y0);
    return true;
}

bool fuseExposures(const std::vector<HdrSource>& sources, const HdrOptions& options, const HdrResult& r,
                   uint8_t* out, int outStride, const HdrProgress& progress) {
    const int n = static_cast<int>(sources.size());
    const int w = r.width, h = r.height;
    const int ref = r.reference;

    int levels = 1;
    while (levels < 12 && std::min(w, h) >> levels >= 8) ++levels;

    // Taps to expand each level to the one above it.
    std::vector<Taps> tx(levels), ty(levels);
    std::vector<int> lw{w}, lh{h};
    for (int i = 1; i < levels; ++i) {
        lw.push_back((lw.back() + 1) / 2);
        lh.push_back((lh.back() + 1) / 2);
    }
    for (int i = 0; i + 1 < levels; ++i) {
        tx[i] = expandTaps(lw[i], lw[i + 1]);
        ty[i] = expandTaps(lh[i], lh[i + 1]);
    }

    // Sums over the frames of weight x Laplacian, per channel and level, and of the weights.
    std::vector<std::vector<Plane>> num(3);
    std::vector<Plane> den;
    for (int i = 0; i < levels; ++i) {
        for (int c = 0; c < 3; ++c) num[c].emplace_back(lw[i], lh[i]);
        den.emplace_back(lw[i], lh[i]);
    }

    const int ghostRadius = std::max(2, std::min(w, h) / 150);

    for (int k = 0; k < n; ++k) {
        const HdrSource& s = sources[k];
        const int ox = r.x + r.dx[k], oy = r.y + r.dy[k];

        // Mertens' weights: local contrast, saturation and closeness to mid-gray.
        Plane gray(w, h), weight(w, h);
        parallelFor(h, [&](int y0, int y1) {
            float rgb[3];
            for (int y = y0; y < y1; ++y) {
                float* g = gray.row(y);
                for (int x = 0; x < w; ++x) {
                    readRgb(s, x + ox, y + oy, rgb);
                    g[x] = luma(rgb);
                    float mu = (rgb[0] + rgb[1] + rgb[2]) / 3;
                    float sat = std::sqrt(((rgb[0] - mu) * (rgb[0] - mu) + (rgb[1] - mu) * (rgb[1] - mu) +
                                           (rgb[2] - mu) * (rgb[2] - mu)) / 3);
                    float well = 1;
                    for (int c = 0; c < 3; ++c) well *= std::exp(-(rgb[c] - 0.5f) * (rgb[c] - 0.5f) / 0.08f);
                    weight.row(y)[x] = (sat + 0.02f) * well;
                }
            }
        });
        parallelFor(h, [&](int y0, int y1) {
            for (int y = y0; y < y1; ++y) {
                const float* up = gray.row(std::max(0, y - 1));
                const float* mid = gray.row(y);
                const float* down = gray.row(std::min(h - 1, y + 1));
                float* wr = weight.row(y);
                for (int x = 0; x < w; ++x) {
                    float lap = 4 * mid[x] - mid[std::max(0, x - 1)] - mid[std::min(w - 1, x + 1)] - up[x] - down[x];
                    wr[x] = wr[x] * (std::fabs(lap) + 0.02f) + 1e-12f;
                }
            }
        });

        // Where this frame disagrees with what the reference predicts, something moved: trust the reference.
        if (options.deghost && k != ref && n > 1) {
            auto lut = exposureMap(sources[ref], s, r, r.dx[ref], r.dy[ref], r.dx[k], r.dy[k]);
            const HdrSource& rs = sources[ref];
            const int rx = r.x + r.dx[ref], ry = r.y + r.dy[ref];
            Plane diff(w, h);
            parallelFor(h, [&](int y0, int y1) {
                float a[3], b[3];
                for (int y = y0; y < y1; ++y) {
                    for (int x = 0; x < w; ++x) {
                        readRgb(rs, x + rx, y + ry, a);
                        readRgb(s, x + ox, y + oy, b);
                        float d = 0;
                        for (int c = 0; c < 3; ++c) {
                            d = std::max(d, std::fabs(b[c] - lut[c][static_cast<int>(a[c] * 255 + 0.5f)]));
                        }
                        // Only a well-exposed reference can stand in for the frame.
                        float l = luma(a);
                        float conf = std::min(1.0f, std::max(0.0f, std::min(l, 1 - l) / 0.08f));
                        diff.row(y)[x] = d * conf;
                    }
                }
            });
            blur(diff, ghostRadius);
            parallelFor(h, [&](int y0, int y1) {
                for (int y = y0; y < y1; ++y) {
                    for (int x = 0; x < w; ++x) {
                        float keep = std::min(1.0f, std::max(0.0f, 1 - (diff.row(y)[x] - 0.08f) / 0.12f));
                        weight.row(y)[x] = weight.row(y)[x] * keep + 1e-12f;
                    }
                }
            });
        }
        gray = Plane();

        std::vector<Plane> wp = gaussianPyramid(std::move(weight), levels);
        for (int i = 0; i < levels; ++i) {
            float* d = den[i].v.data();
            const float* src = wp[i].v.data();
            for (size_t p = 0; p < den[i].v.size(); ++p) d[p] += src[p];
        }

        for (int c = 0; c < 3; ++c) {
            Plane channel(w, h);
            parallelFor(h, [&](int y0, int y1) {
                float rgb[3];
                for (int y = y0; y < y1; ++y) {
                    float* row = channel.row(y);
                    for (int x = 0; x < w; ++x) {
                        readRgb(s, x + ox, y + oy, rgb);
                        row[x] = rgb[c];
                    }
                }
            });
            std::vector<Plane> gp = gaussianPyramid(std::move(channel), levels);
            for (int i = 0; i < levels; ++i) {
                Plane& acc = num[c][i];
                const Plane& g = gp[i];
                const Plane& wl = wp[i];
                const bool last = i + 1 == levels;
                parallelFor(g.h, [&](int y0, int y1) {
                    for (int y = y0; y < y1; ++y) {
                        const float* gr = g.row(y);
                        const float* wr = wl.row(y);
                        float* ar = acc.row(y);
                        for (int x = 0; x < g.w; ++x) {
                            float lap = last ? gr[x] : gr[x] - expandAt(gp[i + 1], tx[i], ty[i], x, y);
                            ar[x] += wr[x] * lap;
                        }
                    }
                });
            }
        }
        if (progress && !progress(static_cast<float>(k + 1) / (n + 1))) return false;
    }

    // Normalize each level by the summed weights, then collapse the pyramid.
    for (int i = 0; i < levels; ++i) {
        const float* d = den[i].v.data();
        for (int c = 0; c < 3; ++c) {
            float* a = num[c][i].v.data();
            for (size_t p = 0; p < num[c][i].v.size(); ++p) a[p] = d[p] > 0 ? a[p] / d[p] : 0;
        }
    }
    for (int c = 0; c < 3; ++c) {
        for (int i = levels - 2; i >= 0; --i) {
            Plane& fine = num[c][i];
            const Plane& coarse = num[c][i + 1];
            parallelFor(fine.h, [&](int y0, int y1) {
                for (int y = y0; y < y1; ++y) {
                    float* fr = fine.row(y);
                    for (int x = 0; x < fine.w; ++x) fr[x] += expandAt(coarse, tx[i], ty[i], x, y);
                }
            });
            num[c][i + 1] = Plane();
        }
    }
    parallelFor(h, [&](int y0, int y1) {
        for (int y = y0; y < y1; ++y) {
            uint8_t* o = out + static_cast<size_t>(y) * outStride;
            for (int x = 0; x < w; ++x) {
                for (int c = 0; c < 3; ++c) {
                    float v = std::min(1.0f, std::max(0.0f, num[c][0].row(y)[x]));
                    o[4 * x + c] = static_cast<uint8_t>(v * 255 + 0.5f);
                }
                o[4 * x + 3] = 255;
            }
        }
    });
    if (progress) progress(1.0f);
    return true;
}

}  // namespace photograin
