#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <cstring>
#include "llama.h"
#include "common.h" // From llama.cpp/common

struct LlamaContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
};

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_invisibleinsight_LlamaCpp_init(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    auto *wrapper = new LlamaContext();
    
    // Load Model
    llama_model_params model_params = llama_model_default_params();
    wrapper->model = llama_model_load_from_file(path, model_params);

    if (!wrapper->model) {
        delete wrapper;
        return 0;
    }

    // Init Context
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048; 
    wrapper->ctx = llama_init_from_model(wrapper->model, ctx_params);

    env->ReleaseStringUTFChars(modelPath, path);
    return (jlong) wrapper;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_invisibleinsight_LlamaCpp_generate(JNIEnv *env, jobject, jlong contextPtr, jstring prompt) {
    auto *wrapper = (LlamaContext *) contextPtr;
    if (!wrapper || !wrapper->ctx || !wrapper->model) return env->NewStringUTF("");

    const char *text_str = env->GetStringUTFChars(prompt, nullptr);
    int text_len = strlen(text_str);
    
    std::vector<llama_token> tokens_list(text_len + 2);
    
    // Get Vocab from Model (New API requirement)
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);
    
    // llama_tokenize(vocab, text, len, out_buf, max_tokens, add_special, parse_special)
    int n_tokens = llama_tokenize(vocab, text_str, text_len, tokens_list.data(), tokens_list.size(), true, false);
    
    if (n_tokens < 0) {
        tokens_list.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, text_str, text_len, tokens_list.data(), tokens_list.size(), true, false);
    }
    
    if (n_tokens >= 0) {
        tokens_list.resize(n_tokens);
    } else {
        env->ReleaseStringUTFChars(prompt, text_str);
        return env->NewStringUTF("Error: Tokenization failed");
    }

    // llama_batch_get_one(tokens, n_tokens)
    llama_batch batch = llama_batch_get_one(tokens_list.data(), tokens_list.size());
    
    llama_decode(wrapper->ctx, batch);

    // Generate
    auto * sparams = llama_sampler_init_greedy();
    llama_token new_token_id = llama_sampler_sample(sparams, wrapper->ctx, -1);
    
    // Convert result back to string (common_token_to_piece uses context which links to vocab internally)
    std::string result = common_token_to_piece(wrapper->ctx, new_token_id);

    llama_sampler_free(sparams);

    env->ReleaseStringUTFChars(prompt, text_str);
    
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_invisibleinsight_LlamaCpp_free(JNIEnv *, jobject, jlong contextPtr) {
    auto *wrapper = (LlamaContext *) contextPtr;
    if (wrapper) {
        if (wrapper->ctx) llama_free(wrapper->ctx);
        if (wrapper->model) llama_model_free(wrapper->model);
        delete wrapper;
    }
}