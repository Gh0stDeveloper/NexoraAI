-keep class com.ghostnexora.ai.NativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.bouncycastle.**
-dontwarn javax.activation.**
