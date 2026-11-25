// SPDX-License-Identifier: GPL-3.0-or-later
// JNI glue between hdr_merge and io.github.steve_linux.photograin.data.HdrMerger.
#include <android/bitmap.h>
#include <jni.h>

#include <string>
#include <vector>

#include "hdr_merge.h"

using photograin::HdrOptions;
using photograin::HdrProgress;
using photograin::HdrResult;
using photograin::HdrSource;

namespace {

void throwIo(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

/** Locks every bitmap of [array]; on failure throws, unlocks what it locked and returns false. */
bool lockAll(JNIEnv* env, jobjectArray array, std::vector<jobject>* bitmaps, std::vector<HdrSource>* sources) {
    jsize n = env->GetArrayLength(array);
    for (jsize i = 0; i < n; ++i) {
        jobject bitmap = env->GetObjectArrayElement(array, i);
        AndroidBitmapInfo info;
        void* pixels = nullptr;
        bool ok = AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS &&
                  (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 || info.format == ANDROID_BITMAP_FORMAT_RGBA_F16) &&
                  (sources->empty() || (static_cast<int>(info.width) == sources->front().width &&
                                        static_cast<int>(info.height) == sources->front().height)) &&
                  AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS;
        if (!ok) {
            for (jobject b : *bitmaps) AndroidBitmap_unlockPixels(env, b);
            bitmaps->clear();
            throwIo(env, "The photos must be ARGB_8888 or RGBA_F16 bitmaps of the same size");
            return false;
        }
        HdrSource s;
        s.pixels = pixels;
        s.width = static_cast<int>(info.width);
        s.height = static_cast<int>(info.height);
        s.stride = static_cast<int>(info.stride);
        s.half = info.format == ANDROID_BITMAP_FORMAT_RGBA_F16;
        bitmaps->push_back(bitmap);
        sources->push_back(s);
    }
    return true;
}

void unlockAll(JNIEnv* env, const std::vector<jobject>& bitmaps) {
    for (jobject b : bitmaps) AndroidBitmap_unlockPixels(env, b);
}

/** Calls [listener].onProgress(fraction); false (cancel) if it says so or threw. */
HdrProgress progressOf(JNIEnv* env, jobject listener) {
    if (listener == nullptr) return nullptr;
    jclass cls = env->GetObjectClass(listener);
    jmethodID method = env->GetMethodID(cls, "onProgress", "(F)Z");
    return [env, listener, method](float fraction) {
        jboolean go = env->CallBooleanMethod(listener, method, fraction);
        if (env->ExceptionCheck()) return false;
        return go == JNI_TRUE;
    };
}

}  // namespace

extern "C" {

/**
 * Returns [reference, x, y, width, height, dx0, dy0, dx1, dy1, ...], or null if cancelled.
 */
JNIEXPORT jintArray JNICALL
Java_io_github_steve_1linux_photograin_data_HdrMerger_nativeAlign(
        JNIEnv* env, jobject, jobjectArray frames, jboolean align, jobject listener) {
    std::vector<jobject> bitmaps;
    std::vector<HdrSource> sources;
    if (!lockAll(env, frames, &bitmaps, &sources)) return nullptr;
    HdrOptions options;
    options.align = align == JNI_TRUE;
    HdrResult result;
    bool done = photograin::alignExposures(sources, options, &result, progressOf(env, listener));
    unlockAll(env, bitmaps);
    if (!done) return nullptr;
    std::vector<jint> v{result.reference, result.x, result.y, result.width, result.height};
    for (size_t k = 0; k < sources.size(); ++k) {
        v.push_back(result.dx[k]);
        v.push_back(result.dy[k]);
    }
    jintArray array = env->NewIntArray(static_cast<jsize>(v.size()));
    if (array != nullptr) env->SetIntArrayRegion(array, 0, static_cast<jsize>(v.size()), v.data());
    return array;
}

/** Fuses into [output] (ARGB_8888, the aligned size); false if cancelled. */
JNIEXPORT jboolean JNICALL
Java_io_github_steve_1linux_photograin_data_HdrMerger_nativeFuse(
        JNIEnv* env, jobject, jobjectArray frames, jintArray alignment, jboolean deghost, jobject output,
        jobject listener) {
    std::vector<jobject> bitmaps;
    std::vector<HdrSource> sources;
    if (!lockAll(env, frames, &bitmaps, &sources)) return JNI_FALSE;

    jsize length = env->GetArrayLength(alignment);
    if (length != static_cast<jsize>(5 + 2 * sources.size())) {
        unlockAll(env, bitmaps);
        throwIo(env, "Alignment does not match the photos");
        return JNI_FALSE;
    }
    std::vector<jint> a(length);
    env->GetIntArrayRegion(alignment, 0, length, a.data());
    HdrResult result;
    result.reference = a[0];
    result.x = a[1];
    result.y = a[2];
    result.width = a[3];
    result.height = a[4];
    for (size_t k = 0; k < sources.size(); ++k) {
        result.dx.push_back(a[5 + 2 * k]);
        result.dy.push_back(a[6 + 2 * k]);
    }

    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, output, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || static_cast<int>(info.width) != result.width ||
        static_cast<int>(info.height) != result.height ||
        AndroidBitmap_lockPixels(env, output, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        unlockAll(env, bitmaps);
        throwIo(env, "The output bitmap does not match the merge");
        return JNI_FALSE;
    }
    HdrOptions options;
    options.deghost = deghost == JNI_TRUE;
    bool done = photograin::fuseExposures(sources, options, result, static_cast<uint8_t*>(pixels),
                                          static_cast<int>(info.stride), progressOf(env, listener));
    AndroidBitmap_unlockPixels(env, output);
    unlockAll(env, bitmaps);
    return done ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
