package com.smartmirror.app.algorithm

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.sqrt

/**
 * 衣物模型驱动。
 * 从 assets 加载 OBJ 模型，接收 [Joint3D] 列表，计算并输出驱动后的顶点数组。
 *
 * 驱动方式（按优先级）：
 * 1. JNI LBS 实现（garment_jni.cpp）— 若 libgarment_jni.so 可用
 * 2. 纯 Kotlin 分块仿射 — JNI 不可用时的回退方案
 *
 * Kotlin 分块仿射逻辑：
 * - 将衣物顶点按 Y 坐标分为躯干、左臂、右臂区域
 * - 每区域根据对应关节计算平移和旋转，应用到该区域顶点
 * - 关节旋转由相邻关节的方向向量确定
 */
class GarmentDriver(private val appContext: Context) {

    companion object {
        private const val TAG = "GarmentDriver"
        private const val DEFAULT_MODEL = "tshirt.obj"

        // 关节索引
        private const val J_NOSE = 0
        private const val J_L_SHOULDER = 1
        private const val J_R_SHOULDER = 2
        private const val J_L_ELBOW = 3
        private const val J_R_ELBOW = 4
        private const val J_L_WRIST = 5
        private const val J_R_WRIST = 6
        private const val J_L_HIP = 7
        private const val J_R_HIP = 8
        private const val J_UPPER_SPINE = 19
        private const val J_MID_SHOULDER = 20
    }

    /** 加载后的原始 OBJ 顶点（T-Pose，3 分量） */
    private val restPoseVertices = mutableListOf<FloatArray>()

    /** 原始法线 */
    private val restPoseNormals = mutableListOf<FloatArray>()

    /** 三角形面（每组 3 个顶点索引） */
    private val faces = mutableListOf<IntArray>()

    /** 当前帧运算后的顶点（输出用） */
    var currentVertices: FloatArray = FloatArray(0)
        private set

    /** 当前帧法线（输出用） */
    var currentNormals: FloatArray = FloatArray(0)
        private set

    /** 每个顶点所属的区域：0=躯干, 1=左臂, 2=右臂 */
    private val vertexRegions = mutableListOf<Int>()

    /** 衣物整体中心 Y（用于区域划分） */
    private var centerY: Float = 0f

    /** JNI 是否可用 */
    private var jniAvailable = false

    init {
        val modelLoaded = loadModel(DEFAULT_MODEL)
        if (!modelLoaded) {
            Log.w(TAG, "OBJ model not found, generating cube fallback")
            generateCubeModel()
            computeRestPoseRegions()
            allocateOutputBuffers()
        }
        initJNI()
    }

    private fun initJNI() {
        if (GarmentJNI.isAvailable) {
            try {
                GarmentJNI.nativeInit(appContext.assets, DEFAULT_MODEL)
                jniAvailable = true
                Log.i(TAG, "JNI garment driver initialized")
            } catch (e: Exception) {
                Log.w(TAG, "JNI init failed, falling back to Kotlin: ${e.message}")
                jniAvailable = false
            }
        } else {
            Log.i(TAG, "JNI not available, using pure Kotlin garment driver")
            jniAvailable = false
        }
    }

