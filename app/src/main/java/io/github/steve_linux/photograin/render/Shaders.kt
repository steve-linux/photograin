// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

/**
 * GLSL ES 3.00 sources of the rendering pipeline.
 *
 * Coordinates: offscreen buffers store the image top row first (row 0 at GL's bottom),
 * which matches how bitmaps are uploaded and read back. Only drawing to the screen flips Y.
 */
object Shaders {
    /** Draws a quad over [uDst] (clip space) and interpolates [uSrc] (origin.xy, size.zw) into vCoord. */
    const val VERTEX = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform vec4 uDst;
uniform vec4 uSrc;
out vec2 vCoord;
void main() {
    gl_Position = vec4(mix(uDst.xy, uDst.zw, aPos), 0.0, 1.0);
    vCoord = uSrc.xy + aPos * uSrc.zw;
}
"""

    /** Resamples the source through the geometry transform (projective); vCoord is in output pixels. */
    const val GEOMETRY = """#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform mat3 uM;
uniform vec2 uTexSize;
uniform bool uCubic;
in vec2 vCoord;
out vec4 oColor;

// Catmull-Rom with 9 bilinear taps: sharper than bilinear when enlarging, without its blur.
vec3 catmullRom(vec2 uv) {
    vec2 pos = uv * uTexSize;
    vec2 p1 = floor(pos - 0.5) + 0.5;
    vec2 f = pos - p1;
    vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
    vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
    vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
    vec2 w3 = f * f * (-0.5 + 0.5 * f);
    vec2 w12 = w1 + w2;
    vec2 t0 = (p1 - 1.0) / uTexSize;
    vec2 t3 = (p1 + 2.0) / uTexSize;
    vec2 t12 = (p1 + w2 / w12) / uTexSize;
    vec3 c = vec3(0.0);
    c += textureLod(uTex, vec2(t0.x, t0.y), 0.0).rgb * w0.x * w0.y;
    c += textureLod(uTex, vec2(t12.x, t0.y), 0.0).rgb * w12.x * w0.y;
    c += textureLod(uTex, vec2(t3.x, t0.y), 0.0).rgb * w3.x * w0.y;
    c += textureLod(uTex, vec2(t0.x, t12.y), 0.0).rgb * w0.x * w12.y;
    c += textureLod(uTex, vec2(t12.x, t12.y), 0.0).rgb * w12.x * w12.y;
    c += textureLod(uTex, vec2(t3.x, t12.y), 0.0).rgb * w3.x * w12.y;
    c += textureLod(uTex, vec2(t0.x, t3.y), 0.0).rgb * w0.x * w3.y;
    c += textureLod(uTex, vec2(t12.x, t3.y), 0.0).rgb * w12.x * w3.y;
    c += textureLod(uTex, vec2(t3.x, t3.y), 0.0).rgb * w3.x * w3.y;
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec3 hm = uM * vec3(vCoord, 1.0);
    vec2 uv = hm.xy / hm.z;
    vec2 inside = step(vec2(-0.0005), uv) * step(uv, vec2(1.0005));
    vec2 st = clamp(uv, 0.0, 1.0);
    vec3 c = uCubic ? catmullRom(st) : texture(uTex, st).rgb;
    oColor = vec4(c, inside.x * inside.y);
}
"""

    /** One direction of a separable Gaussian; uLod lets the first pass downsample via mipmaps. */
    const val BLUR = """#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform vec2 uStep;
uniform float uLod;
uniform float uSigma;
in vec2 vCoord;
out vec4 oColor;
void main() {
    vec4 acc = vec4(0.0);
    float wsum = 0.0;
    for (int i = -12; i <= 12; i++) {
        float x = float(i);
        float w = exp(-x * x / (2.0 * uSigma * uSigma));
        acc += w * textureLod(uTex, vCoord + uStep * x, uLod);
        wsum += w;
    }
    oColor = acc / wsum;
}
"""

    /** All global adjustments. vCoord is the UV in the geometry texture. */
    const val COLOR = """#version 300 es
precision highp float;
uniform sampler2D uGeo;
uniform sampler2D uBlur;
uniform vec2 uGeoTexel;
// Maps vCoord to the position in the whole output image (0..1): xy + vCoord * zw.
uniform vec4 uImgMap;
// Output width and height divided by its long edge.
uniform vec2 uAspect;
// Long edge of the output at the resolution being rendered (preview or export).
uniform float uLongEdgePx;

uniform vec3 uWb;
uniform float uExposure;
uniform float uContrast;
uniform float uHighlights;
uniform float uShadows;
uniform float uWhites;
uniform float uBlacks;
uniform float uVibrance;
uniform float uSaturation;
uniform float uSharpness;
uniform float uClarity;
uniform float uDehaze;
uniform float uVignette;
uniform float uGrain;
uniform float uGrainCell;
uniform float uLumaNr;
uniform float uChromaNr;
// 256x1 lookup: channel c at input i = curve_c(master(i)).
uniform sampler2D uCurve;
uniform float uCurvesOn;
// Per band: hue shift, saturation, luminance (-1..1).
uniform vec3 uHsl[8];
uniform float uHslOn;
// Creative 3D LUT (.cube), sampled at texel centers: coord = v * uLutScale + uLutOffset.
uniform highp sampler3D uLut;
uniform float uLutOn;
uniform float uLutAmount;
uniform float uLutScale;
uniform float uLutOffset;
// LUTs are made for sRGB: working-space linear <-> sRGB linear around the lookup.
uniform float uLutConvert;
uniform mat3 uToSrgb;
uniform mat3 uFromSrgb;

// Working-space linear -> output linear, then the output transfer curve (gamma 0 = sRGB curve).
uniform float uOutConvert;
uniform mat3 uOutM;
uniform float uOutGamma;
// Luminance weights of the working space.
uniform vec3 uLuma;

// Local adjustments. uPosToSrc maps the output position (0..1) to the source (0..1); multiplying
// by uSrcAspect (width and height over the long edge) gives units where circles stay round.
uniform int uMaskCount;
uniform mat3 uPosToSrc;
uniform vec2 uSrcAspect;
// Linear: start xy, end zw. Radial: center xy, radii zw (in long-edge units).
uniform vec4 uMaskGeo[8];
// Type (0 linear, 1 radial, 2 brush), angle (brush: layer of uBrush), feather, invert.
uniform vec4 uMaskOpt[8];
// Painted brush masks over the whole source, one layer per mask (see BrushRaster).
uniform mediump sampler2DArray uBrush;
// Exposure, contrast, highlights, shadows.
uniform vec4 uLocA[8];
// Temperature, tint, saturation, clarity.
uniform vec4 uLocB[8];
// Index of the mask to tint red on screen, or -1.
uniform int uShowMask;

// Pixels left of this x (0..1 in the output) show the original. -1 disables comparison.
uniform float uCompare;
// 1.0 composites over uBg (screen); 0.0 writes the color with the geometry alpha (export).
uniform float uComposite;
uniform vec3 uBg;

in vec2 vCoord;
out vec4 oColor;

#define LUMA uLuma

vec3 toLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(0.04045, c));
}

