// Stub implementation used when the llama.cpp source tree is not available.
// All JNI methods return safe defaults so the app compiles and runs
// in cloud-only mode. The LocalLlmEngine degrades gracefully.

#include <jni.h>
#include <android/log.h>

#define TAG "LlamaBridgeStub"

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeIsAvailable(JNIEnv *, jclass) {
    __android_log_write(ANDROID_LOG_WARN, TAG, "llama.cpp not available — using stub");
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeLoadModel(JNIEnv *, jclass, jstring, jint, jint) {
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeUnloadModel(JNIEnv *, jclass) {}

extern "C" JNIEXPORT void JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeGenerate(JNIEnv *, jclass, jstring, jint, jfloat, jobject) {}

extern "C" JNIEXPORT jint JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeTokenCount(JNIEnv *, jclass, jstring) {
    return 0;
}