    /**
     * 从 assets 加载 OBJ 模型。
     * 只支持顶点 (v)、法线 (vn)、面 (f) 三种行。
     *
     * @return 是否加载成功
     */
    private fun loadModel(modelName: String): Boolean {
        return try {
            val inputStream = appContext.assets.open(modelName)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                when {
                    l.startsWith("v ") -> {
                        val parts = l.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            restPoseVertices.add(floatArrayOf(
                                parts[1].toFloat(),
                                parts[2].toFloat(),
                                parts[3].toFloat()
                            ))
                        }
                    }
                    l.startsWith("vn ") -> {
                        val parts = l.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            restPoseNormals.add(floatArrayOf(
                                parts[1].toFloat(),
                                parts[2].toFloat(),
                                parts[3].toFloat()
                            ))
                        }
                    }
                    l.startsWith("f ") -> {
                        val parts = l.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val face = IntArray(3)
                            for (i in 0..2) {
                                val vi = parts[i + 1].split("/").first().toInt()
                                face[i] = if (vi > 0) vi - 1 else restPoseVertices.size + vi
                            }
                            faces.add(face)
                        }
                    }
                }
            }
            reader.close()
            inputStream.close()

            Log.i(TAG, "Loaded OBJ: ${restPoseVertices.size} vertices, ${faces.size} faces")

            if (restPoseVertices.isEmpty()) {
                return false
            }

            computeRestPoseRegions()
            allocateOutputBuffers()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OBJ: ${e.message}")
            false
        }
    }

    /**
     * 生成立方体占位模型（8 顶点，12 面，单位立方体中心在原点）
     */
    private fun generateCubeModel() {
        restPoseVertices.clear()
        faces.clear()
        restPoseNormals.clear()

        val s = 0.15f // 半边长
        restPoseVertices.addAll(listOf(
            floatArrayOf(-s, -s,  s), floatArrayOf( s, -s,  s),
            floatArrayOf( s,  s,  s), floatArrayOf(-s,  s,  s),
            floatArrayOf(-s, -s, -s), floatArrayOf( s, -s, -s),
            floatArrayOf( s,  s, -s), floatArrayOf(-s,  s, -s),
        ))
        val idx = listOf(
            0,1,2, 0,2,3,   // front
            1,5,6, 1,6,2,   // right
            5,4,7, 5,7,6,   // back
            4,0,3, 4,3,7,   // left
            3,2,6, 3,6,7,   // top
            4,5,1, 4,1,0    // bottom
        )
        for (i in idx.indices step 3) {
            faces.add(intArrayOf(idx[i], idx[i+1], idx[i+2]))
        }
        for (v in restPoseVertices) {
            val len = sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])
            if (len > 0.001f) restPoseNormals.add(floatArrayOf(v[0]/len, v[1]/len, v[2]/len))
            else restPoseNormals.add(floatArrayOf(0f, 0f, 1f))
        }
        Log.i(TAG, "Generated cube fallback: ${restPoseVertices.size} vertices")
    }

    /**
     * 计算 T-Pose 中每个顶点的区域划分。
     */
    private fun computeRestPoseRegions() {
        if (restPoseVertices.isEmpty()) return
        val ys = restPoseVertices.map { it[1] }
        centerY = (ys.min() + ys.max()) / 2f

        val midX = restPoseVertices.map { it[0] }.let { (it.min() + it.max()) / 2f }
        val armThreshold = (restPoseVertices.map { kotlin.math.abs(it[0]) }.maxOrNull() ?: 0.3f) * 0.6f

        vertexRegions.clear()
        for (v in restPoseVertices) {
            // 区域判定只看 |x|：T恤类模型的袖子顶点分布在肩线附近（y 接近模型顶部），
            // 若加 y>centerY 条件会把所有袖子顶点误判为躯干（它们 y < centerY），
            // 导致手臂摆动动画完全无法驱动衣物
            val region = when {
                kotlin.math.abs(v[0] - midX) > armThreshold -> {
                    if (v[0] < midX) 1 else 2
                }
                else -> 0
            }
            vertexRegions.add(region)
        }
        Log.i(TAG, "Region split: torso=${vertexRegions.count { it == 0 }}, " +
                "leftArm=${vertexRegions.count { it == 1 }}, rightArm=${vertexRegions.count { it == 2 }}")
    }

    private fun allocateOutputBuffers() {
        val n = restPoseVertices.size
        currentVertices = FloatArray(n * 3)
        // 法线不逐顶点拷贝：OBJ 的 vn 数量与 v 不一致（非逐顶点对应），
        // 强行对应会产生错误光照。保持全 0，由着色器用屏幕空间导数重建平面法线。
        currentNormals = FloatArray(n * 3)
        for (i in 0 until n) {
            val v = restPoseVertices[i]
            currentVertices[i * 3] = v[0]
            currentVertices[i * 3 + 1] = v[1]
            currentVertices[i * 3 + 2] = v[2]
        }
    }

    /**
     * 根据当前骨骼更新衣服顶点。
     *
     * 优先使用 JNI 路径，不可用时回退到 Kotlin 分块仿射。
     *
     * @param joints 21 个关节的 3D 坐标（米制相机坐标系）
     * @return 更新后的顶点数组 float[n*3]
     */
    fun bindSkeleton(joints: List<Joint3D>): FloatArray {
        if (restPoseVertices.isEmpty() || joints.size < 21) {
            return currentVertices
        }

        // --- 优先 JNI 路径 ---
        if (jniAvailable) {
            try {
                val jointFlat = FloatArray(21 * 3)
                for (i in 0 until 21) {
                    val j = if (i < joints.size) joints[i] else Joint3D(0f, 0f, 0f, 0f)
                    jointFlat[i * 3] = j.x
                    jointFlat[i * 3 + 1] = j.y
                    jointFlat[i * 3 + 2] = j.z
                }
                val result = GarmentJNI.nativeUpdateVertices(jointFlat)
                if (result != null && result.size >= currentVertices.size) {
                    System.arraycopy(result, 0, currentVertices, 0, currentVertices.size)
                    return currentVertices
                }
            } catch (e: Exception) {
                Log.w(TAG, "JNI bindSkeleton failed, falling back to Kotlin: ${e.message}")
            }
        }

        // --- Kotlin 分块仿射回退 ---
        val tPose = MediaPipeBodyTracker.generateMockTPoseJoints()

        // 躯干变换
        val torsoJoints = listOf(J_UPPER_SPINE, J_MID_SHOULDER, J_L_HIP, J_R_HIP)
        val tPoseTorsoCenter = averageJoint(tPose, torsoJoints)
        val currentTorsoCenter = averageJoint(joints, torsoJoints)
        val torsoTranslation = floatArrayOf(
            currentTorsoCenter[0] - tPoseTorsoCenter[0],
            currentTorsoCenter[1] - tPoseTorsoCenter[1],
            currentTorsoCenter[2] - tPoseTorsoCenter[2]
        )

        // 左臂变换
        val lArmTrans = if (joints[J_L_SHOULDER].confidence > 0 && joints[J_L_ELBOW].confidence > 0) {
            val refDir = direction(tPose, J_L_SHOULDER, J_L_ELBOW)
            val curDir = direction(joints, J_L_SHOULDER, J_L_ELBOW)
            val shoulderDelta = delta(joints, tPose, J_L_SHOULDER)
            Triple(shoulderDelta, refDir, curDir)
        } else null

        // 右臂变换
        val rArmTrans = if (joints[J_R_SHOULDER].confidence > 0 && joints[J_R_ELBOW].confidence > 0) {
            val refDir = direction(tPose, J_R_SHOULDER, J_R_ELBOW)
            val curDir = direction(joints, J_R_SHOULDER, J_R_ELBOW)
            val shoulderDelta = delta(joints, tPose, J_R_SHOULDER)
            Triple(shoulderDelta, refDir, curDir)
        } else null

        // 更新顶点
        for (i in restPoseVertices.indices) {
            val v = restPoseVertices[i]
            val region = vertexRegions.getOrElse(i) { 0 }
            var newX = v[0]
            var newY = v[1]
            var newZ = v[2]

            when (region) {
                0 -> {
                    newX += torsoTranslation[0]
                    newY += torsoTranslation[1]
                    newZ += torsoTranslation[2]
                }
                1 -> {
                    if (lArmTrans != null) {
                        newX += lArmTrans.first[0]
                        newY += lArmTrans.first[1]
                        newZ += lArmTrans.first[2]
                        val refLen = vectorLength(lArmTrans.second)
                        val curLen = vectorLength(lArmTrans.third)
                        if (refLen > 0.001f && curLen > 0.001f) {
                            val curDirNorm = normalize(lArmTrans.third)
                            val refOrigin = tPose[J_L_SHOULDER]
                            val dx = v[0] - refOrigin.x
                            val dy = v[1] - refOrigin.y
                            val dz = v[2] - refOrigin.z
                            val scale = curLen / refLen
                            newX = joints[J_L_SHOULDER].x + dx * curDirNorm[0] * scale
                            newY = joints[J_L_SHOULDER].y + dy * curDirNorm[1] * scale
                            newZ = joints[J_L_SHOULDER].z + dz * curDirNorm[2] * scale
                        }
                    } else {
                        newX += torsoTranslation[0]
                        newY += torsoTranslation[1]
                        newZ += torsoTranslation[2]
                    }
                }
                2 -> {
                    if (rArmTrans != null) {
                        newX += rArmTrans.first[0]
                        newY += rArmTrans.first[1]
                        newZ += rArmTrans.first[2]
                        val refLen = vectorLength(rArmTrans.second)
                        val curLen = vectorLength(rArmTrans.third)
                        if (refLen > 0.001f && curLen > 0.001f) {
                            val curDirNorm = normalize(rArmTrans.third)
                            val refOrigin = tPose[J_R_SHOULDER]
                            val dx = v[0] - refOrigin.x
                            val dy = v[1] - refOrigin.y
                            val dz = v[2] - refOrigin.z
                            val scale = curLen / refLen
                            newX = joints[J_R_SHOULDER].x + dx * curDirNorm[0] * scale
                            newY = joints[J_R_SHOULDER].y + dy * curDirNorm[1] * scale
                            newZ = joints[J_R_SHOULDER].z + dz * curDirNorm[2] * scale
                        }
                    } else {
                        newX += torsoTranslation[0]
                        newY += torsoTranslation[1]
                        newZ += torsoTranslation[2]
                    }
                }
            }

            currentVertices[i * 3] = newX
            currentVertices[i * 3 + 1] = newY
            currentVertices[i * 3 + 2] = newZ
        }

        return currentVertices
    }

    /** 获取当前三角形面索引，用于 OpenGL 渲染 */
    fun getFaceIndices(): IntArray {
        val result = IntArray(faces.size * 3)
        for (i in faces.indices) {
            result[i * 3] = faces[i][0]
            result[i * 3 + 1] = faces[i][1]
            result[i * 3 + 2] = faces[i][2]
        }
        return result
    }

    /** 释放资源 */
    fun release() {
        if (jniAvailable) {
            try { GarmentJNI.nativeRelease() } catch (_: Exception) {}
        }
    }

    // ========== 数学工具 ==========

    private fun averageJoint(joints: List<Joint3D>, indices: List<Int>): FloatArray {
        var x = 0f; var y = 0f; var z = 0f; var count = 0
        for (i in indices) {
            if (i < joints.size) {
                x += joints[i].x; y += joints[i].y; z += joints[i].z; count++
            }
        }
        return if (count > 0) floatArrayOf(x / count, y / count, z / count)
        else floatArrayOf(0f, 0f, 0f)
    }

    private fun direction(joints: List<Joint3D>, fromIdx: Int, toIdx: Int): FloatArray {
        if (fromIdx >= joints.size || toIdx >= joints.size) return floatArrayOf(0f, -1f, 0f)
        return floatArrayOf(
            joints[toIdx].x - joints[fromIdx].x,
            joints[toIdx].y - joints[fromIdx].y,
            joints[toIdx].z - joints[fromIdx].z
        )
    }

    private fun delta(current: List<Joint3D>, reference: List<Joint3D>, idx: Int): FloatArray {
        if (idx >= current.size || idx >= reference.size) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            current[idx].x - reference[idx].x,
            current[idx].y - reference[idx].y,
            current[idx].z - reference[idx].z
        )
    }

    private fun vectorLength(v: FloatArray): Float = sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2])

    private fun normalize(v: FloatArray): FloatArray {
        val len = vectorLength(v)
        return if (len > 0.001f) floatArrayOf(v[0]/len, v[1]/len, v[2]/len)
        else floatArrayOf(0f, 0f, 1f)
    }
}
