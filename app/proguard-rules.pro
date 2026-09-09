# SmartMirror ProGuard Rules

# MediaPipe 模型类
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Orbbec SDK
-keep class com.orbbec.** { *; }
-dontwarn com.orbbec.**

# GL 相关
-keep class javax.microedition.khronos.** { *; }

# 保留 JNI native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留算法类（JNI 反射使用）
-keep class com.smartmirror.app.algorithm.** { *; }

# 保留数据模型
-keep class com.smartmirror.app.model.** { *; }

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
