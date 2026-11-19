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

    private const val LENS_WARP = """
uniform float uLensAmount;
uniform vec2 uLensSize;

vec2 lensPosition(vec2 normalized) {
    if (uLensAmount == 0.0) return normalized;
    vec2 centered = normalized - 0.5;
    vec2 aspect = uLensSize / max(uLensSize.x, uLensSize.y);
    vec2 radial = centered * aspect;
    float k = 0.40 * uLensAmount;
    float zoom = 1.0 + max(k, 0.0) * dot(aspect, aspect) * 0.25;
    return 0.5 + centered * ((1.0 + k * dot(radial, radial)) / zoom);
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

$LENS_WARP

void main() {
    vec2 pixel = uLensAmount == 0.0 ? vCoord : lensPosition(vCoord / uLensSize) * uLensSize;
    vec3 hm = uM * vec3(pixel, 1.0);
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
// Contrast, highlights, shadows, clarity.
uniform vec4 uBasicTone;
// Whites, blacks, texture, dehaze.
uniform vec4 uToneDetail;
uniform float uVibrance;
uniform float uSaturation;
uniform float uSharpness;
uniform float uOutputSharpen;
uniform float uVignette;
uniform float uVignetteMidpoint;
uniform float uVignetteRoundness;
uniform float uVignetteFeather;
uniform float uVignetteHighlights;
uniform float uGrainRoughness;
uniform float uBloom;
uniform float uHalation;
uniform float uMidtoneGamma;
uniform float uShadowContrast;
uniform float uMidtoneContrast;
uniform float uHighlightContrast;
uniform float uShadowTone;
uniform float uMidtoneTone;
uniform float uHighlightTone;
uniform float uGlobalHue;
uniform float uRedBlueSwap;
uniform float uSoftFocus;
uniform float uMatte;
uniform float uSepia;
uniform float uCyanotype;
uniform float uNegative;
uniform float uSolarize;
uniform float uPosterize;
uniform float uThreshold;
uniform float uBleachBypass;
uniform float uCrossProcess;
uniform float uOrton;
uniform float uBrightness;
uniform float uShadowFloor;
uniform float uWhiteCeiling;
uniform float uShadowSoftClip;
uniform float uHighlightSoftClip;
uniform float uLumaContrast;
uniform float uShadowExposure;
uniform float uHighlightExposure;
uniform vec3 uRgbGamma;
uniform vec3 uRgbGain;
uniform vec3 uRgbOffset;
uniform float uShadowSaturation;
uniform float uMidtoneSaturation;
uniform float uHighlightSaturation;
uniform float uShadowTint;
uniform float uMidtoneTint;
uniform float uHighlightTint;
uniform float uCycleRgb;
uniform float uMonochromeRed;
uniform float uMonochromeGreen;
uniform float uMonochromeBlue;
uniform float uLumaSharpen;
uniform float uChromaSharpen;
uniform float uEdgeDetect;
uniform float uEmboss;
uniform float uPencilSketch;
uniform float uHighPass;
uniform float uChannelInvertRed;
uniform float uChannelInvertGreen;
uniform float uChannelInvertBlue;
uniform float uLumaInvert;
uniform float uThresholdColor;
uniform float uPosterizeLuma;
uniform float uThermalMap;
uniform float uPurpleGold;
uniform float uTealOrange;
uniform float uInfraredLook;
uniform float uFilmToe;
uniform float uFilmShoulder;
uniform float uLightLeak;
uniform float uTiltShift;
uniform float uRadialBlur;
uniform float uScanlines;
uniform float uDefringe;
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
// Creative 3D LUT (.cube), sampled at texel centers.
uniform highp sampler3D uLut;
uniform float uLutOn;
uniform int uGradingOn;
uniform vec4 uGrading[4];
uniform vec2 uGradeMix;
uniform float uGradientAmount;
uniform vec4 uFilmBase;
uniform float uFilmExposure;
uniform float uFilmBlack;
uniform vec4 uColorReference;
uniform vec4 uColorKey;
uniform float uColorKeyTolerance;
uniform float uGradientMidpoint;
uniform vec3 uGradientShadow;
uniform vec3 uGradientMiddle;
uniform vec3 uGradientHighlight;
// Amount, coordinate scale and offset share one uniform vector.
uniform vec3 uLutMix;
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
// Type (0 linear, 1 radial, 2 brush, 3 whole photo, 4 depth), angle or raster layer, feather, invert.
uniform vec4 uMaskOpt[8];
// Range: type (0 none, 1 luminance, 2 color), then low, high, smooth or hue, hue tolerance,
// saturation (negative: any).
uniform vec4 uMaskRange[8];
// Painted brush masks over the whole source, one layer per mask (see BrushRaster).
uniform mediump sampler2DArray uBrush;
// Exposure, contrast, highlights, shadows.
uniform vec4 uLocA[8];
// Temperature, tint, saturation, clarity.
uniform vec4 uLocB[8];
// Blur, pixelation, then mask reference and flags or packed components (see ShaderParams).
uniform vec4 uLocEffects[8];
// Whites, blacks, texture and dehaze.
uniform vec4 uLocToneDetail[8];
// Hue in turns, sharpness, luminance noise reduction, purple/green fringe correction.
uniform vec4 uLocDetail[8];
// Index of the mask to tint red on screen, or -1.
uniform int uShowMask;
uniform int uMaskOverlayMode;
uniform vec4 uMaskOverlayColor;
uniform float uMaskOverlayOpacity;
// Index of the mask that cuts the photo out, or -1. Outside it the photo becomes uCutoutFill.rgb
// (working space, sRGB curve) when uCutoutFill.a is 1, or transparent when it is 0.
uniform int uCutout;
uniform vec4 uCutoutFill;

// Pixels left of this x (0..1 in the output) show the original. -1 disables comparison.
uniform float uCompare;
// 1.0 composites over uBg (screen); 0.0 writes the color with the geometry alpha (export).
uniform float uComposite;
uniform vec3 uBg;
uniform int uExposureWarning;
uniform int uHistogramSample;
uniform int uFocusPeaking;
uniform int uProof;
uniform int uGamutWarning;
uniform mat3 uProofM;
uniform mat3 uProofToScreen;

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
vec3 denoise(vec3 s, float lumaAmount) {
    float ls = dot(s, LUMA);
    float sigmaR = 0.02 + 0.12 * lumaAmount;
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
    vec3 o = mix(s, accL / wL, lumaAmount);
    float lo = dot(o, LUMA);
    return clamp(lo + mix(o - lo, accC / wC, uChromaNr), 0.0, 1.0);
}

// Same as ShaderParams.whiteBalanceGains.
vec3 wbGains(float temp, float tint) {
    vec3 g = vec3(1.0 + 0.35 * temp, 1.0 - 0.25 * tint, 1.0 - 0.35 * temp);
    return g / dot(g, vec3(0.2126, 0.7152, 0.0722));
}

// Same as MaskRange.weight, on the pixel before any adjustment.
float rangeWeight(vec4 r, vec3 s) {
    if (r.x < 0.5) return 1.0;
    if (r.x < 1.5) {
        float l = dot(s, LUMA);
        float e = max(r.w, 0.001) * 0.25;
        return smoothstep(r.y - e, r.y + e, l) * (1.0 - smoothstep(r.z - e, r.z + e, l));
    }
    float mx = max(s.r, max(s.g, s.b));
    float mn = min(s.r, min(s.g, s.b));
    float c = mx - mn;
    float sat = mx > 0.0 ? c / mx : 0.0;
    float h = 0.0;
    if (c > 0.0) {
        if (mx == s.r) h = mod((s.g - s.b) / c, 6.0);
        else if (mx == s.g) h = (s.b - s.r) / c + 2.0;
        else h = (s.r - s.g) / c + 4.0;
        h /= 6.0;
    }
    float d = abs(h - r.y);
    d = min(d, 1.0 - d);
    float satMatch = 1.0;
    if (r.w >= 0.0) {
        // Same as MaskRange.saturationTolerance, from the hue tolerance.
        float ts = 0.05 + 0.6 * (r.z - 0.01) / 0.15;
        satMatch = 1.0 - smoothstep(ts, ts + 0.1, abs(sat - r.w));
    }
    return (1.0 - smoothstep(r.z, r.z + 0.04, d)) * smoothstep(0.06, 0.18, sat) * satMatch;
}

$LENS_WARP

// Same as Mask.weight, narrowed by the mask's range on the pixel [s].
float baseMaskWeight(int i, vec2 pos, vec3 s) {
    vec3 hp = uPosToSrc * vec3(lensPosition(pos), 1.0);
    vec2 q = hp.xy / hp.z * uSrcAspect;
    vec4 g = uMaskGeo[i];
    vec4 o = uMaskOpt[i];
    float w;
    if (o.x > 3.5) {
        if (g.w < 0.5) return 0.0;
        vec2 src = clamp(q / uSrcAspect, 0.0, 1.0);
        float depth = textureLod(uBrush, vec3(src, o.y), 0.0).r;
        w = smoothstep(g.y, g.y + g.z, abs(depth - g.x));
    } else if (o.x > 2.5) {
        w = 1.0;
        if (o.z < 0.0) {
            vec3 hsv = rgbToHsv(s);
            float blue = smoothstep(0.47, 0.54, hsv.x) * (1.0 - smoothstep(0.68, 0.75, hsv.x));
            float coloredSky = blue * smoothstep(0.09, 0.32, hsv.y) * smoothstep(0.25, 0.72, hsv.z);
            float cloud = (1.0 - smoothstep(0.12, 0.30, hsv.y)) * smoothstep(0.68, 0.94, hsv.z);
            w = max(coloredSky, cloud * 0.8) * (1.0 - smoothstep(0.43, 0.82, pos.y));
        }
    } else if (o.x > 1.5) {
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
    w = o.w > 0.5 ? 1.0 - w : w;
    return w * rangeWeight(uMaskRange[i], s);
}

float combineMaskWeight(float weight, float selection, int operation) {
    if (operation == 1) return weight * (1.0 - selection);
    if (operation == 2) return weight * selection;
    return max(weight, selection);
}

float maskWeight(int i, vec2 pos, vec3 s) {
    float weight = baseMaskWeight(i, pos, s);
    highp int references = int(uLocEffects[i].z);
    highp int flags = int(uLocEffects[i].w);
    if ((flags & 16) != 0) {
        for (int component = 0; component < 7; component++) {
            int code = component < 4 ? (references >> (5 * component)) & 31
                                     : (flags >> (5 * (component - 3))) & 31;
            if (code == 0) break;
            int other = (code - 1) % 8;
            int operation = (code - 1) / 8;
            weight = combineMaskWeight(weight, baseMaskWeight(other, pos, s), operation);
        }
    } else if (references >= 0 && references < uMaskCount && references != i) {
        weight = combineMaskWeight(weight, baseMaskWeight(references, pos, s), flags & 3);
    }
    return (flags & 4) != 0 ? 1.0 - weight : weight;
}

vec3 process(vec3 s, vec2 pos) {
    vec4 la = vec4(0.0);
    vec4 lb = vec4(0.0);
    vec2 localEffects = vec2(0.0);
    vec4 localToneDetail = vec4(0.0);
    vec4 localDetail = vec4(0.0);
    for (int i = 0; i < 8; i++) {
        if (i >= uMaskCount) break;
        highp int flags = int(uLocEffects[i].w);
        if ((flags & 8) != 0) continue;
        float w = maskWeight(i, pos, s);
        la += w * uLocA[i];
        lb += w * uLocB[i];
        localEffects += w * uLocEffects[i].xy;
        localToneDetail += w * uLocToneDetail[i];
        localDetail += w * uLocDetail[i];
    }
    float clarity = uBasicTone.w + lb.w;
    float textureAmount = clamp(uToneDetail.z + localToneDetail.z, -1.0, 1.0);
    float dehaze = clamp(uToneDetail.w + localToneDetail.w, -1.0, 1.0);

    float defringe = clamp(uDefringe + localDetail.w, 0.0, 1.0);
    if (defringe > 0.0) {
        vec2 dx = vec2(uGeoTexel.x, 0.0);
        vec2 dy = vec2(0.0, uGeoTexel.y);
        float gx = dot(texture(uGeo, vCoord + dx).rgb - texture(uGeo, vCoord - dx).rgb, LUMA);
        float gy = dot(texture(uGeo, vCoord + dy).rgb - texture(uGeo, vCoord - dy).rgb, LUMA);
        vec3 hsv = rgbToHsv(s);
        float purple = smoothstep(0.65, 0.73, hsv.x) * (1.0 - smoothstep(0.86, 0.94, hsv.x));
        float green = smoothstep(0.18, 0.26, hsv.x) * (1.0 - smoothstep(0.39, 0.47, hsv.x));
        float strength = defringe * max(purple, green) * smoothstep(0.03, 0.25, length(vec2(gx, gy)));
        s = mix(s, vec3(dot(s, LUMA)), strength);
    }
    float lumaNoiseReduction = clamp(uLumaNr + localDetail.z, 0.0, 1.0);
    if (lumaNoiseReduction > 0.0 || uChromaNr > 0.0) {
        s = denoise(s, lumaNoiseReduction);
    }

    // Unsharp mask on encoded values. Negative amounts blend towards the four neighbours.
    float sharpness = clamp(uSharpness + localDetail.y, -1.0, 1.0);
    if (sharpness != 0.0) {
        vec3 n = texture(uGeo, vCoord + vec2(uGeoTexel.x, 0.0)).rgb
               + texture(uGeo, vCoord - vec2(uGeoTexel.x, 0.0)).rgb
               + texture(uGeo, vCoord + vec2(0.0, uGeoTexel.y)).rgb
               + texture(uGeo, vCoord - vec2(0.0, uGeoTexel.y)).rgb;
        vec3 neighbors = 0.25 * n;
        s = sharpness > 0.0 ? clamp(s + (s - neighbors) * sharpness * 1.5, 0.0, 1.0)
                            : mix(s, neighbors, -sharpness);
    }

    if (localEffects.x > 0.0) {
        s = mix(s, texture(uBlur, vCoord).rgb, clamp(localEffects.x, 0.0, 1.0));
    }
    if (localEffects.y > 0.0) {
        // Grid is anchored in the full output, so neighboring export tiles use the same cells.
        float block = 0.009;
        vec2 scaled = pos * uAspect;
        vec2 center = (floor(scaled / block) + 0.5) * block / uAspect;
        center = clamp(center, 0.0, 1.0);
        vec2 tileUv = vCoord + (center - pos) / max(uImgMap.zw, vec2(1e-6));
        s = mix(s, texture(uGeo, tileUv).rgb, clamp(localEffects.y, 0.0, 1.0));
    }
    vec3 c = toLinear(s);
    vec3 bl = toLinear(texture(uBlur, vCoord).rgb);

    if (uFilmBase.a > 0.5) {
        vec3 base = max(toLinear(uFilmBase.rgb), vec3(0.002));
        c = max(vec3(0.0), -log(clamp(c / base, 0.015, 1.0)) * 0.4 - uFilmBlack) * exp2(uFilmExposure);
        bl = max(vec3(0.0), -log(clamp(bl / base, 0.015, 1.0)) * 0.4 - uFilmBlack) * exp2(uFilmExposure);
    }
    if (uColorReference.a > 0.5) {
        vec3 referenceSample = max(toLinear(uColorReference.rgb), vec3(0.004));
        vec3 correction = clamp(vec3(0.18) / referenceSample, 0.25, 4.0);
        c *= correction;
        bl *= correction;
    }

    float gain = exp2(uExposure + la.x);
    vec3 wb = uMaskCount > 0 ? uWb * wbGains(lb.x, lb.y) : uWb;
    c *= wb * gain;
    bl *= wb * gain;

    // Shadows and highlights driven by local (blurred) luminance to limit halos.
    float localL = toSrgb(vec3(dot(bl, LUMA))).x;
    float wS = 1.0 - smoothstep(0.0, 0.6, localL);
    float wH = smoothstep(0.4, 1.0, localL);
    c *= exp2((uBasicTone.z + la.w) * 1.5 * wS + (uBasicTone.y + la.z) * 1.5 * wH);

    if (uBloom > 0.0 || uHalation > 0.0) {
        // Reuse the padded blur so diffusion follows the same scale in preview and tiled export.
        float surroundingLight = dot(bl, LUMA);
        float bright = smoothstep(0.30, 0.90, surroundingLight);
        c += uBloom * 0.30 * bright * clamp(bl, 0.0, 1.0);
        float spill = clamp(surroundingLight - dot(toLinear(s) * wb * gain, LUMA), 0.0, 1.0);
        c += uHalation * bright * spill * vec3(0.60, 0.16, 0.035);
    }

    vec3 v = toSrgb(c);
    vec3 bv = toSrgb(bl);

    if (dehaze > 0.0) {
        float haze = dehaze * 0.85 * min(min(bv.r, bv.g), bv.b);
        v = (v - haze) / max(1.0 - haze, 0.05);
    } else if (dehaze < 0.0) {
        v = mix(v, vec3(0.85), -dehaze * 0.5);
    }

    float bp = -clamp(uToneDetail.y + localToneDetail.y, -1.0, 1.0) * 0.15;
    float wp = 1.0 - clamp(uToneDetail.x + localToneDetail.x, -1.0, 1.0) * 0.2;
    v = clamp((v - bp) / (wp - bp), 0.0, 1.0);

    v = contrastCurve(v, uBasicTone.x + la.y);

    if (uMidtoneGamma != 0.0) {
        v = pow(clamp(v, 0.0, 1.0), vec3(exp2(-uMidtoneGamma)));
    }
    if (uShadowContrast != 0.0 || uMidtoneContrast != 0.0 || uHighlightContrast != 0.0) {
        float tone = clamp(dot(v, LUMA), 0.0, 1.0);
        float shadows = 1.0 - smoothstep(0.15, 0.5, tone);
        float highlights = smoothstep(0.5, 0.85, tone);
        float midtones = 1.0 - shadows - highlights;
        float shift = shadows * uShadowContrast * (tone - 0.2)
                    + midtones * uMidtoneContrast * (tone - 0.5)
                    + highlights * uHighlightContrast * (tone - 0.8);
        v = clamp(v + 1.6 * shift * tone * (1.0 - tone), 0.0, 1.0);
    }

    if (clarity != 0.0) {
        float lv = dot(v, LUMA);
        float lbl = dot(bv, LUMA);
        float mid = 1.0 - pow(abs(2.0 * lv - 1.0), 2.0);
        v += clarity * 1.5 * (lv - lbl) * mid;
    }

    if (uSoftFocus > 0.0) {
        // Blur detail in the same exposure and white balance as the source.
        v += 0.75 * uSoftFocus * (bv - toSrgb(toLinear(s) * wb * gain));
    }

    if (uBrightness != 0.0) {
        v += 0.25 * uBrightness;
    }
    if (uShadowFloor != 0.0) {
        v = mix(v, 0.3 + 0.7 * v, uShadowFloor);
    }
    if (uWhiteCeiling != 0.0) {
        v *= 1.0 - 0.35 * uWhiteCeiling;
    }
    if (uShadowSoftClip != 0.0) {
        v += uShadowSoftClip * 0.12 * exp(-8.0 * clamp(v, 0.0, 1.0));
    }
    if (uHighlightSoftClip != 0.0) {
        v -= uHighlightSoftClip * 0.12 * exp(-8.0 * (1.0 - clamp(v, 0.0, 1.0)));
    }
    if (uLumaContrast != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += vec3((contrastCurve(vec3(y), uLumaContrast).r - y));
    }
    if (uShadowExposure != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v *= exp2(uShadowExposure * (1.0 - smoothstep(0.02, 0.25, y)));
    }
    if (uHighlightExposure != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v *= exp2(0.5 * uHighlightExposure * smoothstep(0.7, 1.0, y));
    }

    vec3 neighbors = s;
    vec2 gradient = vec2(0.0);
    if (uLumaSharpen != 0.0 || uChromaSharpen != 0.0 || textureAmount != 0.0 ||
        uEdgeDetect != 0.0 || uEmboss != 0.0 || uPencilSketch != 0.0) {
        vec2 dx = vec2(uGeoTexel.x, 0.0);
        vec2 dy = vec2(0.0, uGeoTexel.y);
        vec3 left = texture(uGeo, vCoord - dx).rgb;
        vec3 right = texture(uGeo, vCoord + dx).rgb;
        vec3 top = texture(uGeo, vCoord - dy).rgb;
        vec3 bottom = texture(uGeo, vCoord + dy).rgb;
        neighbors = 0.25 * (left + right + top + bottom);
        gradient = vec2(dot(right - left, LUMA), dot(bottom - top, LUMA));
    }
    if (uLumaSharpen != 0.0) {
        v += vec3(uLumaSharpen * 1.4 * dot(s - neighbors, LUMA));
    }
    if (uChromaSharpen != 0.0) {
        vec3 delta = s - neighbors;
        v += uChromaSharpen * (delta - dot(delta, LUMA));
    }
    if (textureAmount != 0.0) {
        v += textureAmount * 0.8 * (s - neighbors) * (1.0 - smoothstep(0.04, 0.17, length(s - neighbors)));
    }
    if (uEdgeDetect != 0.0) {
        v = mix(v, vec3(clamp(length(gradient) * 3.0, 0.0, 1.0)), uEdgeDetect);
    }
    if (uEmboss != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v = mix(v, vec3(0.5 + 1.7 * (gradient.x + gradient.y)), uEmboss);
    }
    if (uPencilSketch != 0.0) {
        v = mix(v, vec3(1.0 - smoothstep(0.015, 0.35, length(gradient))), uPencilSketch);
    }
    if (uHighPass != 0.0) {
        v = mix(v, 0.5 + 1.5 * (toSrgb(toLinear(s) * wb * gain) - bv), uHighPass);
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

    if (uGlobalHue != 0.0) {
        vec3 hueColor = rgbToHsv(clamp(v, 0.0, 1.0));
        hueColor.x = fract(hueColor.x + 0.5 * uGlobalHue);
        v = hsvToRgb(hueColor);
    }
    if (localDetail.x != 0.0) {
        // Remove and restore the common negative offset so the rotation also works
        // for HDR values and for colors outside the working gamut.
        float offset = min(0.0, min(v.r, min(v.g, v.b)));
        vec3 hueColor = rgbToHsv(v - vec3(offset));
        hueColor.x = fract(hueColor.x + localDetail.x);
        v = hsvToRgb(hueColor) + vec3(offset);
    }
    if (uRedBlueSwap > 0.0) v = mix(v, v.bgr, uRedBlueSwap);
    if (uShadowTone != 0.0 || uMidtoneTone != 0.0 || uHighlightTone != 0.0) {
        float tone = clamp(dot(v, LUMA), 0.0, 1.0);
        float low = 1.0 - smoothstep(0.15, 0.5, tone);
        float high = smoothstep(0.5, 0.85, tone);
        float warmth = low * uShadowTone + high * uHighlightTone + (1.0 - low - high) * uMidtoneTone;
        v += warmth * vec3(0.15, -0.02, -0.24) * smoothstep(0.0, 0.12, tone);
    }

    if (uRgbGamma.r != 0.0) {
        v.r = pow(clamp(v.r, 0.0, 1.0), exp2(-uRgbGamma.r));
    }
    if (uRgbGamma.g != 0.0) {
        v.g = pow(clamp(v.g, 0.0, 1.0), exp2(-uRgbGamma.g));
    }
    if (uRgbGamma.b != 0.0) {
        v.b = pow(clamp(v.b, 0.0, 1.0), exp2(-uRgbGamma.b));
    }
    if (uRgbGain.r != 0.0) {
        v.r *= exp2(uRgbGain.r);
    }
    if (uRgbGain.g != 0.0) {
        v.g *= exp2(uRgbGain.g);
    }
    if (uRgbGain.b != 0.0) {
        v.b *= exp2(uRgbGain.b);
    }
    if (uRgbOffset.r != 0.0) {
        v.r += 0.25 * uRgbOffset.r;
    }
    if (uRgbOffset.g != 0.0) {
        v.g += 0.25 * uRgbOffset.g;
    }
    if (uRgbOffset.b != 0.0) {
        v.b += 0.25 * uRgbOffset.b;
    }
    if (uShadowSaturation != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += (v - y) * uShadowSaturation * (1.0 - smoothstep(0.12, 0.5, y));
    }
    if (uMidtoneSaturation != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += (v - y) * uMidtoneSaturation * (1.0 - abs(2.0 * y - 1.0));
    }
    if (uHighlightSaturation != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += (v - y) * uHighlightSaturation * smoothstep(0.5, 0.88, y);
    }
    if (uShadowTint != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += uShadowTint * vec3(0.09, -0.05, 0.09) * (1.0 - smoothstep(0.12, 0.5, y));
    }
    if (uMidtoneTint != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += uMidtoneTint * vec3(0.09, -0.05, 0.09) * (1.0 - abs(2.0 * y - 1.0));
    }
    if (uHighlightTint != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += uHighlightTint * vec3(0.09, -0.05, 0.09) * smoothstep(0.5, 0.88, y);
    }
    if (uCycleRgb != 0.0) {
        v = mix(v, v.gbr, uCycleRgb);
    }
    if (uMonochromeRed != 0.0) {
        v = mix(v, vec3(v.r), uMonochromeRed);
    }
    if (uMonochromeGreen != 0.0) {
        v = mix(v, vec3(v.g), uMonochromeGreen);
    }
    if (uMonochromeBlue != 0.0) {
        v = mix(v, vec3(v.b), uMonochromeBlue);
    }

    if (uGradingOn != 0) {
        float tone = clamp(dot(v, LUMA) + uGradeMix.x * 0.25, 0.0, 1.0);
        float span = mix(0.12, 0.48, uGradeMix.y);
        float low = 1.0 - smoothstep(0.35 - span * 0.5, 0.35 + span * 0.5, tone);
        float high = smoothstep(0.65 - span * 0.5, 0.65 + span * 0.5, tone);
        float weights[4] = float[4](low, max(0.0, 1.0 - low - high), high, 1.0);
        for (int i = 0; i < 4; i++) {
            vec3 color = hsvToRgb(vec3(fract(uGrading[i].x), 1.0, 1.0));
            vec3 chroma = color - dot(color, LUMA);
            v += weights[i] * uGrading[i].y * chroma * 0.28;
            v *= exp2(weights[i] * uGrading[i].z);
        }
    }
    if (uGradientAmount > 0.0) {
        float lightness = clamp(dot(v, LUMA), 0.0, 1.0);
        vec3 mapped = lightness < uGradientMidpoint
            ? mix(uGradientShadow, uGradientMiddle, lightness / uGradientMidpoint)
            : mix(uGradientMiddle, uGradientHighlight, (lightness - uGradientMidpoint) / (1.0 - uGradientMidpoint));
        v = mix(v, mapped, uGradientAmount);
    }

    if (uLutOn > 0.5) {
        vec3 lin = clamp(v, 0.0, 1.0);
        if (uLutConvert > 0.5) lin = toSrgb(clamp(uToSrgb * toLinear(lin), 0.0, 1.0));
        vec3 l = texture(uLut, lin * uLutMix.y + uLutMix.z).rgb;
        if (uLutConvert > 0.5) l = toSrgb(uFromSrgb * toLinear(l));
        v = mix(v, l, uLutMix.x);
    }

    if (uOrton > 0.0) {
        vec3 soft = clamp(bv, 0.0, 1.0);
        vec3 screened = 1.0 - (1.0 - v) * (1.0 - soft);
        v = mix(v, mix(screened, screened * soft, 0.35), uOrton * 0.7);
    }
    if (uBleachBypass > 0.0) {
        float tone = clamp(dot(v, LUMA), 0.0, 1.0);
        vec3 reduced = mix(v, vec3(tone), 0.65);
        v = mix(v, contrastCurve(reduced, 0.6), uBleachBypass);
    }
    if (uCrossProcess > 0.0) {
        vec3 color = clamp(v, 0.0, 1.0);
        vec3 crossed = vec3(pow(color.r, 0.78), smoothstep(0.0, 1.0, color.g), 0.16 + 0.72 * color.b);
        v = mix(v, crossed, uCrossProcess);
    }
    if (uMatte > 0.0) v = mix(v, 0.15 + 0.73 * v, uMatte);
    if (uSepia > 0.0) {
        float tone = clamp(dot(v, LUMA), 0.0, 1.0);
        v = mix(v, mix(vec3(0.045, 0.025, 0.014), vec3(1.0, 0.89, 0.69), tone), uSepia);
    }
    if (uCyanotype > 0.0) {
        float tone = clamp(dot(v, LUMA), 0.0, 1.0);
        v = mix(v, mix(vec3(0.015, 0.12, 0.28), vec3(0.92, 0.96, 1.0), tone), uCyanotype);
    }
    if (uNegative > 0.0) v = mix(v, 1.0 - v, uNegative);
    if (uSolarize > 0.0) {
        vec3 color = clamp(v, 0.0, 1.0);
        v = mix(v, 1.0 - abs(2.0 * color - 1.0), uSolarize);
    }
    if (uPosterize > 0.0) {
        float levels = floor(mix(255.0, 2.0, uPosterize));
        v = floor(clamp(v, 0.0, 1.0) * levels + 0.5) / levels;
    }
    if (uThreshold > 0.0) v = vec3(step(uThreshold, dot(v, LUMA)));

    if (uChannelInvertRed != 0.0) {
        v.r = mix(v.r, 1.0 - v.r, uChannelInvertRed);
    }
    if (uChannelInvertGreen != 0.0) {
        v.g = mix(v.g, 1.0 - v.g, uChannelInvertGreen);
    }
    if (uChannelInvertBlue != 0.0) {
        v.b = mix(v.b, 1.0 - v.b, uChannelInvertBlue);
    }
    if (uLumaInvert != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += vec3(uLumaInvert * (1.0 - 2.0 * y));
    }
    if (uThresholdColor != 0.0) {
        v = step(vec3(uThresholdColor), v);
    }
    if (uPosterizeLuma != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        float levels = floor(mix(255.0, 2.0, uPosterizeLuma));
        v += vec3(floor(y * levels + 0.5) / levels - y);
    }
    if (uThermalMap != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        vec3 heat = clamp(1.5 - abs(4.0 * y - vec3(3.0, 2.0, 1.0)), 0.0, 1.0);
        v = mix(v, heat, uThermalMap);
    }
    if (uPurpleGold != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v = mix(v, mix(vec3(0.12, 0.03, 0.24), vec3(1.0, 0.84, 0.35), y), uPurpleGold);
    }
    if (uTealOrange != 0.0) {
        float y = clamp(dot(v, LUMA), 0.0, 1.0);
        v += uTealOrange * mix(vec3(-0.13, 0.035, 0.07), vec3(0.11, 0.015, -0.12), smoothstep(0.2, 0.8, y));
    }
    if (uInfraredLook != 0.0) {
        float infrared = clamp(dot(v, vec3(0.32, 0.95, -0.27)), 0.0, 1.0);
        v = mix(v, vec3(infrared), uInfraredLook);
    }
    if (uFilmToe != 0.0) {
        v = mix(v, v * smoothstep(-0.15, 0.42, v), uFilmToe);
    }
    if (uFilmShoulder != 0.0) {
        v = mix(v, 1.0 - (1.0 - v) * smoothstep(-0.15, 0.42, 1.0 - v), uFilmShoulder);
    }
    if (uLightLeak != 0.0) {
        float leak = exp(-7.0 * length((pos - vec2(0.05, 0.82)) * uAspect));
        v = mix(v, 1.0 - (1.0 - v) * (1.0 - vec3(1.0, 0.26, 0.035) * leak), uLightLeak);
    }
    if (uTiltShift != 0.0) {
        float outsideBand = smoothstep(0.08, 0.42, abs(pos.y - 0.5));
        v += uTiltShift * outsideBand * (bv - toSrgb(toLinear(s) * wb * gain));
    }
    if (uRadialBlur != 0.0) {
        float outsideCenter = smoothstep(0.15, 0.48, length(pos - 0.5));
        v += uRadialBlur * outsideCenter * (bv - toSrgb(toLinear(s) * wb * gain));
    }
    if (uScanlines != 0.0) {
        float stripes = 0.5 + 0.5 * cos(6.2831853 * pos.y * uAspect.y * 240.0);
        float visible = 1.0 - smoothstep(0.4, 1.0, fwidth(pos.y * uAspect.y * 240.0));
        v *= 1.0 - 0.45 * uScanlines * stripes * visible;
    }

    if (uVignette != 0.0) {
        vec2 shape = mix(vec2(1.0), uAspect, uVignetteRoundness);
        float radius = length((pos - 0.5) * shape) / length(0.5 * shape);
        float width = max(uVignetteFeather, 0.02);
        float w = smoothstep(uVignetteMidpoint - width * 0.5, uVignetteMidpoint + width * 0.5, radius);
        if (uVignette < 0.0) {
            float protection = smoothstep(0.60, 1.0, dot(v, LUMA)) * uVignetteHighlights;
            v *= 1.0 + uVignette * w * (1.0 - protection);
        } else {
            v = mix(v, vec3(1.0), uVignette * w * 0.8);
        }
    }

    if (uGrain > 0.0) {
        vec2 q = pos * uAspect / uGrainCell;
        float detail = uGrainRoughness;
        float n = (valueNoise(q) + detail * valueNoise(q * 2.07 + 17.0)) / (1.0 + detail) - 0.5;
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

// Diagnostic mask modes are rendered in display sRGB and bypass the cutout and warnings.
vec3 maskPreview(vec3 image, float weight, vec3 overlay, float opacity, int mode) {
    if (mode == 5) return vec3(weight);
    if (mode == 4) return mix(vec3(1.0), image, weight);
    if (mode == 3) return mix(vec3(0.0), image, weight);
    vec3 mono = vec3(dot(image, LUMA));
    vec3 subject = mode == 1 ? mix(image, overlay, opacity) : image;
    return mix(mono, subject, weight);
}

void main() {
    vec4 src = texture(uGeo, vCoord);
    vec2 pos = uImgMap.xy + vCoord * uImgMap.zw;
    bool original = pos.x < uCompare;
    vec3 rgb = original ? src.rgb : process(src.rgb, pos);
    if (uComposite > 0.5 && uShowMask >= 0 && uMaskOverlayMode > 0) {
        float weight = maskWeight(uShowMask, pos, src.rgb) * src.a;
        vec3 display = uOutConvert > 0.5 ? toOutput(rgb) : rgb;
        vec3 overlay = uMaskOverlayColor.a > 0.5 ? uMaskOverlayColor.rgb : vec3(1.0, 0.1, 0.1);
        if (uOutConvert > 0.5) overlay = toOutput(overlay);
        oColor = vec4(maskPreview(display, weight, overlay, uMaskOverlayOpacity, uMaskOverlayMode), 1.0);
        return;
    }
    // How much of the photo the cutout keeps; the original side of the comparison keeps it all.
    float keep = uCutout >= 0 && !original ? maskWeight(uCutout, pos, src.rgb) : 1.0;
    if (!original && uColorKey.a > 0.5) {
        float distanceToKey = length(src.rgb - uColorKey.rgb);
        keep *= smoothstep(uColorKeyTolerance * 0.65, uColorKeyTolerance, distanceToKey);
    }
    float photoCoverage = keep * src.a;
    if (uCutoutFill.a > 0.5) {
        rgb = mix(uCutoutFill.rgb, rgb, keep);
        keep = 1.0;
    }
    if (uShowMask >= 0) {
        vec3 overlay = uMaskOverlayColor.a > 0.5 ? uMaskOverlayColor.rgb : vec3(1.0, 0.1, 0.1);
        rgb = mix(rgb, overlay, uMaskOverlayOpacity * maskWeight(uShowMask, pos, src.rgb));
    }
    if (uOutputSharpen > 0.0 && !original) {
        vec2 dx = vec2(uGeoTexel.x, 0.0);
        vec2 dy = vec2(0.0, uGeoTexel.y);
        vec3 neighbors = (texture(uGeo, vCoord + dx).rgb + texture(uGeo, vCoord - dx).rgb +
                          texture(uGeo, vCoord + dy).rgb + texture(uGeo, vCoord - dy).rgb) * 0.25;
        rgb = clamp(rgb + vec3(dot(src.rgb - neighbors, LUMA) * uOutputSharpen), 0.0, 1.0);
    }
    if (uProof != 0 && !original) {
        vec3 target = uProofM * toLinear(rgb);
        bool outside = any(lessThan(target, vec3(-0.001))) || any(greaterThan(target, vec3(1.001)));
        rgb = toSrgb(clamp(uProofToScreen * clamp(target, 0.0, 1.0), 0.0, 1.0));
        if (uGamutWarning != 0 && outside && photoCoverage >= 0.999) rgb = vec3(1.0, 0.0, 0.9);
    } else if (uOutConvert > 0.5) rgb = toOutput(rgb);
    if (uComposite > 0.5) {
        if (uFocusPeaking != 0 && photoCoverage >= 0.999 && uShowMask < 0) {
            vec2 dx = vec2(uGeoTexel.x, 0.0);
            vec2 dy = vec2(0.0, uGeoTexel.y);
            float gx = dot(texture(uGeo, vCoord + dx).rgb - texture(uGeo, vCoord - dx).rgb, LUMA);
            float gy = dot(texture(uGeo, vCoord + dy).rgb - texture(uGeo, vCoord - dy).rgb, LUMA);
            rgb = mix(rgb, vec3(1.0, 0.85, 0.04), smoothstep(0.12, 0.35, length(vec2(gx, gy))));
        }
        if (uExposureWarning != 0 && uShowMask < 0 && photoCoverage >= 0.999) {
            // Measure the displayed sRGB image, before compositing it over the preview background.
            float brightness = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
            if (brightness >= 254.0 / 255.0) rgb = vec3(1.0, 0.16, 0.16);
            else if (brightness <= 1.0 / 255.0) rgb = vec3(0.24, 0.48, 1.0);
        }
        // A transparent background shows as a checkerboard on screen.
        vec2 cell = floor(gl_FragCoord.xy / 12.0);
        vec3 bg = keep < 1.0 ? mix(vec3(0.42), vec3(0.58), mod(cell.x + cell.y, 2.0)) : uBg;
        oColor = vec4(mix(uBg, mix(bg, rgb, keep), src.a), 1.0);
    } else {
        // Premultiplied, as Android bitmaps hold their pixels.
        float alpha = uHistogramSample != 0 ? keep * src.a : keep;
        oColor = vec4(rgb * alpha, alpha);
    }
}
"""
}
