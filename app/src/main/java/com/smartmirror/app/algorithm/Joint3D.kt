package com.smartmirror.app.algorithm

/**
 * 3D 关节点，坐标单位为米（由深度图毫米值转换而来）
 */
data class Joint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val confidence: Float
)