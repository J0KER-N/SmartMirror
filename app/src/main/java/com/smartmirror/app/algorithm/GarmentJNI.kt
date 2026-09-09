package com.smartmirror.app.algorithm

import android.util.Log

/**
 * JNI 桥接类，对应 garment_jni.cpp 中的原生函数。
 *
 * 库名：garment_jni（由 app/src/main/cpp/CMakeLists.txt 编译生成 libgarment_jni.so）
 *
 * 如果库加载失败，所有 native 方法调用安全返回 null/无操作，
 * GarmentDriver 会自动降级为 Kotlin 分块仿射实现。
 */
object GarmentJNI {

    private const val TAG = "GarmentJNI"

    /** JNI 库是否加载成功 */
    val isAvailable: Boolean

    init {
        isAvailable = try {
            System.loadLibrary("garment_jni")
            Log.i(TAG, "Native library loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library (libgarment_jni.so) not available, will use Kotlin fallback. " +
                "Build the native module with: ./gradlew :app:externalNativeBuildDebug")
            false
        }
    }

    /** 初始化：加载 OBJ 模型，预计算骨骼绑定 */
    @JvmStatic
    external fun nativeInit(assetManager: android.content.res.AssetManager, modelPath: String)

    /** 更新顶点：输入 21*3 float 关节数组，输出 n*3 float 顶点数组 */
    @JvmStatic
    external fun nativeUpdateVertices(joints: FloatArray): FloatArray?

    /** 释放资源 */
    @JvmStatic
    external fun nativeRelease()
}
