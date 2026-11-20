# Automatic sky mask (experimental)

In Masks → Selection, choose **Sky AI (beta)**. A small U²-Net-p network trained specifically
for sky segmentation runs offline on the CPU. Its weights are bundled in the APK. The
initial exposure is −0.30 EV so the effect is visible. Use the eye button to inspect the red
overlay, then Paint/Erase or the lasso to refine it. Invert targets the complement; color and
luminance ranges further limit it. Light, Color and Effects use the resulting raster.

## Scope and limitations

This is the MIT-licensed public **small/demo** model from xiongzhu666, not the author's
unpublished high-precision model and not a claim of Lightroom-equivalent quality. The
upstream author explicitly notes errors on building details and textured clouds.

An informal PC check on one existing daylight architecture photo selected the sky while
largely excluding the stone facade, trees and reflected scene in the pond. On 22 existing
non-sky control thumbnails, 16 produced empty selections and 6 still produced false positives
(on signs, screens, one night scene and a label). This is not an annotated accuracy benchmark.
The model is deliberately labelled beta: inspect the overlay and correct mistakes manually.

The rejected color-only prototype is not shipped. No network permission, download at runtime,
new inference engine, or change to IS-Net weights is introduced.

## Coordinates and export

The input is the original preview averaged into a 384×384 square and transformed to upright
EXIF orientation, using the same input helper as subject selection. RGB channels use the
upstream ImageNet mean and standard deviation. ncnn reads the original fp16 weights while
CPU inference uses fp32 activations.

Sky inference preserves absolute sigmoid probabilities. It does **not** min/max-stretch each
image: that would turn near-zero noise in no-sky photos into a false full-strength selection.
If fewer than 0.2% of model pixels exceed 0.65, the mask is empty. Otherwise values below 0.35
are excluded, values above 0.65 are fully selected, with a smooth transition between them.
These are confidence gates, not a guarantee of no false positives.

Coverage is mapped back into stored-source coordinates and resized to the standard brush
grid. Crop, perspective, editor rotation, brush refinements, inversion and ranges therefore
share the existing preview/export path. The editor reuses the cached selection for export;
reopening and batch export recompute it for each original photo. Recipes store `SKY` and
source-relative brush strokes rather than an image-size-dependent bitmap. Subject/object
cache updates retain the sky cache and vice versa.

## Reproduction and verification

Model provenance, revision, hashes and license are in [MODELS.md](MODELS.md). Run
`python3 tools/sky/fetch.py` to regenerate the bundled model files from pinned upstream data.

JVM coverage checks confidence gating, soft masks, all eight EXIF orientations on rectangular
output, enclosed sky regions, brush refinement/reset, recipe compatibility, raster shader
selection and coexistence with subject/object caches. These tests exercise integration and
postprocessing, not learned segmentation accuracy.

`tools/sky/native_probe.cpp` can be built on the host with `segmenter.cpp` and the pinned ncnn
submodule to test the same portable inference code as Android. It accepts 384×384 upright
RGBA bytes and writes the raw 384×384 probability bytes. No phone is needed.
