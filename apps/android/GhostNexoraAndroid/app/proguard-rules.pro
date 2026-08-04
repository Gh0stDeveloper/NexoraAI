-keep class com.ghostnexora.ai.NativeConfig { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.bouncycastle.**
-dontwarn javax.activation.**
