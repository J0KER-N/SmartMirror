package com.smartmirror.app.algorithm

/**
 * 人体姿态跟踪接口。
 * 输入对齐的彩色 + 深度帧，输出 3D 关节点列表。
 */
interface IBodyTracker {
    fun update(frame: CombinedFrame): List<Joint3D>
    fun release()
}