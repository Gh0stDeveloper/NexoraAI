package com.ghostnexora.ai

internal object NativeBridge {
    init {
        System.loadLibrary("nexora")
    }

    external fun apiOrigin(): String
    external fun chatPath(): String
    external fun clientHeaderName(): String
    external fun versionHeaderName(): String
}
