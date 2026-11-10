#include <jni.h>
#include <android/log.h>
#include <Processing.NDI.Lib.h>

#include <atomic>
#include <chrono>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "thor_ndi"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_mu;
bool g_initialized = false;
NDIlib_find_instance_t g_find = nullptr;
NDIlib_recv_instance_t g_recv = nullptr;
std::string g_source_name;
std::string g_source_url;
std::atomic<bool> g_paused{false};
std::atomic<bool> g_audio_enabled{false};
int g_max_w = 0; // 0 = native (no client downscale)
int g_max_h = 0;
int64_t g_min_frame_interval_ns = 0; // 0 = unlimited display FPS
int64_t g_last_frame_ns = 0;

static jstring utf8(JNIEnv* env, const char* s) {
    if (!s) s = "";
    return env->NewStringUTF(s);
}

static bool ensure_init_locked() {
    if (g_initialized) return true;
    if (!NDIlib_initialize()) {
        LOGE("NDIlib_initialize failed");
        return false;
    }
    g_initialized = true;
    return true;
}

static void destroy_recv_locked() {
    if (g_recv) {
        NDIlib_recv_destroy(g_recv);
        g_recv = nullptr;
    }
    g_source_name.clear();
    g_source_url.clear();
    g_paused = false;
}

/** quality 0–49 → lowest; 50–100 → highest (full source bandwidth/resolution). */
static NDIlib_recv_bandwidth_e bandwidth_for_quality(jint quality) {
    return quality >= 50 ? NDIlib_recv_bandwidth_highest : NDIlib_recv_bandwidth_lowest;
}

static void rgba_to_argb_scaled(
    const uint8_t* src,
    int src_w,
    int src_h,
    int stride,
    int dst_w,
    int dst_h,
    jint* dst
) {
    for (int y = 0; y < dst_h; ++y) {
        const int sy = (y * src_h) / dst_h;
        const uint8_t* row = src + sy * stride;
        for (int x = 0; x < dst_w; ++x) {
            const int sx = (x * src_w) / dst_w;
            const uint8_t* p = row + sx * 4;
            const uint8_t r = p[0];
            const uint8_t g = p[1];
            const uint8_t b = p[2];
            const uint8_t a = p[3];
            dst[y * dst_w + x] = (jint)((a << 24) | (r << 16) | (g << 8) | b);
        }
    }
}

static jintArray make_video_array(
    JNIEnv* env,
    const NDIlib_video_frame_v2_t& video,
    int max_w,
    int max_h
) {
    const int src_w = video.xres;
    const int src_h = video.yres;
    if (src_w <= 0 || src_h <= 0 || !video.p_data) return nullptr;

    int dst_w = src_w;
    int dst_h = src_h;
    // max_w/max_h == 0 → keep native received size (no client downscale).
    if (max_w > 0 && max_h > 0 && (dst_w > max_w || dst_h > max_h)) {
        const float sx = (float)max_w / (float)dst_w;
        const float sy = (float)max_h / (float)dst_h;
        const float s = sx < sy ? sx : sy;
        dst_w = (int)(dst_w * s);
        dst_h = (int)(dst_h * s);
        if (dst_w < 1) dst_w = 1;
        if (dst_h < 1) dst_h = 1;
    }

    const jsize len = 2 + dst_w * dst_h;
    jintArray out = env->NewIntArray(len);
    if (!out) return nullptr;
    std::vector<jint> buf((size_t)len);
    buf[0] = dst_w;
    buf[1] = dst_h;
    rgba_to_argb_scaled(
        video.p_data,
        src_w,
        src_h,
        video.line_stride_in_bytes,
        dst_w,
        dst_h,
        buf.data() + 2
    );
    env->SetIntArrayRegion(out, 0, len, buf.data());
    return out;
}