vec3 toSrgb(vec3 c) {
    c = max(c, 0.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
}

vec3 contrastCurve(vec3 v, float k) {
    float e = exp2(k);
    vec3 lo = 0.5 * pow(2.0 * v, vec3(e));
    vec3 hi = 1.0 - 0.5 * pow(2.0 - 2.0 * v, vec3(e));
    return mix(lo, hi, step(0.5, v));
}

float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

const float BAND_HUE[8] = float[8](0.0, 30.0, 60.0, 120.0, 180.0, 240.0, 270.0, 300.0);

vec3 rgbToHsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsvToRgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

vec3 applyHsl(vec3 v) {
    vec3 hsv = rgbToHsv(v);
    float h = hsv.x * 360.0;
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 8; i++) {
        float d = abs(mod(h - BAND_HUE[i] + 180.0, 360.0) - 180.0);
        float w = max(0.0, 1.0 - d / 45.0);
        w *= w;
        acc += w * uHsl[i];
        wsum += w;
    }
    acc /= max(wsum, 1.0);
    hsv.x = fract(hsv.x + acc.x * 30.0 / 360.0 + 1.0);
    hsv.y = clamp(hsv.y * (1.0 + acc.y), 0.0, 1.0);
    // Luminance only moves colored pixels; grays have no hue to belong to.
    hsv.z = clamp(hsv.z * exp2(acc.z * 0.8 * hsv.y), 0.0, 1.0);
    return hsvToRgb(hsv);
}

