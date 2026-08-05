#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#define XOR_KEY 0x5A

#if NEXORA_DEBUG_BUILD
static const uint8_t encoded_url[] = {
    50, 46, 46, 42, 96, 117, 117, 107, 106, 116, 106, 116, 104, 116, 104, 96, 105, 106, 106, 106, 117
};
#else
static const uint8_t encoded_url[] = {
    50, 46, 46, 42, 41, 96, 117, 117, 59, 42, 51, 61, 50, 53, 41, 46, 52, 63, 34, 53, 40, 59, 59, 51, 116, 62, 47, 57, 49, 62, 52, 41, 116, 53, 40, 61, 117
};
#endif

JNIEXPORT jstring JNICALL
Java_com_ghostnexora_ai_NativeConfig_apiBaseUrl(JNIEnv *env, jobject thiz) {
    (void) thiz;
    const size_t length = sizeof(encoded_url) / sizeof(encoded_url[0]);
    char *decoded = (char *) calloc(length + 1, sizeof(char));
    if (decoded == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    for (size_t index = 0; index < length; ++index) {
        decoded[index] = (char) (encoded_url[index] ^ XOR_KEY);
    }
    decoded[length] = '\0';

    jstring result = (*env)->NewStringUTF(env, decoded);
    for (size_t index = 0; index < length; ++index) {
        decoded[index] = 0;
    }
    free(decoded);
    return result;
}
