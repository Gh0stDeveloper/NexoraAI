package com.ghostnexora.ai

object NativeConfig {
    init {
        System.loadLibrary("nexora_config")
    }

    external fun apiBaseUrl(): String
}
