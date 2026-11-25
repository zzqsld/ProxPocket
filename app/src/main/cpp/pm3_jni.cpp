#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <android/log.h>

extern "C" {
#include "pm3.h"
}

#define LOG_TAG "Pm3Bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_proxpocket_app_pm3_Pm3Core_nativeInit(JNIEnv *env, jobject /* thiz */, jstring homePath) {
    const char *home = env->GetStringUTFChars(homePath, nullptr);
    if (home == nullptr) return JNI_FALSE;
    setenv("HOME", home, 1);
    LOGI("HOME=%s", home);
    env->ReleaseStringUTFChars(homePath, home);
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_proxpocket_app_pm3_Pm3Core_nativeOpen(JNIEnv *env, jobject /* thiz */, jstring port) {
    const char *p = env->GetStringUTFChars(port, nullptr);
    if (p == nullptr) return 0;
    LOGI("pm3_open(%s)", p);
    pm3 *dev = pm3_open(p);
    env->ReleaseStringUTFChars(port, p);
    if (dev == nullptr) {
        LOGE("pm3_open failed");
        return 0;
    }
    return reinterpret_cast<jlong>(dev);
}

JNIEXPORT jstring JNICALL
Java_com_proxpocket_app_pm3_Pm3Core_nativeExec(JNIEnv *env, jobject /* thiz */, jlong handle, jstring cmd) {
    pm3 *dev = reinterpret_cast<pm3 *>(handle);
    if (dev == nullptr) return env->NewStringUTF("!! not connected");
    const char *c = env->GetStringUTFChars(cmd, nullptr);
    if (c == nullptr) return nullptr;
    pm3_console(dev, c, true, true);
    env->ReleaseStringUTFChars(cmd, c);
    const char *out = pm3_grabbed_output_get(dev);
    return env->NewStringUTF(out != nullptr ? out : "");
}

JNIEXPORT void JNICALL
Java_com_proxpocket_app_pm3_Pm3Core_nativeClose(JNIEnv * /* env */, jobject /* thiz */, jlong handle) {
    pm3 *dev = reinterpret_cast<pm3 *>(handle);
    if (dev != nullptr) {
        pm3_close(dev);
    }
}

} // extern "C"
