package com.example.invisibleinsight

object LlamaCpp {
    init {
        System.loadLibrary("native-lib")
    }

    /**
     * @param modelPath Absolute path to the .gguf model file on the device.
     * @return Pointer to the native context object.
     */
    external fun init(modelPath: String): Long

    /**
     * @param contextPtr Pointer returned by init.
     * @param prompt Text to process.
     * @return Generated text.
     */
    external fun generate(contextPtr: Long, prompt: String): String

    external fun free(contextPtr: Long)
}