vec3 applyCurves(vec3 v) {
    vec3 t = v * (255.0 / 256.0) + 0.5 / 256.0;
    return vec3(
        texture(uCurve, vec2(t.r, 0.5)).r,
        texture(uCurve, vec2(t.g, 0.5)).g,
        texture(uCurve, vec2(t.b, 0.5)).b);
}

// Edge-preserving luminance smoothing (5x5 bilateral) and chroma smoothing (5x5 Gaussian).
vec3 denoise(vec3 s) {
    float ls = dot(s, LUMA);
    float sigmaR = 0.02 + 0.12 * uLumaNr;
    vec3 accL = vec3(0.0);
    float wL = 0.0;
    vec3 accC = vec3(0.0);
    float wC = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec3 n = texture(uGeo, vCoord + vec2(float(x), float(y)) * uGeoTexel).rgb;
            float ln = dot(n, LUMA);
            float ws = exp(-float(x * x + y * y) / 4.5);
            float wr = exp(-(ln - ls) * (ln - ls) / (2.0 * sigmaR * sigmaR));
            accL += ws * wr * n;
            wL += ws * wr;
            accC += ws * (n - ln);
            wC += ws;
        }
    }
    vec3 o = mix(s, accL / wL, uLumaNr);
    float lo = dot(o, LUMA);
    return clamp(lo + mix(o - lo, accC / wC, uChromaNr), 0.0, 1.0);
}

// Same as ShaderParams.whiteBalanceGains.
vec3 wbGains(float temp, float tint) {
    vec3 g = vec3(1.0 + 0.35 * temp, 1.0 - 0.25 * tint, 1.0 - 0.35 * temp);
    return g / dot(g, vec3(0.2126, 0.7152, 0.0722));
}

// Same as Mask.weight.
float maskWeight(int i, vec2 pos) {
    vec3 hp = uPosToSrc * vec3(pos, 1.0);
    vec2 q = hp.xy / hp.z * uSrcAspect;
    vec4 g = uMaskGeo[i];
    vec4 o = uMaskOpt[i];
    float w;
    if (o.x > 1.5) {
        vec2 src = clamp(q / uSrcAspect, 0.0, 1.0);
        w = textureLod(uBrush, vec3(src, o.y), 0.0).r;
    } else if (o.x < 0.5) {
        vec2 a = g.xy * uSrcAspect;
        vec2 d = g.zw * uSrcAspect - a;
        float t = dot(q - a, d) / max(dot(d, d), 1e-8);
        w = 1.0 - smoothstep(0.0, 1.0, t);
    } else {
        vec2 d = q - g.xy * uSrcAspect;
        float cs = cos(o.y);
        float sn = sin(o.y);
        vec2 r = vec2(cs * d.x + sn * d.y, -sn * d.x + cs * d.y) / max(g.zw, vec2(1e-5));
        w = 1.0 - smoothstep(1.0 - max(o.z, 0.001), 1.0, length(r));
    }
    return o.w > 0.5 ? 1.0 - w : w;
}

