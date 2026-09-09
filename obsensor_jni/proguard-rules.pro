# obsensor_jni ProGuard Rules
-keep class com.orbbec.obsensor.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