/** [sampleRate, channels, interleaved float samples…] */
static jfloatArray make_audio_array(JNIEnv* env, const NDIlib_audio_frame_v2_t& audio) {
    const int rate = audio.sample_rate;
    const int ch = audio.no_channels;
    const int n = audio.no_samples;
    if (rate <= 0 || ch <= 0 || n <= 0 || !audio.p_data) return nullptr;

    const jsize len = 2 + ch * n;
    jfloatArray out = env->NewFloatArray(len);
    if (!out) return nullptr;
    std::vector<jfloat> buf((size_t)len);
    buf[0] = (jfloat)rate;
    buf[1] = (jfloat)ch;
    const int stride_floats = audio.channel_stride_in_bytes / (int)sizeof(float);
    for (int i = 0; i < n; ++i) {
        for (int c = 0; c < ch; ++c) {
            const float* plane = audio.p_data + c * stride_floats;
            buf[2 + i * ch + c] = plane[i];
        }
    }
    env->SetFloatArrayRegion(out, 0, len, buf.data());
    return out;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeInit(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mu);
    return ensure_init_locked() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeShutdown(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mu);
    destroy_recv_locked();
    if (g_find) {
        NDIlib_find_destroy(g_find);
        g_find = nullptr;
    }
    if (g_initialized) {
        NDIlib_destroy();
        g_initialized = false;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeDiscover(
    JNIEnv* env,
    jobject,
    jint timeout_ms
) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (!ensure_init_locked()) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }
    if (!g_find) {
        g_find = NDIlib_find_create_v2();
        if (!g_find) {
            return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
        }
    }
    NDIlib_find_wait_for_sources(g_find, timeout_ms > 0 ? timeout_ms : 1000);
    uint32_t count = 0;
    const NDIlib_source_t* sources = NDIlib_find_get_current_sources(g_find, &count);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray((jsize)(count * 2), stringClass, nullptr);
    for (uint32_t i = 0; i < count; ++i) {
        env->SetObjectArrayElement(out, (jsize)(i * 2), utf8(env, sources[i].p_ndi_name));
        env->SetObjectArrayElement(out, (jsize)(i * 2 + 1), utf8(env, sources[i].p_url_address));
    }
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeConnect(
    JNIEnv* env,
    jobject,
    jstring jname,
    jstring jurl,
    jint quality,
    jboolean audioEnabled,
    jint maxWidth,
    jint maxHeight,
    jint maxFps
) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (!ensure_init_locked()) return JNI_FALSE;

    destroy_recv_locked();

    const char* name = env->GetStringUTFChars(jname, nullptr);
    const char* url = env->GetStringUTFChars(jurl, nullptr);
    g_source_name = name ? name : "";
    g_source_url = url ? url : "";
    if (name) env->ReleaseStringUTFChars(jname, name);
    if (url) env->ReleaseStringUTFChars(jurl, url);

    // 0 = native size / unlimited FPS (matches Kotlin NdiSettings).
    g_max_w = maxWidth > 0 ? maxWidth : 0;
    g_max_h = maxHeight > 0 ? maxHeight : 0;
    if (maxFps <= 0) {
        g_min_frame_interval_ns = 0;
    } else {
        g_min_frame_interval_ns = 1000000000LL / maxFps;
    }
    g_last_frame_ns = 0;
    g_audio_enabled = audioEnabled == JNI_TRUE;

    NDIlib_recv_create_v3_t desc;
    std::memset(&desc, 0, sizeof(desc));
    desc.source_to_connect_to.p_ndi_name = g_source_name.empty() ? nullptr : g_source_name.c_str();
    desc.source_to_connect_to.p_url_address = g_source_url.empty() ? nullptr : g_source_url.c_str();
    desc.color_format = NDIlib_recv_color_format_RGBX_RGBA;
    desc.bandwidth = bandwidth_for_quality(quality);
    desc.allow_video_fields = false;
    desc.p_ndi_recv_name = "THOR OSC Controller";

    g_recv = NDIlib_recv_create_v3(&desc);
    if (!g_recv) {
        LOGE("NDIlib_recv_create_v3 failed");
        return JNI_FALSE;
    }
    NDIlib_source_t src;
    src.p_ndi_name = g_source_name.empty() ? nullptr : g_source_name.c_str();
    src.p_url_address = g_source_url.empty() ? nullptr : g_source_url.c_str();
    NDIlib_recv_connect(g_recv, &src);
    g_paused = false;
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeDisconnect(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mu);
    destroy_recv_locked();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeSetPaused(JNIEnv*, jobject, jboolean paused) {
    g_paused = paused == JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativeIsConnected(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mu);
    return g_recv != nullptr ? JNI_TRUE : JNI_FALSE;
}

/**
 * Returns Object[2]:
 *   [0] Integer kind: 1=video, 2=audio
 *   [1] int[] video [w,h,argb…] OR float[] audio [rate, ch, interleaved…]
 * or null if nothing / paused drain.
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_mareg_osccontroller_ndi_NdiNative_nativePoll(JNIEnv* env, jobject) {
    NDIlib_recv_instance_t recv = nullptr;
    int max_w = 0;
    int max_h = 0;
    int64_t min_interval = 0;
    bool want_audio = false;
    bool paused = false;
    {
        std::lock_guard<std::mutex> lock(g_mu);
        if (!g_recv) return nullptr;
        recv = g_recv;
        max_w = g_max_w;
        max_h = g_max_h;
        min_interval = g_min_frame_interval_ns;
        want_audio = g_audio_enabled.load();
        paused = g_paused.load();
    }

    NDIlib_video_frame_v2_t video;
    NDIlib_audio_frame_v2_t audio;
    const NDIlib_frame_type_e type = NDIlib_recv_capture_v2(recv, &video, &audio, nullptr, 50);

    // While paused: drain queues so NDI does not back up, but emit nothing.
    if (paused) {
        if (type == NDIlib_frame_type_video) {
            NDIlib_recv_free_video_v2(recv, &video);
        } else if (type == NDIlib_frame_type_audio) {
            NDIlib_recv_free_audio_v2(recv, &audio);
        }
        return nullptr;
    }

    jclass objectClass = env->FindClass("java/lang/Object");
    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID integerCtor = env->GetMethodID(integerClass, "<init>", "(I)V");

    auto pack = [&](jint kind, jobject payload) -> jobjectArray {
        jobjectArray arr = env->NewObjectArray(2, objectClass, nullptr);
        env->SetObjectArrayElement(arr, 0, env->NewObject(integerClass, integerCtor, kind));
        env->SetObjectArrayElement(arr, 1, payload);
        return arr;
    };

    if (type == NDIlib_frame_type_audio) {
        jobjectArray result = nullptr;
        if (want_audio) {
            jfloatArray pcm = make_audio_array(env, audio);
            if (pcm) result = pack(2, pcm);
        }
        NDIlib_recv_free_audio_v2(recv, &audio);
        return result;
    }

    if (type != NDIlib_frame_type_video) {
        return nullptr;
    }

    const auto now = std::chrono::steady_clock::now().time_since_epoch().count();
    const bool skip_video_emit =
        min_interval > 0 && g_last_frame_ns > 0 && (now - g_last_frame_ns) < min_interval;
    if (skip_video_emit) {
        // FPS throttle: drop after receive (still frees bandwidth locally/CPU).
        NDIlib_recv_free_video_v2(recv, &video);
        return nullptr;
    }

    jintArray pixels = make_video_array(env, video, max_w, max_h);
    NDIlib_recv_free_video_v2(recv, &video);
    if (!pixels) return nullptr;
    g_last_frame_ns = now;
    return pack(1, pixels);
}