vec3 process(vec3 s, vec2 pos) {
    vec4 la = vec4(0.0);
    vec4 lb = vec4(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= uMaskCount) break;
        float w = maskWeight(i, pos);
        la += w * uLocA[i];
        lb += w * uLocB[i];
    }
    float clarity = uClarity + lb.w;

    if (uLumaNr > 0.0 || uChromaNr > 0.0) {
        s = denoise(s);
    }

    // Sharpening: unsharp mask with the 4 direct neighbours, on the encoded values.
    if (uSharpness > 0.0) {
        vec3 n = texture(uGeo, vCoord + vec2(uGeoTexel.x, 0.0)).rgb
               + texture(uGeo, vCoord - vec2(uGeoTexel.x, 0.0)).rgb
               + texture(uGeo, vCoord + vec2(0.0, uGeoTexel.y)).rgb
               + texture(uGeo, vCoord - vec2(0.0, uGeoTexel.y)).rgb;
        s = clamp(s + (s - 0.25 * n) * uSharpness * 1.5, 0.0, 1.0);
    }

    vec3 c = toLinear(s);
    vec3 bl = toLinear(texture(uBlur, vCoord).rgb);

    float gain = exp2(uExposure + la.x);
    vec3 wb = uMaskCount > 0 ? uWb * wbGains(lb.x, lb.y) : uWb;
    c *= wb * gain;
    bl *= wb * gain;

    // Shadows and highlights driven by local (blurred) luminance to limit halos.
    float localL = toSrgb(vec3(dot(bl, LUMA))).x;
    float wS = 1.0 - smoothstep(0.0, 0.6, localL);
    float wH = smoothstep(0.4, 1.0, localL);
    c *= exp2((uShadows + la.w) * 1.5 * wS + (uHighlights + la.z) * 1.5 * wH);

    vec3 v = toSrgb(c);
    vec3 bv = toSrgb(bl);

    if (uDehaze > 0.0) {
        float haze = uDehaze * 0.85 * min(min(bv.r, bv.g), bv.b);
        v = (v - haze) / max(1.0 - haze, 0.05);
    } else if (uDehaze < 0.0) {
        v = mix(v, vec3(0.85), -uDehaze * 0.5);
    }

    float bp = -uBlacks * 0.15;
    float wp = 1.0 - uWhites * 0.2;
    v = clamp((v - bp) / (wp - bp), 0.0, 1.0);

    v = contrastCurve(v, uContrast + la.y);

    if (clarity != 0.0) {
        float lv = dot(v, LUMA);
        float lbl = dot(bv, LUMA);
        float mid = 1.0 - pow(abs(2.0 * lv - 1.0), 2.0);
        v += clarity * 1.5 * (lv - lbl) * mid;
    }

    if (uCurvesOn > 0.5) {
        v = applyCurves(clamp(v, 0.0, 1.0));
    }

    float y = dot(v, LUMA);
    float sat = max(v.r, max(v.g, v.b)) - min(v.r, min(v.g, v.b));
    v = mix(vec3(y), v, 1.0 + uVibrance * (1.0 - sat) * 1.2);
    v = mix(vec3(y), v, max(0.0, 1.0 + uSaturation + lb.z));

    if (uHslOn > 0.5) {
        v = applyHsl(clamp(v, 0.0, 1.0));
    }

    if (uLutOn > 0.5) {
        vec3 lin = clamp(v, 0.0, 1.0);
        if (uLutConvert > 0.5) lin = toSrgb(clamp(uToSrgb * toLinear(lin), 0.0, 1.0));
        vec3 l = texture(uLut, lin * uLutScale + uLutOffset).rgb;
        if (uLutConvert > 0.5) l = toSrgb(uFromSrgb * toLinear(l));
        v = mix(v, l, uLutAmount);
    }

    if (uVignette != 0.0) {
        vec2 d = (pos - 0.5) * uAspect;
        float r = length(d) / length(0.5 * uAspect);
        float w = smoothstep(0.3, 1.1, r);
        if (uVignette < 0.0) {
            v *= 1.0 + uVignette * w;
        } else {
            v = mix(v, vec3(1.0), uVignette * w * 0.8);
        }
    }

    if (uGrain > 0.0) {
        vec2 q = pos * uAspect / uGrainCell;
        float n = (valueNoise(q) + 0.5 * valueNoise(q * 2.07 + 17.0)) / 1.5 - 0.5;
        // When a grain cell is smaller than a screen pixel the preview averages it out.
        float cellPx = uGrainCell * uLongEdgePx;
        float visible = clamp(cellPx, 0.25, 1.0);
        float m = 1.0 - pow(abs(2.0 * dot(v, LUMA) - 1.0), 2.0);
        v += n * uGrain * 0.3 * m * visible;
    }

    return clamp(v, 0.0, 1.0);
}

vec3 toOutput(vec3 v) {
    vec3 l = clamp(uOutM * toLinear(v), 0.0, 1.0);
    return uOutGamma > 0.0 ? pow(l, vec3(1.0 / uOutGamma)) : toSrgb(l);
}

void main() {
    vec4 src = texture(uGeo, vCoord);
    vec2 pos = uImgMap.xy + vCoord * uImgMap.zw;
    vec3 rgb = pos.x < uCompare ? src.rgb : process(src.rgb, pos);
    if (uShowMask >= 0) rgb = mix(rgb, vec3(1.0, 0.1, 0.1), 0.6 * maskWeight(uShowMask, pos));
    if (uOutConvert > 0.5) rgb = toOutput(rgb);
    if (uComposite > 0.5) {
        oColor = vec4(mix(uBg, rgb, src.a), 1.0);
    } else {
        oColor = vec4(rgb, 1.0);
    }
}
"""
}
