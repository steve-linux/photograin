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

    /** Resamples the source through the geometry transform; vCoord is in output pixels. */
    const val GEOMETRY = """#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform mat3 uM;
in vec2 vCoord;
out vec4 oColor;
void main() {
    vec2 uv = (uM * vec3(vCoord, 1.0)).xy;
    vec2 inside = step(vec2(-0.0005), uv) * step(uv, vec2(1.0005));
    oColor = vec4(texture(uTex, clamp(uv, 0.0, 1.0)).rgb, inside.x * inside.y);
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

// Pixels left of this x (0..1 in the output) show the original. -1 disables comparison.
uniform float uCompare;
// 1.0 composites over uBg (screen); 0.0 writes the color with the geometry alpha (export).
uniform float uComposite;
uniform vec3 uBg;

in vec2 vCoord;
out vec4 oColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

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

vec3 process(vec3 s, vec2 pos) {
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

    float gain = exp2(uExposure);
    c *= uWb * gain;
    bl *= uWb * gain;

    // Shadows and highlights driven by local (blurred) luminance to limit halos.
    float localL = toSrgb(vec3(dot(bl, LUMA))).x;
    float wS = 1.0 - smoothstep(0.0, 0.6, localL);
    float wH = smoothstep(0.4, 1.0, localL);
    c *= exp2(uShadows * 1.5 * wS + uHighlights * 1.5 * wH);

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

    v = contrastCurve(v, uContrast);

    if (uClarity != 0.0) {
        float lv = dot(v, LUMA);
        float lb = dot(bv, LUMA);
        float mid = 1.0 - pow(abs(2.0 * lv - 1.0), 2.0);
        v += uClarity * 1.5 * (lv - lb) * mid;
    }

    float y = dot(v, LUMA);
    float sat = max(v.r, max(v.g, v.b)) - min(v.r, min(v.g, v.b));
    v = mix(vec3(y), v, 1.0 + uVibrance * (1.0 - sat) * 1.2);
    v = mix(vec3(y), v, 1.0 + uSaturation);

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

void main() {
    vec4 src = texture(uGeo, vCoord);
    vec2 pos = uImgMap.xy + vCoord * uImgMap.zw;
    vec3 rgb = pos.x < uCompare ? src.rgb : process(src.rgb, pos);
    if (uComposite > 0.5) {
        oColor = vec4(mix(uBg, rgb, src.a), 1.0);
    } else {
        oColor = vec4(rgb, 1.0);
    }
}
"""
}
