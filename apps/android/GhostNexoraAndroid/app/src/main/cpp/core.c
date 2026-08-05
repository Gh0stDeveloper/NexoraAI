#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

static const uint8_t nx_key[] = {0x39, 0xA7, 0x5D, 0xC3, 0x18, 0x6E, 0xD1, 0x42};

#if NEXORA_DEBUG_BUILD
static const uint8_t nx_origin[] = {
    82, 217, 216, 75, 65, 91, 237, 7, 114, 151,
    100, 253, 81, 34, 230, 4, 127, 237, 108, 251
};
#else
static const uint8_t nx_origin[] = {
    82, 217, 216, 75, 138, 46, 237, 1, 69, 213, 47, 180,
    103, 227, 169, 194, 184, 216, 36, 180, 153, 25, 175, 47,
    0, 209, 211, 160, 106, 28, 82, 205, 8, 222, 222, 188
};
#endif

static const uint8_t nx_chat_route[] = {
    11, 204, 220, 66, 86, 29, 173, 52, 189, 201, 51, 252,
    98, 232, 183, 194, 123, 42, 40, 185, 108, 25, 91
};
static const uint8_t nx_client_header[] = {
    98, 144, 2, 190, 143, 27, 174, 55, 113, 226, 40, 186, 100, 226, 168
};
static const uint8_t nx_version_header[] = {
    98, 144, 2, 190, 143, 27, 174, 55, 113, 255, 51, 65, 146, 233, 165, 56
};

static jstring nx_decode(JNIEnv *env, const uint8_t *source, size_t length) {
    char *decoded = (char *) calloc(length + 1U, sizeof(char));
    if (decoded == NULL) return (*env)->NewStringUTF(env, "");

    for (size_t index = 0; index < length; ++index) {
        const uint8_t shift = (uint8_t) ((index * 7U + 3U) & 0x1FU);
        const uint8_t value = source[index] ^ nx_key[index % sizeof(nx_key)];
        decoded[index] = (char) (value - shift);
    }
    decoded[length] = '\0';

    jstring result = (*env)->NewStringUTF(env, decoded);
    volatile char *wipe = decoded;
    for (size_t index = 0; index < length; ++index) wipe[index] = 0;
    free(decoded);
    return result;
}

static jstring nx_api_origin(JNIEnv *env, jobject instance) {
    (void) instance;
    return nx_decode(env, nx_origin, sizeof(nx_origin));
}

static jstring nx_chat_path(JNIEnv *env, jobject instance) {
    (void) instance;
    return nx_decode(env, nx_chat_route, sizeof(nx_chat_route));
}

static jstring nx_client_header_name(JNIEnv *env, jobject instance) {
    (void) instance;
    return nx_decode(env, nx_client_header, sizeof(nx_client_header));
}

static jstring nx_version_header_name(JNIEnv *env, jobject instance) {
    (void) instance;
    return nx_decode(env, nx_version_header, sizeof(nx_version_header));
}

static const JNINativeMethod nx_methods[] = {
    {"apiOrigin", "()Ljava/lang/String;", (void *) nx_api_origin},
    {"chatPath", "()Ljava/lang/String;", (void *) nx_chat_path},
    {"clientHeaderName", "()Ljava/lang/String;", (void *) nx_client_header_name},
    {"versionHeaderName", "()Ljava/lang/String;", (void *) nx_version_header_name},
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass bridge = (*env)->FindClass(env, "com/ghostnexora/ai/NativeBridge");
    if (bridge == NULL) return JNI_ERR;

    const jint method_count = (jint) (sizeof(nx_methods) / sizeof(nx_methods[0]));
    if ((*env)->RegisterNatives(env, bridge, nx_methods, method_count) != JNI_OK) {
        (*env)->DeleteLocalRef(env, bridge);
        return JNI_ERR;
    }
    (*env)->DeleteLocalRef(env, bridge);
    return JNI_VERSION_1_6;
}
