package com.smartmirror.app.algorithm

import kotlin.math.cos
import kotlin.math.sin

/**
 * 模拟关节提供器。
 * 生成抬手动作序列：右臂从下垂缓慢举起到水平再放下，周期循环。
 *
 * 可用于跳过 MediaPipe 直接测试 GarmentDriver + TryOnRenderer。
 */
object MockJointProvider {

    private const val TAG = "MockJointProvider"

    /** 抬手周期时长（秒） */
    private const val CYCLE_SECONDS = 4.0f

    /**
     * 基于时间戳生成当前帧的 21 个关节。
     * 以 T-Pose 为基础，右臂做抬手动画，左臂轻微摆动。
     *
     * @param timestamp 帧时间戳（System.nanoTime()）
     * @return 21 个 [Joint3D]
     */
    fun generate(timestamp: Long): List<Joint3D> {
        val t = (timestamp and 0xFFFFFFFFL).toFloat() * 1e-9f
        val phase = (t % CYCLE_SECONDS) / CYCLE_SECONDS * 2f * kotlin.math.PI.toFloat()

        // phase: 0→π: 右臂从下垂（-90°）举起到水平（0°）；π→2π: 放下
        val rightArmAngle = cos(phase) * (kotlin.math.PI.toFloat() / 2f) - kotlin.math.PI.toFloat() / 2f
        // 左臂轻微跟随摆动（幅度小一些）
        val leftArmAngle = sin(phase * 0.7f) * 0.3f - 0.5f

        return buildAnimatedJoints(rightArmAngle, leftArmAngle)
    }

    /**
     * 用指定的臂部角度构建完整 21 关节。
     *
     * @param rightArmAngle 右臂上抬角度（弧度），0=水平，-π/2=垂直下垂
     * @param leftArmAngle  左臂摆动角度
     */
    fun buildAnimatedJoints(rightArmAngle: Float, leftArmAngle: Float): List<Joint3D> {
        val depthZ = 1.5f

        // T-Pose 参考数据
        val shoulderY = -0.15f
        val armSpan = 0.8f
        val upperArmLen = 0.3f
        val forearmLen = 0.28f
        val hipY = 0.4f
        val kneeY = 0.85f
        val ankleY = 1.3f
        val centerX = 0f
        val neckY = -0.1f

        // 右肩位置
        val rShoulder = Joint3D(armSpan, shoulderY, depthZ, 1f)
        // 左肩位置
        val lShoulder = Joint3D(-armSpan, shoulderY, depthZ, 1f)

        // 右臂：肩 → 肘 → 腕，沿抬手角度旋转
        // 右肘：肩 + 上臂长度 * (sin(angle), -cos(angle), 0)   [angle=0 时水平向前→改为水平向右]
        val rElbow = Joint3D(
            x = rShoulder.x + upperArmLen * cos(rightArmAngle),
            y = rShoulder.y + upperArmLen * sin(rightArmAngle),
            z = depthZ,
            confidence = 1f
        )
        // 右腕：肘 + 前臂长度，沿相同方向
        val rWrist = Joint3D(
            x = rElbow.x + forearmLen * cos(rightArmAngle),
            y = rElbow.y + forearmLen * sin(rightArmAngle),
            z = depthZ,
            confidence = 1f
        )

        // 左臂：轻微摆动
        val lElbow = Joint3D(
            x = lShoulder.x + upperArmLen * cos(leftArmAngle),
            y = lShoulder.y + upperArmLen * sin(leftArmAngle),
            z = depthZ,
            confidence = 1f
        )
        val lWrist = Joint3D(
            x = lElbow.x + forearmLen * cos(leftArmAngle),
            y = lElbow.y + forearmLen * sin(leftArmAngle),
            z = depthZ,
            confidence = 1f
        )

        return listOf(
            // 0: nose
            Joint3D(centerX, 0f, depthZ, 1f),
            // 1: left shoulder
            lShoulder,
            // 2: right shoulder
            rShoulder,
            // 3: left elbow
            lElbow,
            // 4: right elbow
            rElbow,
            // 5: left wrist
            lWrist,
            // 6: right wrist
            rWrist,
            // 7: left hip
            Joint3D(-0.15f, hipY, depthZ, 1f),
            // 8: right hip
            Joint3D(0.15f, hipY, depthZ, 1f),
            // 9: left knee
            Joint3D(-0.15f, kneeY, depthZ, 1f),
            // 10: right knee
            Joint3D(0.15f, kneeY, depthZ, 1f),
            // 11: left ankle
            Joint3D(-0.15f, ankleY, depthZ, 1f),
            // 12: right ankle
            Joint3D(0.15f, ankleY, depthZ, 1f),
            // 13: left eye
            Joint3D(-0.03f, -0.05f, depthZ, 1f),
            // 14: right eye
            Joint3D(0.03f, -0.05f, depthZ, 1f),
            // 15: left ear
            Joint3D(-0.08f, -0.02f, depthZ, 1f),
            // 16: right ear
            Joint3D(0.08f, -0.02f, depthZ, 1f),
            // 17: mouth left
            Joint3D(-0.05f, 0.05f, depthZ, 1f),
            // 18: mouth right
            Joint3D(0.05f, 0.05f, depthZ, 1f),
            // 19: upper spine
            Joint3D(centerX, neckY, depthZ, 1f),
            // 20: mid shoulder
            Joint3D(centerX, shoulderY, depthZ, 1f)
        )
    }
}