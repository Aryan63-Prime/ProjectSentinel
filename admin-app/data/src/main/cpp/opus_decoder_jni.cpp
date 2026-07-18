/**
 * JNI bridge for libopus decoder.
 *
 * Memory ownership:
 * - The OpusDecoder* is allocated by opus_decoder_create() and freed by opus_decoder_destroy().
 * - The native handle is stored as a jlong on the Kotlin side.
 * - Kotlin is responsible for calling nativeDestroy() exactly once.
 *
 * Thread safety:
 * - Each OpusDecoder* must be used from a single thread at a time.
 * - The Kotlin layer ensures this via the audio coroutine dispatcher.
 *
 * Buffer ownership:
 * - Opus input: pinned from JVM ByteArray (GetByteArrayElements / ReleaseByteArrayElements).
 * - PCM output: pinned from JVM ShortArray (GetShortArrayElements / ReleaseShortArrayElements).
 * - No native allocations for buffers — all memory is owned by the JVM.
 *
 * Production invariants:
 * - A decoder failure never destroys the decoder. Only nativeDestroy does that.
 * - Failures return negative values. The caller drops the frame and continues.
 * - nativeDecodePLC performs packet loss concealment for missing frames.
 */

#include <jni.h>
#include <opus.h>
#include <android/log.h>

#define TAG "Sentinel:OpusDecJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

/**
 * Creates a new Opus decoder.
 *
 * @param sampleRate  Sample rate (48000 per protocol).
 * @param channels    Number of channels (1 per protocol).
 * @return            Native handle (pointer cast to jlong), or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_sentinel_admin_data_audio_OpusDecoderJni_nativeCreate(
    JNIEnv *env,
    jclass /* clazz */,
    jint sampleRate,
    jint channels
) {
    int error = 0;
    OpusDecoder *decoder = opus_decoder_create(sampleRate, channels, &error);

    if (error != OPUS_OK || decoder == nullptr) {
        LOGE("opus_decoder_create failed: %s (error=%d)", opus_strerror(error), error);
        return 0;
    }

    LOGI("Opus decoder created: rate=%d ch=%d", sampleRate, channels);
    return reinterpret_cast<jlong>(decoder);
}

/**
 * Decodes an Opus frame into PCM samples.
 *
 * @param handle    Native decoder handle from nativeCreate().
 * @param opusData  Encoded Opus data.
 * @param opusLen   Length of encoded data in bytes.
 * @param pcmOut    Output buffer for decoded PCM samples (16-bit signed).
 * @param frameSize Maximum number of samples per channel to decode.
 * @return          Number of samples decoded per channel, or negative on error.
 */
JNIEXPORT jint JNICALL
Java_com_sentinel_admin_data_audio_OpusDecoderJni_nativeDecode(
    JNIEnv *env,
    jclass /* clazz */,
    jlong handle,
    jbyteArray opusData,
    jint opusLen,
    jshortArray pcmOut,
    jint frameSize
) {
    if (handle == 0) {
        LOGE("nativeDecode called with null handle");
        return -1;
    }

    OpusDecoder *decoder = reinterpret_cast<OpusDecoder *>(handle);

    jbyte *inData = env->GetByteArrayElements(opusData, nullptr);
    if (inData == nullptr) {
        LOGE("GetByteArrayElements failed for opusData");
        return -1;
    }

    jshort *outData = env->GetShortArrayElements(pcmOut, nullptr);
    if (outData == nullptr) {
        LOGE("GetShortArrayElements failed for pcmOut");
        env->ReleaseByteArrayElements(opusData, inData, JNI_ABORT);
        return -1;
    }

    int decoded = opus_decode(
        decoder,
        reinterpret_cast<const unsigned char *>(inData),
        opusLen,
        outData,
        frameSize,
        0  /* no FEC */
    );

    // Release arrays — copy back pcmOut, abort opusData (read-only)
    env->ReleaseByteArrayElements(opusData, inData, JNI_ABORT);
    env->ReleaseShortArrayElements(pcmOut, outData, 0); // 0 = copy back

    if (decoded < 0) {
        LOGE("opus_decode failed: %s (error=%d)", opus_strerror(decoded), decoded);
    }

    return decoded;
}

/**
 * Performs Packet Loss Concealment (PLC) by decoding a missing frame.
 * libopus interpolates from previous state to produce smooth audio.
 *
 * @param handle    Native decoder handle.
 * @param pcmOut    Output buffer for concealed PCM samples.
 * @param frameSize Number of samples per channel to generate.
 * @return          Number of samples generated, or negative on error.
 */
JNIEXPORT jint JNICALL
Java_com_sentinel_admin_data_audio_OpusDecoderJni_nativeDecodePLC(
    JNIEnv *env,
    jclass /* clazz */,
    jlong handle,
    jshortArray pcmOut,
    jint frameSize
) {
    if (handle == 0) {
        LOGE("nativeDecodePLC called with null handle");
        return -1;
    }

    OpusDecoder *decoder = reinterpret_cast<OpusDecoder *>(handle);

    jshort *outData = env->GetShortArrayElements(pcmOut, nullptr);
    if (outData == nullptr) {
        LOGE("GetShortArrayElements failed for pcmOut (PLC)");
        return -1;
    }

    // Pass NULL data and 0 length to trigger PLC
    int decoded = opus_decode(
        decoder,
        nullptr,
        0,
        outData,
        frameSize,
        0
    );

    env->ReleaseShortArrayElements(pcmOut, outData, 0);

    if (decoded < 0) {
        LOGE("opus_decode PLC failed: %s (error=%d)", opus_strerror(decoded), decoded);
    }

    return decoded;
}

/**
 * Destroys an Opus decoder and frees native memory.
 * Safe to call with handle=0 (no-op).
 * Must be called exactly once per handle.
 *
 * @param handle Native decoder handle from nativeCreate().
 */
JNIEXPORT void JNICALL
Java_com_sentinel_admin_data_audio_OpusDecoderJni_nativeDestroy(
    JNIEnv * /* env */,
    jclass /* clazz */,
    jlong handle
) {
    if (handle == 0) {
        return;
    }

    OpusDecoder *decoder = reinterpret_cast<OpusDecoder *>(handle);
    opus_decoder_destroy(decoder);
    LOGI("Opus decoder destroyed");
}

} // extern "C"
