// SPDX-License-Identifier: GPL-3.0-or-later
// JNI glue between the AI models and io.github.steve_linux.photograin.data: upscaler for
// AiUpscaler, segmenter for SubjectSegmenter.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <jni.h>

#include <string>
#include <vector>

#include "segmenter.h"
#include "upscaler.h"

using photograin::Segmenter;
using photograin::Upscaler;

namespace {

void throwIo(JNIEnv* env, const std::string& message) {
    jclass cls = env->FindClass("java/io/IOException");
    if (cls != nullptr) env->ThrowNew(cls, message.c_str());
}

bool readAsset(AAssetManager* assets, const char* name, std::vector<unsigned char>* out) {
    AAsset* asset = AAssetManager_open(assets, name, AASSET_MODE_BUFFER);
    if (asset == nullptr) return false;
    out->resize(AAsset_getLength(asset));
    bool ok = AAsset_read(asset, out->data(), out->size()) == static_cast<int>(out->size());
    AAsset_close(asset);
    return ok;
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

/** Reads the [param] and [model] assets; throws and returns false on failure. */
bool readModel(JNIEnv* env, jobject assetManager, jstring param, jstring model,
               std::vector<unsigned char>* paramData, std::vector<unsigned char>* modelData) {
    AAssetManager* assets = AAssetManager_fromJava(env, assetManager);
    const char* paramName = env->GetStringUTFChars(param, nullptr);
    const char* modelName = env->GetStringUTFChars(model, nullptr);
    bool read = readAsset(assets, paramName, paramData) && readAsset(assets, modelName, modelData);
    env->ReleaseStringUTFChars(param, paramName);
    env->ReleaseStringUTFChars(model, modelName);
    if (!read) throwIo(env, "Cannot read the model from the app");
    return read;
}

}  // namespace

extern "C" {

/** Loads [param] and [model] from the app's assets; returns a handle, or 0 after throwing. */
JNIEXPORT jlong JNICALL
Java_io_github_steve_1linux_photograin_data_AiUpscaler_nativeLoad(
        JNIEnv* env, jobject, jobject assetManager, jstring param, jstring model, jboolean gpu) {
    std::vector<unsigned char> paramData, modelData;
    if (!readModel(env, assetManager, param, model, &paramData, &modelData)) return 0;
    auto* upscaler = new Upscaler();
    std::string error;
    if (!upscaler->load(std::string(paramData.begin(), paramData.end()), std::move(modelData), gpu, &error)) {
        delete upscaler;
        throwIo(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(upscaler);
}

JNIEXPORT jboolean JNICALL
Java_io_github_steve_1linux_photograin_data_AiUpscaler_nativeUsesGpu(JNIEnv*, jobject, jlong handle) {
    return reinterpret_cast<Upscaler*>(handle)->gpu();
}

/** Enlarges [input] four times into [output], which must be exactly four times its size. */
JNIEXPORT void JNICALL
Java_io_github_steve_1linux_photograin_data_AiUpscaler_nativeRun(
        JNIEnv* env, jobject, jlong handle, jobject input, jobject output) {
    AndroidBitmapInfo inInfo, outInfo;
    uint8_t* in = lock(env, input, &inInfo);
    if (in == nullptr) return;
    uint8_t* out = lock(env, output, &outInfo);
    if (out == nullptr) {
        AndroidBitmap_unlockPixels(env, input);
        return;
    }
    std::string error;
    bool ok = outInfo.width == inInfo.width * Upscaler::kScale && outInfo.height == inInfo.height * Upscaler::kScale;
    if (!ok) {
        error = "Output bitmap has the wrong size";
    } else {
        ok = reinterpret_cast<Upscaler*>(handle)->run(in, static_cast<int>(inInfo.width), static_cast<int>(inInfo.height),
                                                      static_cast<int>(inInfo.stride), out, static_cast<int>(outInfo.stride),
                                                      &error);
    }
    AndroidBitmap_unlockPixels(env, output);
    AndroidBitmap_unlockPixels(env, input);
    if (!ok) throwIo(env, error);
}

/** Loads the segmentation model from the app's assets; returns a handle, or 0 after throwing. */
JNIEXPORT jlong JNICALL
Java_io_github_steve_1linux_photograin_data_SubjectSegmenter_nativeLoad(
        JNIEnv* env, jobject, jobject assetManager, jstring param, jstring model) {
    std::vector<unsigned char> paramData, modelData;
    if (!readModel(env, assetManager, param, model, &paramData, &modelData)) return 0;
    auto* segmenter = new Segmenter();
    std::string error;
    if (!segmenter->load(std::string(paramData.begin(), paramData.end()), std::move(modelData), &error)) {
        delete segmenter;
        throwIo(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(segmenter);
}

/** The subject of [input] as Segmenter::kSize squared bytes, or null after throwing. */
JNIEXPORT jbyteArray JNICALL
Java_io_github_steve_1linux_photograin_data_SubjectSegmenter_nativeRun(
        JNIEnv* env, jobject, jlong handle, jobject input) {
    AndroidBitmapInfo info;
    uint8_t* in = lock(env, input, &info);
    if (in == nullptr) return nullptr;
    std::vector<uint8_t> mask(Segmenter::kSize * Segmenter::kSize);
    std::string error;
    bool ok = reinterpret_cast<Segmenter*>(handle)->run(in, static_cast<int>(info.width), static_cast<int>(info.height),
                                                        static_cast<int>(info.stride), mask.data(), &error);
    AndroidBitmap_unlockPixels(env, input);
    if (!ok) {
        throwIo(env, error);
        return nullptr;
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(mask.size()));
    if (out != nullptr) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(mask.size()), reinterpret_cast<const jbyte*>(mask.data()));
    }
    return out;
}

}  // extern "C"
