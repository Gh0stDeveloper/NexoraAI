-keep class com.ghostnexora.ai.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.bouncycastle.**
-dontwarn javax.activation.**
# PDFBox only uses this optional decoder for embedded JPEG 2000 images.
# Nexora extracts PDF text, so the JP2Android dependency is intentionally absent.
-dontwarn com.gemalto.jp2.JP2Decoder
