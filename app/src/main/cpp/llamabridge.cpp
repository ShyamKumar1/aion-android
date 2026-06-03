#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

// ---------------------------------------------------------------------------
// Global model state — one model at a time. Mutex-guarded for thread safety.
// ---------------------------------------------------------------------------
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static std::mutex g_mutex;

// ---------------------------------------------------------------------------
// Helper: call the Kotlin callback with a token string.
// ---------------------------------------------------------------------------
static void call_token_callback(JNIEnv *env, jobject callback, const std::string &token) {
    jclass cls = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    if (method) {
        jstring jtoken = env->NewStringUTF(token.c_str());
        env->CallVoidMethod(callback, method, jtoken);
        env->DeleteLocalRef(jtoken);
    }
    env->DeleteLocalRef(cls);
}

// ---------------------------------------------------------------------------
// JNI: nativeIsAvailable — always true on arm64-v8a with NEON.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeIsAvailable(JNIEnv *, jclass) {
    // llama.cpp requires arm64 with NEON. We assume the APK is arm64-only
    // (see AION_GUIDELINES §19) so this returns true.
    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// JNI: nativeLoadModel — load a GGUF file.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jboolean JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeLoadModel(
    JNIEnv *env, jclass, jstring jpath, jint n_ctx, jint n_gpu_layers) {

    std::lock_guard<std::mutex> lock(g_mutex);

    // Unload any previously loaded model
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }

    const char *path = env->GetStringUTFChars(jpath, nullptr);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    g_model = llama_load_model_from_file(path, model_params);
    env->ReleaseStringUTFChars(jpath, path);

    if (!g_model) return JNI_FALSE;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = n_ctx; // batch size = context for simplicity
    g_ctx = llama_new_context_with_model(g_model, ctx_params);

    if (!g_ctx) {
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// JNI: nativeUnloadModel — free all resources.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeUnloadModel(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
}

// ---------------------------------------------------------------------------
// JNI: nativeGenerate — tokenize prompt, sample, call callback per token.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT void JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeGenerate(
    JNIEnv *env, jclass, jstring jprompt, jint max_tokens, jfloat temperature,
    jobject tokenCallback) {

    // Keep a global ref so the callback stays valid across JNI calls
    jobject globalCallback = env->NewGlobalRef(tokenCallback);

    {
        std::lock_guard<std::mutex> lock(g_mutex);

        if (!g_model || !g_ctx) {
            call_token_callback(env, globalCallback, "[no model loaded]");
            env->DeleteGlobalRef(globalCallback);
            return;
        }

        const char *prompt_chars = env->GetStringUTFChars(jprompt, nullptr);
        std::string prompt(prompt_chars);
        env->ReleaseStringUTFChars(jprompt, prompt_chars);

        // Tokenize the prompt
        int n_tokens = llama_tokenize(g_model, prompt.c_str(), prompt.size(), nullptr, 0, true, false);
        std::vector<llama_token> tokens(n_tokens);
        llama_tokenize(g_model, prompt.c_str(), prompt.size(), tokens.data(), tokens.size(), true, false);

        // Generate
        for (int i = 0; i < max_tokens; ++i) {
            if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), tokens.size()))) {
                break;
            }

            auto token_id = llama_sample_token_greedy(g_ctx);
            if (token_id == llama_token_eos(g_model)) break;

            tokens = {token_id};
            std::string piece = llama_token_to_piece(g_ctx, token_id);

            // Call the Kotlin callback
            call_token_callback(env, globalCallback, piece);
        }
    }

    env->DeleteGlobalRef(globalCallback);
}

// ---------------------------------------------------------------------------
// JNI: nativeTokenCount — estimate tokens for a string.
// ---------------------------------------------------------------------------
extern "C" JNIEXPORT jint JNICALL
Java_com_aion_agent_llm_LlamaBridge_nativeTokenCount(JNIEnv *env, jclass, jstring jtext) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return 0;

    const char *text = env->GetStringUTFChars(jtext, nullptr);
    int count = llama_tokenize(g_model, text, strlen(text), nullptr, 0, true, false);
    env->ReleaseStringUTFChars(jtext, text);
    return count;
}
