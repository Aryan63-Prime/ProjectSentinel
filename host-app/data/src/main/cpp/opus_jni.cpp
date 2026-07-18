/**
 * JNI bridge for libopus encoder.
 *
 * Memory ownership:
 * - The OpusEncoder* is allocated by opus_encoder_create() and freed by opus_encoder_destroy().
 * - The native handle is stored as a jlong on the Kotlin side.
 * - Kotlin is responsible for calling nativeDestroy() to free the handle.
 *
 * Thread safety:
 * - Each OpusEncoder* must be used from a single thread at a time.
 * - The Kotlin layer ensures this via the audio coroutine dispatcher.
 *
 * Buffer ownership:
 * - PCM input: pinned from JVM ShortArray (GetShortArrayElements / ReleaseShortArrayElements).
 * - Opus output: pinned from JVM ByteArray (GetByteArrayElements / ReleaseByteArrayElements).
 * - No native allocations for buffers — all memory is owned by the JVM.
 */

#include <jni.h>
#include <opus.h>
#include <android/log.h>

#define TAG "Sentinel:OpusJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

/**
 * Creates a new Opus encoder.
 *
 * @param sampleRate  Sample rate (must be 48000 per protocol).
 * @param channels    Number of channels (must be 1 per protocol).
 * @param application Opus application type (OPUS_APPLICATION_VOIP = 2048).
 * @param bitrate     Target bitrate in bits/s (24000 per protocol).
 * @return            Native handle (pointer cast to jlong), or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_sentinel_host_data_audio_OpusJni_nativeCreate(
    JNIEnv *env,
    jclass /* clazz */,
    jint sampleRate,
    jint channels,
    jint application,
    jint bitrate
) {
    int error = 0;
    OpusEncoder *encoder = opus_encoder_create(sampleRate, channels, application, &error);

    if (error != OPUS_OK || encoder == nullptr) {
        LOGE("opus_encoder_create failed: %s (error=%d)", opus_strerror(error), error);
        return 0;
    }

    // Set target bitrate
    error = opus_encoder_ctl(encoder, OPUS_SET_BITRATE(bitrate));
    if (error != OPUS_OK) {
        LOGE("OPUS_SET_BITRATE(%d) failed: %s", bitrate, opus_strerror(error));
        opus_encoder_destroy(encoder);
        return 0;
    }

    // Enable VBR for adaptive bitrate
    opus_encoder_ctl(encoder, OPUS_SET_VBR(1));

    // Set signal type to voice for better voice encoding
    opus_encoder_ctl(encoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));

    LOGI("Opus encoder created: rate=%d ch=%d bitrate=%d", sampleRate, channels, bitrate);
    return reinterpret_cast<jlong>(encoder);
}

/**
 * Encodes a frame of PCM audio into Opus.
 *
 * @param handle    Native encoder handle from nativeCreate().
 * @param pcm       PCM samples (16-bit signed, interleaved if stereo).
 * @param frameSize Number of samples per channel in this frame (960 for 20ms at 48kHz).
 * @param output    Output buffer for encoded Opus data.
 * @param maxOutput Maximum number of bytes to write to output.
 * @return          Number of bytes written to output, or negative on error.
 */
JNIEXPORT jint JNICALL
Java_com_sentinel_host_data_audio_OpusJni_nativeEncode(
    JNIEnv *env,
    jclass /* clazz */,
    jlong handle,
    jshortArray pcm,
    jint frameSize,
    jbyteArray output,
    jint maxOutput
) {
    if (handle == 0) {
        LOGE("nativeEncode called with null handle");
        return -1;
    }

    OpusEncoder *encoder = reinterpret_cast<OpusEncoder *>(handle);

    // Pin JVM arrays to avoid copies where possible
    jshort *pcmData = env->GetShortArrayElements(pcm, nullptr);
    if (pcmData == nullptr) {
        LOGE("GetShortArrayElements failed for pcm");
        return -1;
    }

    jbyte *outData = env->GetByteArrayElements(output, nullptr);
    if (outData == nullptr) {
        LOGE("GetByteArrayElements failed for output");
        env->ReleaseShortArrayElements(pcm, pcmData, JNI_ABORT);
        return -1;
    }

    int encoded = opus_encode(
        encoder,
        pcmData,
        frameSize,
        reinterpret_cast<unsigned char *>(outData),
        maxOutput
    );

    // Release arrays — copy back output, abort pcm (read-only)
    env->ReleaseShortArrayElements(pcm, pcmData, JNI_ABORT);
    env->ReleaseByteArrayElements(output, outData, 0); // 0 = copy back

    if (encoded < 0) {
        LOGE("opus_encode failed: %s (error=%d)", opus_strerror(encoded), encoded);
    }

    return encoded;
}

/**
 * Destroys an Opus encoder and frees native memory.
 * Safe to call with handle=0 (no-op).
 *
 * @param handle Native encoder handle from nativeCreate().
 */
JNIEXPORT void JNICALL
Java_com_sentinel_host_data_audio_OpusJni_nativeDestroy(
    JNIEnv * /* env */,
    jclass /* clazz */,
    jlong handle
) {
    if (handle == 0) {
        return;
    }

    OpusEncoder *encoder = reinterpret_cast<OpusEncoder *>(handle);
    opus_encoder_destroy(encoder);
    LOGI("Opus encoder destroyed");
}

} // extern "C"
