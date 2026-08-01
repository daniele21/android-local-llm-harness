#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_daniele21_localllm_llamacpp_LlamaCppBridge_runtimeVersion(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("llama.cpp-not-linked");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_daniele21_localllm_llamacpp_LlamaCppBridge_isLlamaCppLinked(
        JNIEnv* /* env */,
        jobject /* this */) {
    return JNI_FALSE;
}
