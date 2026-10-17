// SPDX-License-Identifier: GPL-3.0-or-later
// JNI glue between RawDecoder and io.github.steve_linux.photograin.data.RawImage.
#include <android/bitmap.h>
#include <jni.h>

#include <vector>

#include "raw_decoder.h"

using photograin::RawDecoder;
using photograin::RawInfo;

namespace {

void throwIo(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

/** Opens [path] into [decoder]; throws and returns false on failure. */
bool open(JNIEnv* env, RawDecoder* decoder, jstring path) {
    const char* p = env->GetStringUTFChars(path, nullptr);
    if (p == nullptr) return false;
    int ret = decoder->openFile(p);
    env->ReleaseStringUTFChars(path, p);
    if (ret != 0) {
        throwIo(env, RawDecoder::errorMessage(ret));
        return false;
    }
    return true;
}

}  // namespace

extern "C" {

JNIEXPORT jintArray JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeProbe(JNIEnv* env, jobject, jstring path) {
    RawDecoder decoder;
    if (!open(env, &decoder, path)) return nullptr;
    RawInfo info;
    int ret = decoder.probe(&info);
    if (ret != 0) {
        throwIo(env, RawDecoder::errorMessage(ret));
        return nullptr;
    }
    jint values[] = {info.width, info.height, info.flip};
    jintArray result = env->NewIntArray(3);
    if (result != nullptr) env->SetIntArrayRegion(result, 0, 3, values);
    return result;
}

JNIEXPORT jlong JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeDevelop(
        JNIEnv* env, jobject, jstring path, jboolean halfSize) {
    auto* decoder = new RawDecoder();
    if (!open(env, decoder, path)) {
        delete decoder;
        return 0;
    }
    int ret = decoder->develop(halfSize == JNI_TRUE);
    if (ret != 0) {
        delete decoder;
        throwIo(env, RawDecoder::errorMessage(ret));
        return 0;
    }
    return reinterpret_cast<jlong>(decoder);
}

JNIEXPORT jint JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeWidth(JNIEnv*, jobject, jlong handle) {
    return reinterpret_cast<RawDecoder*>(handle)->developedWidth();
}

JNIEXPORT jint JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeHeight(JNIEnv*, jobject, jlong handle) {
    return reinterpret_cast<RawDecoder*>(handle)->developedHeight();
}

JNIEXPORT void JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeCopyTo(
        JNIEnv* env, jobject, jlong handle, jobject bitmap) {
    auto* decoder = reinterpret_cast<RawDecoder*>(handle);
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_F16 ||
        static_cast<int>(info.width) != decoder->developedWidth() ||
        static_cast<int>(info.height) != decoder->developedHeight()) {
        throwIo(env, "Bitmap does not match the developed image");
        return;
    }
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        throwIo(env, "Cannot lock bitmap pixels");
        return;
    }
    decoder->copyRgbaHalf(static_cast<uint16_t*>(pixels), info.stride);
    AndroidBitmap_unlockPixels(env, bitmap);
}

JNIEXPORT void JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeRelease(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<RawDecoder*>(handle);
}

JNIEXPORT jbyteArray JNICALL
Java_io_github_steve_1linux_photograin_data_RawImage_nativeThumbnail(JNIEnv* env, jobject, jstring path) {
    RawDecoder decoder;
    if (!open(env, &decoder, path)) return nullptr;
    std::vector<uint8_t> jpeg;
    if (decoder.thumbnail(&jpeg) != 0 || jpeg.empty()) return nullptr;
    jbyteArray result = env->NewByteArray(static_cast<jsize>(jpeg.size()));
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(jpeg.size()),
                                reinterpret_cast<const jbyte*>(jpeg.data()));
    }
    return result;
}

}  // extern "C"
