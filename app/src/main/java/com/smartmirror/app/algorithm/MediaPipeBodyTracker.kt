package com.smartmirror.app.algorithm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import kotlin.math.abs

/**
 * MediaPipe Pose Landmarker 姿态估计 + 深度对齐实现。
 *
 * 流程：
 * 1. 使用 MediaPipe PoseLandmarker 从 RGB 帧检测 33 个归一化 2D 关键点。
 * 2. 筛选出 21 个主要关节。
 * 3. 在每个关键点像素坐标处采样对齐后的深度图，得到毫米深度值。
 * 4. 利用相机内参将 (u, v, depth_mm) 转为以米为单位的 3D 坐标 (x, y, z)，
 *    坐标系：x 向右，y 向下，z 朝前（相机坐标系）。
 *
 * 若 MediaPipe 初始化失败（模型文件缺失等），降级为随机静止骨骼。
 */
class MediaPipeBodyTracker(
    private val appContext: Context
) : IBodyTracker {

    companion object {
        private const val TAG = "MediaPipeBodyTracker"
        private const val MODEL_PATH = "pose_landmarker_lite.task"
        private const val FRAME_WIDTH = 640
        private const val FRAME_HEIGHT = 480

        /** MediaPipe Pose 33 个关键点中我们关注的 21 个主要关节索引 */
        val JOINT_INDICES = intArrayOf(
            0,   // nose
            11,  // left shoulder
            12,  // right shoulder
            13,  // left elbow
            14,  // right elbow
            15,  // left wrist
            16,  // right wrist
            23,  // left hip
            24,  // right hip
            25,  // left knee
            26,  // right knee
            27,  // left ankle
            28,  // right ankle
            5,   // left eye (inner)
            6,   // right eye (inner)
            1,   // left ear
            2,   // right ear
            7,   // left mouth corner
            8,   // right mouth corner
            3,   // upper spine (approximated)
            4    // mid shoulder (approximated)
        )

        /** 21 个关节的友好名称（用于调试） */
        val JOINT_NAMES = arrayOf(
            "nose", "left_shoulder", "right_shoulder", "left_elbow", "right_elbow",
            "left_wrist", "right_wrist", "left_hip", "right_hip", "left_knee",
            "right_knee", "left_ankle", "right_ankle",
            "left_eye", "right_eye", "left_ear", "right_ear",
            "mouth_left", "mouth_right", "upper_spine", "mid_shoulder"
        )

        /** 指数平滑系数 (0~1)，值越小越平滑 */
        private const val SMOOTHING_FACTOR = 0.6f

        /**
         * 生成一个 T-Pose 的 21 个关节（米制相机坐标）
         * 坐标系：x 向右，y 向下，z 朝前
         * 身高约 1.7m，臂展开约 1.6m
         */
        fun generateMockTPoseJoints(): List<Joint3D> {
            val armSpan = 0.8f
            val shoulderY = -0.15f
            val hipY = 0.4f
            val kneeY = 0.85f
            val ankleY = 1.3f
            val centerX = 0f
            val depthZ = 1.5f
            val upperArm = 0.3f
            val forearm = 0.28f

            return listOf(
                Joint3D(centerX, 0f, depthZ, 1f),                        // 0: nose
                Joint3D(-armSpan, shoulderY, depthZ, 1f),                 // 1: left shoulder
                Joint3D(armSpan, shoulderY, depthZ, 1f),                  // 2: right shoulder
                Joint3D(-armSpan - upperArm, shoulderY + 0.05f, depthZ, 1f), // 3: left elbow
                Joint3D(armSpan + upperArm, shoulderY + 0.05f, depthZ, 1f),  // 4: right elbow
                Joint3D(-armSpan - upperArm - forearm, shoulderY + 0.1f, depthZ, 1f), // 5: left wrist
                Joint3D(armSpan + upperArm + forearm, shoulderY + 0.1f, depthZ, 1f),  // 6: right wrist
                Joint3D(-0.15f, hipY, depthZ, 1f),                        // 7: left hip
                Joint3D(0.15f, hipY, depthZ, 1f),                         // 8: right hip
                Joint3D(-0.15f, kneeY, depthZ, 1f),                       // 9: left knee
                Joint3D(0.15f, kneeY, depthZ, 1f),                        // 10: right knee
                Joint3D(-0.15f, ankleY, depthZ, 1f),                      // 11: left ankle
                Joint3D(0.15f, ankleY, depthZ, 1f),                       // 12: right ankle
                Joint3D(-0.03f, -0.05f, depthZ, 1f),                      // 13: left eye
                Joint3D(0.03f, -0.05f, depthZ, 1f),                       // 14: right eye
                Joint3D(-0.08f, -0.02f, depthZ, 1f),                      // 15: left ear
                Joint3D(0.08f, -0.02f, depthZ, 1f),                       // 16: right ear
                Joint3D(-0.05f, 0.05f, depthZ, 1f),                       // 17: mouth left
                Joint3D(0.05f, 0.05f, depthZ, 1f),                        // 18: mouth right
                Joint3D(centerX, -0.1f, depthZ, 1f),                      // 19: upper spine (neck base)
                Joint3D(centerX, shoulderY, depthZ, 1f)                   // 20: mid shoulder
            )
        }
    }

    // --- MediaPipe 组件 ---
    private var poseLandmarker: PoseLandmarker? = null
    private var mediaPipeAvailable: Boolean = false

    // --- 最新检测结果 ---
    @Volatile
    private var latestResult: PoseLandmarkerResult? = null

    // --- 上次帧的关节（用于平滑和降级） ---
    private val previousJoints = MutableList(21) { Joint3D(0f, 0f, 0f, 0f) }

    // --- 模拟关节数据（降级时使用） ---
    private val mockJoints = generateMockTPoseJoints()

    // --- 相机内参（Orbbec Gemini 2 RGB 640x480 典型值，后续可从 SDK 读取） ---
    private var fx: Float = 620.0f
    private var fy: Float = 620.0f
    private var cx: Float = 320.0f
    private var cy: Float = 240.0f

    init {
        try {
            initMediaPipe()
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe init failed, will use static skeleton", e)
            mediaPipeAvailable = false
        }
    }

    private fun initMediaPipe() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .setDelegate(Delegate.GPU)
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    latestResult = result
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(appContext, options)
            mediaPipeAvailable = true
            Log.i(TAG, "MediaPipe PoseLandmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe: ${e.message}. Download pose_landmarker_lite.task to assets/")
            mediaPipeAvailable = false
        }
    }

    override fun update(frame: CombinedFrame): List<Joint3D> {
        return if (mediaPipeAvailable && poseLandmarker != null) {
            processMediaPipe(frame)
        } else {
            // 降级：返回静态 T-Pose + 轻微摆动模拟呼吸
            getDegradedJoints(frame.timestamp)
        }
    }

    /**
     * MediaPipe 推理 + 深度对齐主流程
     */
    private fun processMediaPipe(frame: CombinedFrame): List<Joint3D> {
        // 1. 将 ByteBuffer (RGB888) 转为 MediaPipe MPImage
        val mpImage = convertToMPImage(frame.color, frame.width, frame.height)
        if (mpImage == null) {
            return getDegradedJoints(frame.timestamp)
        }

        // 2. 异步推理（LIVE_STREAM 模式，结果通过 ResultListener 回调）
        poseLandmarker?.detectAsync(mpImage, frame.timestamp)

        // 3. 获取最新结果
        val result = latestResult ?: return getDegradedJoints(frame.timestamp)

        if (result.landmarks().isEmpty()) {
            return getDegradedJoints(frame.timestamp)
        }

        val landmarks = result.landmarks()[0] // 取第一个人

        // 4. 从 33 个关键点中提取 21 个关节 + 深度对齐
        val joints = mutableListOf<Joint3D>()
        for (i in JOINT_INDICES.indices) {
            val mpIdx = JOINT_INDICES[i]
            if (mpIdx < landmarks.size) {
                val lm = landmarks[mpIdx]
                // MediaPipe 归一化坐标 [0,1] → 像素坐标
                val u = lm.x() * frame.width
                val v = lm.y() * frame.height

                // 从深度图中采样该位置的深度值
                val depthMm = sampleDepth(
                    frame.depth, u.toInt(), v.toInt(),
                    frame.width, frame.height
                )

                // 转换为 3D 坐标
                val joint3D = if (depthMm > 0) {
                    uvDepthTo3D(u, v, depthMm, frame.width, frame.height)
                } else {
                    Joint3D(
                        (u - cx) * 1.5f / fx,
                        (v - cy) * 1.5f / fy,
                        1.5f,
                        lm.visibility().orElse(0.5f)
                    )
                }

                // 指数平滑
                val prev = previousJoints[i]
                val smoothed = if (prev.confidence > 0f) {
                    Joint3D(
                        prev.x * (1f - SMOOTHING_FACTOR) + joint3D.x * SMOOTHING_FACTOR,
                        prev.y * (1f - SMOOTHING_FACTOR) + joint3D.y * SMOOTHING_FACTOR,
                        prev.z * (1f - SMOOTHING_FACTOR) + joint3D.z * SMOOTHING_FACTOR,
                        joint3D.confidence
                    )
                } else {
                    joint3D
                }
                previousJoints[i] = smoothed
                joints.add(smoothed)
            } else {
                joints.add(previousJoints[i])
            }
        }

        return joints
    }

    /**
     * 将 RGB888 ByteBuffer 转为 MediaPipe MPImage
     */
    private fun convertToMPImage(colorBuffer: ByteBuffer, width: Int, height: Int): MPImage? {
        return try {
            // 创建 Bitmap 作为中间格式
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)

            // 确保 position 在开头
            val buf = colorBuffer.duplicate()
            buf.rewind()

            for (i in pixels.indices) {
                val r = buf.get().toInt() and 0xFF
                val g = buf.get().toInt() and 0xFF
                val b = buf.get().toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            BitmapImageBuilder(bitmap).build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert frame to MPImage", e)
            null
        }
    }

    /**
     * 将 2D 关键点 + 深度值转换为 3D 相机坐标。
     * 针孔模型：x = (u - cx) * z / fx,  y = (v - cy) * z / fy,  z = depth_mm / 1000
     */
    private fun uvDepthTo3D(u: Float, v: Float, depthMm: Short, width: Int, height: Int): Joint3D {
        val depthM = depthMm.toFloat() / 1000f
        if (depthM <= 0.001f || depthM > 10f) {
            return Joint3D(0f, 0f, 0f, 0f) // 无效深度
        }
        val x = (u - cx) * depthM / fx
        val y = (v - cy) * depthM / fy
        return Joint3D(x, y, depthM, 1f)
    }

    /**
     * 从深度图中读取指定 (u, v) 处的毫米深度值。
     * 若该点无效（0），尝试用周围 3x3 中值替代。
     */
    private fun sampleDepth(depth: ShortBuffer, u: Int, v: Int, width: Int, height: Int): Short {
        if (u < 0 || u >= width || v < 0 || v >= height) return 0
        val idx = v * width + u
        val d = if (idx < depth.capacity()) depth.get(idx) else 0
        if (d > 0) return d

        // 3x3 中值回退
        val neighbors = mutableListOf<Short>()
        for (dy in -1..1) {
            for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nu = u + dx
                val nv = v + dy
                if (nu in 0 until width && nv in 0 until height) {
                    val nIdx = nv * width + nu
                    if (nIdx < depth.capacity()) {
                        val nd = depth.get(nIdx)
                        if (nd > 0) neighbors.add(nd)
                    }
                }
            }
        }
        return if (neighbors.isNotEmpty()) {
            neighbors.sorted()[neighbors.size / 2]
        } else 0
    }

    /**
     * 降级模式：生成轻微摆动的静态 T-Pose（用于开发调试）
     */
    private fun getDegradedJoints(timestamp: Long): List<Joint3D> {
        val t = (timestamp and 0xFFFFFFFFL).toFloat() * 0.001f
        val breathe = kotlin.math.sin(t * 2.0f) * 0.01f

        return mockJoints.mapIndexed { i: Int, joint: Joint3D ->
            val isTorso = i in listOf(0, 1, 2, 7, 8, 17, 18, 19)
            val offset = if (isTorso) breathe else 0f

            val prev = previousJoints[i]
            val smoothed = if (prev.confidence > 0f) {
                Joint3D(
                    prev.x * (1f - SMOOTHING_FACTOR) + (joint.x + offset) * SMOOTHING_FACTOR,
                    prev.y * (1f - SMOOTHING_FACTOR) + (joint.y + offset) * SMOOTHING_FACTOR,
                    prev.z * (1f - SMOOTHING_FACTOR) + (joint.z + offset) * SMOOTHING_FACTOR,
                    0.5f
                )
            } else {
                Joint3D(joint.x + offset, joint.y + offset, joint.z, 0.5f)
            }
            previousJoints[i] = smoothed
            smoothed
        }
    }

    override fun release() {
        try {
            poseLandmarker?.close()
        } catch (_: Exception) {}
        poseLandmarker = null
    }
}
