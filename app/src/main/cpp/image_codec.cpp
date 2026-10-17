// SPDX-License-Identifier: GPL-3.0-or-later
#include "image_codec.h"

#include <avif/avif.h>
#include <jxl/cms.h>
#include <jxl/decode.h>
#include <jxl/encode.h>
#include <jxl/thread_parallel_runner.h>

#include <algorithm>
#include <cstring>
#include <memory>
#include <thread>

namespace photograin {
namespace {

int threadCount(int requested) {
    if (requested > 0) return requested;
    unsigned n = std::thread::hardware_concurrency();
    return static_cast<int>(std::clamp(n, 1u, 8u));
}

JxlColorEncoding jxlEncoding(Profile profile) {
    JxlColorEncoding ce;
    JxlColorEncodingSetToSRGB(&ce, JXL_FALSE);
    switch (profile) {
        case Profile::kSrgb:
            break;
        case Profile::kDisplayP3:
            ce.primaries = JXL_PRIMARIES_P3;
            break;
        case Profile::kAdobeRgb:
            ce.primaries = JXL_PRIMARIES_CUSTOM;
            ce.primaries_red_xy[0] = 0.64;
            ce.primaries_red_xy[1] = 0.33;
            ce.primaries_green_xy[0] = 0.21;
            ce.primaries_green_xy[1] = 0.71;
            ce.primaries_blue_xy[0] = 0.15;
            ce.primaries_blue_xy[1] = 0.06;
            ce.transfer_function = JXL_TRANSFER_FUNCTION_GAMMA;
            ce.gamma = 256.0 / 563.0;  // encoding exponent of the 563/256 Adobe RGB curve
            break;
    }
    ce.rendering_intent = JXL_RENDERING_INTENT_PERCEPTUAL;
    return ce;
}

bool isSrgb(const JxlColorEncoding& ce) {
    return ce.color_space != JXL_COLOR_SPACE_XYB && ce.white_point == JXL_WHITE_POINT_D65 &&
           ce.primaries == JXL_PRIMARIES_SRGB && ce.transfer_function == JXL_TRANSFER_FUNCTION_SRGB;
}

using JxlEncoderPtr = std::unique_ptr<JxlEncoder, decltype(&JxlEncoderDestroy)>;
using JxlDecoderPtr = std::unique_ptr<JxlDecoder, decltype(&JxlDecoderDestroy)>;
using JxlRunnerPtr = std::unique_ptr<void, decltype(&JxlThreadParallelRunnerDestroy)>;

}  // namespace

bool encodeJxl(const Pixels& in, const EncodeOptions& options, std::vector<uint8_t>* out, std::string* error) {
    JxlEncoderPtr enc(JxlEncoderCreate(nullptr), JxlEncoderDestroy);
    JxlRunnerPtr runner(JxlThreadParallelRunnerCreate(nullptr, threadCount(options.threads)),
                        JxlThreadParallelRunnerDestroy);
    if (!enc || !runner) return *error = "Out of memory", false;
    auto fail = [&](const char* what) {
        *error = std::string(what) + " failed (" + std::to_string(JxlEncoderGetError(enc.get())) + ")";
        return false;
    };
    if (JxlEncoderSetParallelRunner(enc.get(), JxlThreadParallelRunner, runner.get()) != JXL_ENC_SUCCESS) {
        return fail("SetParallelRunner");
    }

    const bool lossless = options.quality >= 100;
    JxlBasicInfo info;
    JxlEncoderInitBasicInfo(&info);
    info.xsize = static_cast<uint32_t>(in.width);
    info.ysize = static_cast<uint32_t>(in.height);
    info.bits_per_sample = 8;
    info.num_color_channels = 3;
    info.num_extra_channels = 0;
    info.alpha_bits = 0;
    // Lossless keeps the RGB samples as they are; lossy converts to XYB internally.
    info.uses_original_profile = lossless ? JXL_TRUE : JXL_FALSE;
    if (JxlEncoderSetBasicInfo(enc.get(), &info) != JXL_ENC_SUCCESS) return fail("SetBasicInfo");
    JxlColorEncoding ce = jxlEncoding(options.profile);
    if (JxlEncoderSetColorEncoding(enc.get(), &ce) != JXL_ENC_SUCCESS) return fail("SetColorEncoding");

    if (!options.exif.empty()) {
        if (JxlEncoderUseBoxes(enc.get()) != JXL_ENC_SUCCESS) return fail("UseBoxes");
        // An Exif box starts with the offset of the TIFF header within the payload.
        std::vector<uint8_t> box(4 + options.exif.size(), 0);
        std::memcpy(box.data() + 4, options.exif.data(), options.exif.size());
        if (JxlEncoderAddBox(enc.get(), "Exif", box.data(), box.size(), JXL_FALSE) != JXL_ENC_SUCCESS) {
            return fail("AddBox");
        }
        JxlEncoderCloseBoxes(enc.get());
    }

    JxlEncoderFrameSettings* settings = JxlEncoderFrameSettingsCreate(enc.get(), nullptr);
    if (lossless) {
        if (JxlEncoderSetFrameLossless(settings, JXL_TRUE) != JXL_ENC_SUCCESS) return fail("SetFrameLossless");
    } else {
        float distance = JxlEncoderDistanceFromQuality(static_cast<float>(options.quality));
        if (JxlEncoderSetFrameDistance(settings, distance) != JXL_ENC_SUCCESS) return fail("SetFrameDistance");
    }
    JxlEncoderFrameSettingsSetOption(settings, JXL_ENC_FRAME_SETTING_EFFORT, 7);

    // Drop the alpha byte: the export is always opaque.
    std::vector<uint8_t> rgb(static_cast<size_t>(in.width) * in.height * 3);
    for (int y = 0; y < in.height; y++) {
        const uint8_t* src = in.data + y * in.stride;
        uint8_t* dst = rgb.data() + static_cast<size_t>(y) * in.width * 3;
        for (int x = 0; x < in.width; x++, src += 4, dst += 3) {
            dst[0] = src[0];
            dst[1] = src[1];
            dst[2] = src[2];
        }
    }
    JxlPixelFormat format = {3, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, 0};
    if (JxlEncoderAddImageFrame(settings, &format, rgb.data(), rgb.size()) != JXL_ENC_SUCCESS) {
        return fail("AddImageFrame");
    }
    JxlEncoderCloseInput(enc.get());
    std::vector<uint8_t>().swap(rgb);

    out->resize(1 << 20);
    uint8_t* next = out->data();
    size_t avail = out->size();
    JxlEncoderStatus status;
    while ((status = JxlEncoderProcessOutput(enc.get(), &next, &avail)) == JXL_ENC_NEED_MORE_OUTPUT) {
        size_t used = next - out->data();
        out->resize(out->size() * 2);
        next = out->data() + used;
        avail = out->size() - used;
    }
    if (status != JXL_ENC_SUCCESS) return fail("ProcessOutput");
    out->resize(next - out->data());
    return true;
}

bool encodeAvif(const Pixels& in, const EncodeOptions& options, std::vector<uint8_t>* out, std::string* error) {
    // Full chroma at high quality; 4:2:0 otherwise, as JPEG does, for much smaller files.
    avifPixelFormat yuv = options.quality >= 90 ? AVIF_PIXEL_FORMAT_YUV444 : AVIF_PIXEL_FORMAT_YUV420;
    std::unique_ptr<avifImage, decltype(&avifImageDestroy)> image(
        avifImageCreate(static_cast<uint32_t>(in.width), static_cast<uint32_t>(in.height), 8, yuv),
        avifImageDestroy);
    if (!image) return *error = "Out of memory", false;
    switch (options.profile) {
        case Profile::kSrgb:
            image->colorPrimaries = AVIF_COLOR_PRIMARIES_BT709;
            image->transferCharacteristics = AVIF_TRANSFER_CHARACTERISTICS_SRGB;
            break;
        case Profile::kDisplayP3:
            image->colorPrimaries = AVIF_COLOR_PRIMARIES_SMPTE432;
            image->transferCharacteristics = AVIF_TRANSFER_CHARACTERISTICS_SRGB;
            break;
        case Profile::kAdobeRgb:
            // No CICP code exists for Adobe RGB, so the ICC profile describes it.
            image->colorPrimaries = AVIF_COLOR_PRIMARIES_UNSPECIFIED;
            image->transferCharacteristics = AVIF_TRANSFER_CHARACTERISTICS_UNSPECIFIED;
            break;
    }
    image->matrixCoefficients = AVIF_MATRIX_COEFFICIENTS_BT601;
    image->yuvRange = AVIF_RANGE_FULL;

    avifResult r;
    if (!options.icc.empty() && options.profile == Profile::kAdobeRgb) {
        r = avifImageSetProfileICC(image.get(), options.icc.data(), options.icc.size());
        if (r != AVIF_RESULT_OK) return *error = avifResultToString(r), false;
    }
    if (!options.exif.empty()) {
        r = avifImageSetMetadataExif(image.get(), options.exif.data(), options.exif.size());
        if (r != AVIF_RESULT_OK) return *error = avifResultToString(r), false;
    }

    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image.get());
    rgb.format = AVIF_RGB_FORMAT_RGBA;
    rgb.depth = 8;
    rgb.ignoreAlpha = AVIF_TRUE;
    rgb.pixels = const_cast<uint8_t*>(in.data);
    rgb.rowBytes = static_cast<uint32_t>(in.stride);
    r = avifImageRGBToYUV(image.get(), &rgb);
    if (r != AVIF_RESULT_OK) return *error = avifResultToString(r), false;

