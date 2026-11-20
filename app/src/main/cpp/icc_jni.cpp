// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <lcms2.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <memory>
#include <type_traits>

namespace {
using Context = std::unique_ptr<std::remove_pointer_t<cmsContext>, decltype(&cmsDeleteContext)>;
using Profile = std::unique_ptr<void, decltype(&cmsCloseProfile)>;
using Transform = std::unique_ptr<void, decltype(&cmsDeleteTransform)>;
constexpr int kBlockSize = 1024;
constexpr int kProfileLimit = 16 * 1024 * 1024;
constexpr unsigned kAlarm = 0xff7f;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_io_github_steve_1linux_photograin_data_PrintProof_nativeProof(
    JNIEnv* env, jobject, jbyteArray profileBytes, jintArray argb, jboolean gamut) {
    const auto length = env->GetArrayLength(profileBytes);
    if (length < 128 || length > kProfileLimit) return nullptr;
    Context context(cmsCreateContext(nullptr, nullptr), cmsDeleteContext);
    if (!context) return nullptr;
    auto release = [env, profileBytes](jbyte* bytes) {
        env->ReleaseByteArrayElements(profileBytes, bytes, JNI_ABORT);
    };
    std::unique_ptr<jbyte, decltype(release)> bytes(env->GetByteArrayElements(profileBytes, nullptr), release);
    if (!bytes) return nullptr;
    Profile printer(cmsOpenProfileFromMemTHR(context.get(), bytes.get(), length), cmsCloseProfile);
    if (!printer || cmsGetDeviceClass(printer.get()) != cmsSigOutputClass) return nullptr;
    const auto printerSpace = cmsGetColorSpace(printer.get());
    if (printerSpace != cmsSigRgbData && printerSpace != cmsSigCmykData && printerSpace != cmsSigGrayData) {
        return nullptr;
    }
    Profile srgb(cmsCreate_sRGBProfileTHR(context.get()), cmsCloseProfile);
    if (!srgb) return nullptr;

    std::array<cmsUInt16Number, cmsMAXCHANNELS> alarm{};
    alarm[0] = kAlarm;
    alarm[1] = 0x0001;
    alarm[2] = kAlarm;
    cmsSetAlarmCodesTHR(context.get(), alarm.data());

    cmsUInt32Number flags = cmsFLAGS_SOFTPROOFING | cmsFLAGS_BLACKPOINTCOMPENSATION;
    if (gamut) flags |= cmsFLAGS_GAMUTCHECK;
    Transform transform(cmsCreateProofingTransformTHR(
        context.get(), srgb.get(), TYPE_RGB_8, srgb.get(), TYPE_RGB_16, printer.get(),
        INTENT_RELATIVE_COLORIMETRIC, INTENT_RELATIVE_COLORIMETRIC, flags), cmsDeleteTransform);
    if (!transform) return nullptr;

    const auto count = env->GetArrayLength(argb);
    auto output = env->NewIntArray(count);
    if (!output) return nullptr;
    std::array<jint, kBlockSize> pixels{};
    std::array<cmsUInt8Number, kBlockSize * 3> input{};
    std::array<cmsUInt16Number, kBlockSize * 3> proof{};
    for (jsize offset = 0; offset < count; offset += kBlockSize) {
        const auto size = std::min(count - offset, kBlockSize);
        env->GetIntArrayRegion(argb, offset, size, pixels.data());
        if (env->ExceptionCheck()) return nullptr;
        for (int i = 0; i < size; ++i) {
            const auto pixel = static_cast<uint32_t>(pixels[i]);
            input[3 * i] = (pixel >> 16) & 255;
            input[3 * i + 1] = (pixel >> 8) & 255;
            input[3 * i + 2] = pixel & 255;
        }
        cmsDoTransform(transform.get(), input.data(), proof.data(), size);
        for (int i = 0; i < size; ++i) {
            const auto* rgb = proof.data() + 3 * i;
            unsigned color;
            if (gamut && rgb[0] == alarm[0] && rgb[1] == alarm[1] && rgb[2] == alarm[2]) {
                color = 0xff00ff;
            } else {
                color = ((rgb[0] + 128) / 257u << 16) |
                        ((rgb[1] + 128) / 257u << 8) | ((rgb[2] + 128) / 257u);
            }
            pixels[i] = static_cast<jint>((static_cast<uint32_t>(pixels[i]) & 0xff000000) | color);
        }
        env->SetIntArrayRegion(output, offset, size, pixels.data());
        if (env->ExceptionCheck()) return nullptr;
    }
    return output;
}
