// SPDX-License-Identifier: GPL-3.0-or-later
// JNI glue between image_codec and io.github.steve_linux.photograin.data.ImageCodecs.
#include <android/bitmap.h>
#include <jni.h>

#include <string>
#include <vector>

#include "image_codec.h"

using namespace photograin;

namespace {

void throwIo(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

std::vector<uint8_t> bytes(JNIEnv* env, jbyteArray array) {
    std::vector<uint8_t> out;
    if (array == nullptr) return out;
    out.resize(env->GetArrayLength(array));
    env->GetByteArrayRegion(array, 0, static_cast<jsize>(out.size()), reinterpret_cast<jbyte*>(out.data()));
    return out;
}

/** Locks an RGBA_8888 [bitmap]; throws and returns null on failure. */
uint8_t* lock(JNIEnv* env, jobject bitmap, AndroidBitmapInfo* info) {
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info->format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        throwIo(env, "Expected an ARGB_8888 bitmap");
        return nullptr;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwIo(env, "Cannot lock bitmap pixels");
        return nullptr;
    }
    return static_cast<uint8_t*>(pixels);
}

}  // namespace

extern "C" {

/** [format]: 0 = JPEG XL, 1 = AVIF. Returns the encoded file. */
JNIEXPORT jbyteArray JNICALL
Java_io_github_steve_1linux_photograin_data_ImageCodecs_nativeEncode(
        JNIEnv* env, jobject, jint format, jobject bitmap, jint quality, jint profile, jbyteArray exif,
        jbyteArray icc) {
    EncodeOptions options;
    options.quality = quality;
    options.profile = static_cast<Profile>(profile);
    options.exif = bytes(env, exif);
    options.icc = bytes(env, icc);

    AndroidBitmapInfo info;
    uint8_t* pixels = lock(env, bitmap, &info);
    if (pixels == nullptr) return nullptr;
    Pixels in{pixels, static_cast<int>(info.width), static_cast<int>(info.height), info.stride};
    std::vector<uint8_t> out;
    std::string error;
    bool ok = format == 0 ? encodeJxl(in, options, &out, &error) : encodeAvif(in, options, &out, &error);
    AndroidBitmap_unlockPixels(env, bitmap);
    if (!ok) {
        throwIo(env, error);
        return nullptr;
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte*>(out.data()));
    }
    return result;
}

/** Returns {width, height, profile}. */
JNIEXPORT jintArray JNICALL
Java_io_github_steve_1linux_photograin_data_ImageCodecs_nativeProbeJxl(JNIEnv* env, jobject, jbyteArray file) {
    std::vector<uint8_t> data = bytes(env, file);
    JxlInfo info;
    std::string error;
    if (!probeJxl(data.data(), data.size(), &info, &error)) {
        throwIo(env, error);
        return nullptr;
    }
    jint values[] = {info.width, info.height, static_cast<jint>(info.profile)};
    jintArray result = env->NewIntArray(3);
    if (result != nullptr) env->SetIntArrayRegion(result, 0, 3, values);
    return result;
}

JNIEXPORT void JNICALL
Java_io_github_steve_1linux_photograin_data_ImageCodecs_nativeDecodeJxl(
        JNIEnv* env, jobject, jbyteArray file, jint profile, jobject bitmap) {
    std::vector<uint8_t> data = bytes(env, file);
    AndroidBitmapInfo info;
    uint8_t* pixels = lock(env, bitmap, &info);
    if (pixels == nullptr) return;
    std::string error;
    bool ok = decodeJxl(data.data(), data.size(), static_cast<Profile>(profile), pixels, info.stride,
                        static_cast<size_t>(info.stride) * info.height, &error);
    AndroidBitmap_unlockPixels(env, bitmap);
    if (!ok) throwIo(env, error);
}

}  // extern "C"