    std::unique_ptr<avifEncoder, decltype(&avifEncoderDestroy)> encoder(avifEncoderCreate(), avifEncoderDestroy);
    if (!encoder) return *error = "Out of memory", false;
    encoder->codecChoice = AVIF_CODEC_CHOICE_AOM;
    encoder->quality = std::clamp(options.quality, 0, 100);
    encoder->qualityAlpha = AVIF_QUALITY_LOSSLESS;
    encoder->speed = 6;
    encoder->maxThreads = threadCount(options.threads);
    avifRWData output = AVIF_DATA_EMPTY;
    r = avifEncoderWrite(encoder.get(), image.get(), &output);
    if (r != AVIF_RESULT_OK) {
        avifRWDataFree(&output);
        *error = std::string(avifResultToString(r)) + ": " + encoder->diag.error;
        return false;
    }
    out->assign(output.data, output.data + output.size);
    avifRWDataFree(&output);
    return true;
}

bool isJxl(const uint8_t* data, size_t size) {
    JxlSignature sig = JxlSignatureCheck(data, size);
    return sig == JXL_SIG_CODESTREAM || sig == JXL_SIG_CONTAINER;
}

namespace {

/**
 * Runs the decoder up to the color encoding, picking the output space, and optionally on to
 * the full image written into [out].
 */
bool runJxl(const uint8_t* data, size_t size, JxlInfo* info, const Profile* target, uint8_t* out,
            size_t stride, size_t outSize, std::string* error) {
    JxlDecoderPtr dec(JxlDecoderCreate(nullptr), JxlDecoderDestroy);
    if (!dec) return *error = "Out of memory", false;
    JxlRunnerPtr runner(nullptr, JxlThreadParallelRunnerDestroy);
    int events = JXL_DEC_BASIC_INFO | JXL_DEC_COLOR_ENCODING;
    if (out != nullptr) {
        runner.reset(JxlThreadParallelRunnerCreate(nullptr, threadCount(0)));
        if (!runner) return *error = "Out of memory", false;
        JxlDecoderSetParallelRunner(dec.get(), JxlThreadParallelRunner, runner.get());
        events |= JXL_DEC_FULL_IMAGE;
    }
    if (JxlDecoderSubscribeEvents(dec.get(), events) != JXL_DEC_SUCCESS) return *error = "SubscribeEvents", false;
    JxlDecoderSetCms(dec.get(), *JxlGetDefaultCms());
    JxlDecoderSetInput(dec.get(), data, size);
    JxlDecoderCloseInput(dec.get());

    JxlPixelFormat format = {4, JXL_TYPE_UINT8, JXL_NATIVE_ENDIAN, stride};
    JxlInfo found;
    for (;;) {
        JxlDecoderStatus status = JxlDecoderProcessInput(dec.get());
        switch (status) {
            case JXL_DEC_BASIC_INFO: {
                JxlBasicInfo basic;
                if (JxlDecoderGetBasicInfo(dec.get(), &basic) != JXL_DEC_SUCCESS) return *error = "BasicInfo", false;
                // The decoder applies the orientation, swapping the sides for the transposing ones.
                bool swap = basic.orientation > JXL_ORIENT_FLIP_VERTICAL;
                found.width = static_cast<int>(swap ? basic.ysize : basic.xsize);
                found.height = static_cast<int>(swap ? basic.xsize : basic.ysize);
                break;
            }
            case JXL_DEC_COLOR_ENCODING: {
                JxlColorEncoding original;
                bool srgb = JxlDecoderGetColorAsEncodedProfile(dec.get(), JXL_COLOR_PROFILE_TARGET_ORIGINAL,
                                                               &original) == JXL_DEC_SUCCESS &&
                            (isSrgb(original) || original.color_space == JXL_COLOR_SPACE_GRAY);
                found.profile = srgb ? Profile::kSrgb : Profile::kDisplayP3;
                if (out == nullptr) {
                    *info = found;
                    return true;
                }
                JxlColorEncoding wanted = jxlEncoding(*target);
                if (JxlDecoderSetOutputColorProfile(dec.get(), &wanted, nullptr, 0) != JXL_DEC_SUCCESS) {
                    return *error = "SetOutputColorProfile", false;
                }
                break;
            }
            case JXL_DEC_NEED_IMAGE_OUT_BUFFER: {
                size_t needed = 0;
                if (JxlDecoderImageOutBufferSize(dec.get(), &format, &needed) != JXL_DEC_SUCCESS || needed > outSize) {
                    return *error = "Output buffer too small", false;
                }
                if (JxlDecoderSetImageOutBuffer(dec.get(), &format, out, outSize) != JXL_DEC_SUCCESS) {
                    return *error = "SetImageOutBuffer", false;
                }
                break;
            }
            case JXL_DEC_FULL_IMAGE:
                break;  // Only the first frame of an animation is used.
            case JXL_DEC_SUCCESS:
                if (info != nullptr) *info = found;
                return true;
            case JXL_DEC_NEED_MORE_INPUT:
                return *error = "Truncated file", false;
            default:
                return *error = "Decoding failed", false;
        }
        if (status == JXL_DEC_FULL_IMAGE) {
            if (info != nullptr) *info = found;
            return true;
        }
    }
}

}  // namespace

bool probeJxl(const uint8_t* data, size_t size, JxlInfo* info, std::string* error) {
    return runJxl(data, size, info, nullptr, nullptr, 0, 0, error);
}

bool decodeJxl(const uint8_t* data, size_t size, Profile profile, uint8_t* out, size_t stride, size_t outSize,
               std::string* error) {
    return runJxl(data, size, nullptr, &profile, out, stride, outSize, error);
}

}  // namespace photograin
