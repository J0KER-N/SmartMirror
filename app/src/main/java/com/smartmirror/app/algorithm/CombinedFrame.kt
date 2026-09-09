package com.smartmirror.app.algorithm

import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * 一帧对齐后的彩色 + 深度数据。
 * color 为 RGB888 格式（ByteBuffer），depth 为 16 位毫米值（ShortBuffer），
 * 两者分辨率相同（经 Orbbec 对齐后均为 640x480）。
 */
data class CombinedFrame(
    val color: ByteBuffer,
    val depth: ShortBuffer,
    val width: Int,
    val height: Int,
    val timestamp: Long
) {
    val size: Int get() = width * height
}