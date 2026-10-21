// SPDX-License-Identifier: GPL-3.0-or-later
// JNI glue between the AI models and io.github.steve_linux.photograin.data: upscaler for
// AiUpscaler, segmenter for SubjectSegmenter, inpainter for ObjectRemover, selector for
// ObjectSelector.
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <string>
#include <vector>

#include "inpainter.h"
#include "segmenter.h"
#include "selector.h"
#include "upscaler.h"

using photograin::Inpainter;
using photograin::Segmenter;
using photograin::Selector;
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

bool readAsset(JNIEnv* env, AAssetManager* assets, jstring name, std::vector<unsigned char>* out) {
    const char* chars = env->GetStringUTFChars(name, nullptr);
    bool ok = readAsset(assets, chars, out);
    env->ReleaseStringUTFChars(name, chars);
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
    bool read = readAsset(env, assets, param, paramData) && readAsset(env, assets, model, modelData);
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

/** Loads the inpainting model from the app's assets; returns a handle, or 0 after throwing. */
JNIEXPORT jlong JNICALL
Java_io_github_steve_1linux_photograin_data_ObjectRemover_nativeLoad(
        JNIEnv* env, jobject, jobject assetManager, jstring param, jstring model) {
    std::vector<unsigned char> paramData, modelData;
    if (!readModel(env, assetManager, param, model, &paramData, &modelData)) return 0;
    auto* inpainter = new Inpainter();
    std::string error;
    if (!inpainter->load(std::string(paramData.begin(), paramData.end()), std::move(modelData), &error)) {
        delete inpainter;
        throwIo(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(inpainter);
}

/**
 * Fills the holes of [image], an Inpainter::kSize square, in place; [mask] has one byte per
 * pixel, nonzero in the holes.
 */
JNIEXPORT void JNICALL
Java_io_github_steve_1linux_photograin_data_ObjectRemover_nativeRun(
        JNIEnv* env, jobject, jlong handle, jobject image, jbyteArray mask) {
    constexpr int n = Inpainter::kSize;
    if (env->GetArrayLength(mask) != n * n) {
        throwIo(env, "The mask has the wrong size");
        return;
    }
    AndroidBitmapInfo info;
    uint8_t* pixels = lock(env, image, &info);
    if (pixels == nullptr) return;
    std::string error;
    bool ok = static_cast<int>(info.width) == n && static_cast<int>(info.height) == n;
    if (!ok) {
        error = "The image has the wrong size";
    } else {
        std::vector<uint8_t> in(n * n * 4), out(n * n * 4), holes(n * n);
        for (int y = 0; y < n; y++) std::copy_n(pixels + y * info.stride, n * 4, in.data() + y * n * 4);
        env->GetByteArrayRegion(mask, 0, n * n, reinterpret_cast<jbyte*>(holes.data()));
        ok = reinterpret_cast<Inpainter*>(handle)->run(in.data(), holes.data(), out.data(), &error);
        if (ok) for (int y = 0; y < n; y++) std::copy_n(out.data() + y * n * 4, n * 4, pixels + y * info.stride);
    }
    AndroidBitmap_unlockPixels(env, image);
    if (!ok) throwIo(env, error);
}

/** Loads the selection model from the app's assets; returns a handle, or 0 after throwing. */
JNIEXPORT jlong JNICALL
Java_io_github_steve_1linux_photograin_data_ObjectSelector_nativeLoad(
        JNIEnv* env, jobject, jobject assetManager, jstring encoderParam, jstring encoderModel,
        jstring decoderParam, jstring decoderModel, jstring prompt) {
    std::vector<unsigned char> encParam, encModel, decParam, decModel, promptData;
    if (!readModel(env, assetManager, encoderParam, encoderModel, &encParam, &encModel) ||
        !readModel(env, assetManager, decoderParam, decoderModel, &decParam, &decModel)) {
        return 0;
    }
    if (!readAsset(env, AAssetManager_fromJava(env, assetManager), prompt, &promptData)) {
        throwIo(env, "Cannot read the model from the app");
        return 0;
    }
    auto* selector = new Selector();
    std::string error;
    if (!selector->load(std::string(encParam.begin(), encParam.end()), std::move(encModel),
                        std::string(decParam.begin(), decParam.end()), std::move(decModel), promptData, &error)) {
        delete selector;
        throwIo(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(selector);
}

/** Reads [image], upright with a long edge of at most Selector::kSize, for later selections. */
JNIEXPORT void JNICALL
Java_io_github_steve_1linux_photograin_data_ObjectSelector_nativeEncode(
        JNIEnv* env, jobject, jlong handle, jobject image) {
    AndroidBitmapInfo info;
    uint8_t* pixels = lock(env, image, &info);
    if (pixels == nullptr) return;
    std::string error;
    bool ok = reinterpret_cast<Selector*>(handle)->encode(pixels, static_cast<int>(info.width), static_cast<int>(info.height),
                                                          static_cast<int>(info.stride), &error);
    AndroidBitmap_unlockPixels(env, image);
    if (!ok) throwIo(env, error);
}

/**
 * The object at the taps of the last encoded image: [coords] holds x, y pairs in its pixels
 * and [labels] 1 to include or 0 to leave out each one. Returns Selector::kMaskSize squared
 * values, positive on the object, or null after throwing.
 */
JNIEXPORT jfloatArray JNICALL
Java_io_github_steve_1linux_photograin_data_ObjectSelector_nativeSelect(
        JNIEnv* env, jobject, jlong handle, jfloatArray coords, jintArray labels) {
    const int n = env->GetArrayLength(labels);
    if (env->GetArrayLength(coords) != 2 * n) {
        throwIo(env, "Expected two coordinates per tap");
        return nullptr;
    }
    std::vector<float> xy(2 * n);
    std::vector<int> kinds(n);
    env->GetFloatArrayRegion(coords, 0, 2 * n, xy.data());
    env->GetIntArrayRegion(labels, 0, n, reinterpret_cast<jint*>(kinds.data()));
    std::vector<float> mask(Selector::kMaskSize * Selector::kMaskSize);
    std::string error;
    if (!reinterpret_cast<Selector*>(handle)->select(xy.data(), kinds.data(), n, mask.data(), &error)) {
        throwIo(env, error);
        return nullptr;
    }
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(mask.size()));
    if (out != nullptr) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(mask.size()), mask.data());
    return out;
}

}  // extern "C"
