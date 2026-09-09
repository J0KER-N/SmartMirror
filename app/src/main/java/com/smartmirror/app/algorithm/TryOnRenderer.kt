package com.smartmirror.app.algorithm

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3.0 渲染器。
 * 功能：
 * 1. 绘制摄像头 RGB 纹理作为全屏背景
 * 2. 绘制驱动物件模型（从 GarmentDriver 获取顶点）
 * 3. 深度遮挡处理（通过深度纹理比较丢弃被身体遮挡的衣物片段）
 *
 * 线程安全：外部线程调用 updateGarment/updateCameraFrame，
 * 内部使用 volatile 标记，在 onDrawFrame 中原子读取。
 */
class TryOnRenderer(
    private val appContext: Context
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "TryOnRenderer"
    }

    // ==================== 状态标记 ====================
    @Volatile
    var hasCamera: Boolean = false

    /** 真实深度数据可用（真实相机模式才启用着色器深度遮挡测试） */
    @Volatile
    var hasDepthData: Boolean = false

    @Volatile
    private var frameBitmap: Bitmap? = null

    /** 至少成功上传过一帧摄像头纹理后置 true（uHasCamera 用，避免帧消费间隙闪烁） */
    @Volatile
    private var cameraTextureReady: Boolean = false

    @Volatile
    private var garmentVertices: FloatArray? = null

    @Volatile
    private var garmentNormals: FloatArray? = null

    private var faceIndices: IntArray = intArrayOf()

    // ==================== 着色器程序 ====================
    private var bgProgram: Int = 0
    private var garmentProgram: Int = 0

    // ==================== 背景四边形 ====================
    private val bgVertices = floatArrayOf(
        -1f, -1f,  0f, 0f,  // bottom-left
         1f, -1f,  1f, 0f,  // bottom-right
        -1f,  1f,  0f, 1f,  // top-left
         1f,  1f,  1f, 1f   // top-right
    )
    private val bgIndices = byteArrayOf(0, 1, 2, 1, 3, 2)
    private var bgVao: Int = 0
    private var bgVbo: Int = 0
    private var bgIbo: Int = 0

    // ==================== 衣物模型 ====================
    private var garmentVao: Int = 0
    private var garmentVbo: Int = 0
    private var garmentNbo: Int = 0
    private var garmentIbo: Int = 0
    private var garmentIndexCount: Int = 0

    // ==================== 纹理 ====================
    private var cameraTextureId: Int = 0
    private var garmentTextureId: Int = 0
    private var depthTextureId: Int = 0

    // ==================== 矩阵 ====================
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // ==================== 视口 ====================
    private var viewportW: Int = 640
    private var viewportH: Int = 480

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.15f, 0.15f, 0.15f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        // OBJ 顶点环绕方向未知，剔除可能把可见面全部剔掉 — 先关闭
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        // 纹理
        cameraTextureId = createTexture()
        depthTextureId = createDepthTexture(640, 480)
        garmentTextureId = createSolidColorTexture(0.2f, 0.5f, 0.9f)

        // 着色器
        bgProgram = createProgram(
            loadShaderSource("camera_bg_vs.glsl"),
            loadShaderSource("camera_bg_fs.glsl")
        )
        garmentProgram = createProgram(
            loadShaderSource("garment_vs.glsl"),
            loadShaderSource("garment_fs.glsl")
        )

        // VAO/VBO
        setupBackgroundQuad()
        setupGarmentBuffers()

        // 视图矩阵：相机位于原点看向 +Z
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 2.5f,   // eye
            0f, 0f, 0f,     // center
            0f, 1f, 0f)     // up
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportW = width
        viewportH = height
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 10f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 上传摄像头纹理（如果有新帧）
        uploadCameraTexture()

        // 1. 绘制摄像头背景
        drawBackground()

        // 2. 绘制衣物（含深度遮挡）
        drawGarment()
    }

    // ==================== 背景绘制 ====================

    private fun drawBackground() {
        GLES30.glUseProgram(bgProgram)
        GLES30.glBindVertexArray(bgVao)

        // 绑定摄像头纹理到单元 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cameraTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(bgProgram, "uCameraTexture"), 0)

        // hasCamera 标志（着色器用此决定显示摄像头纹理还是灰色背景）
        GLES30.glUniform1f(GLES30.glGetUniformLocation(bgProgram, "uHasCamera"),
            if (hasCamera && cameraTextureReady) 1f else 0f)

        // 背景四边形位于 NDC z=0（深度 0.5），比场景中的衣物（深度~0.97）更近；
        // 若写入深度会把衣物整体剔除。背景只贡献颜色，必须关闭深度写入。
        GLES30.glDepthMask(false)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_BYTE, 0)
        GLES30.glDepthMask(true)
        GLES30.glBindVertexArray(0)
    }

    // ==================== 衣物绘制 ====================

    private fun drawGarment() {
        val verts = garmentVertices ?: return
        val norms = garmentNormals
        if (faceIndices.isEmpty()) return

        // 更新顶点 VBO
        val vertBuf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertBuf.put(verts)
        vertBuf.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, garmentVbo)
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertBuf.capacity() * 4, vertBuf)

        // 更新法线 VBO
        if (norms != null && norms.isNotEmpty()) {
            val normBuf = ByteBuffer.allocateDirect(norms.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            normBuf.put(norms)
            normBuf.flip()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, garmentNbo)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, normBuf.capacity() * 4, normBuf)
        }

        // 更新索引 VBO
        if (garmentIndexCount != faceIndices.size) {
            garmentIndexCount = faceIndices.size
            val idxBuf = ByteBuffer.allocateDirect(faceIndices.size * 4)
                .order(ByteOrder.nativeOrder()).asIntBuffer()
            idxBuf.put(faceIndices)
            idxBuf.flip()
            // 在 VAO 绑定状态下更新 ELEMENT 缓冲（VAO 已在 setupGarmentBuffers 绑定 garmentIbo）
            GLES30.glBindVertexArray(garmentVao)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, garmentIbo)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idxBuf.capacity() * 4, idxBuf, GLES30.GL_DYNAMIC_DRAW)
            GLES30.glBindVertexArray(0)
        }

        // 计算 MVP
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, projectionMatrix, 0)
        // 实际上应该是 projection * view = mvp (without model since garment is in world coords)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.invertM(normalMatrix, 0, viewMatrix, 0)
        // normalMatrix 的转置逆（简化：直接使用 view 矩阵的逆转置）

        GLES30.glUseProgram(garmentProgram)
        GLES30.glBindVertexArray(garmentVao)

        // 矩阵 uniform
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(garmentProgram, "uMVPMatrix"), 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(garmentProgram, "uNormalMatrix"), 1, false, normalMatrix, 0)

        // 颜色纹理（单元 0）
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, garmentTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(garmentProgram, "uColorTexture"), 0)

        // 深度纹理（单元 1）— 用于遮挡测试
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(garmentProgram, "uDepthTexture"), 1)

        // 深度纹理尺寸
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(garmentProgram, "uDepthTextureSize"),
            640f, 480f)

        // 投影矩阵（用于 NDC 深度比较）
        GLES30.glUniformMatrix4fv(
            GLES30.glGetUniformLocation(garmentProgram, "uProjectionMatrix"),
            1, false, projectionMatrix, 0)

        // 光照方向
        GLES30.glUniform3f(
            GLES30.glGetUniformLocation(garmentProgram, "uLightDir"),
            -0.5f, 1.0f, 0.5f)

        // 透明度
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(garmentProgram, "uOpacity"), 0.85f)

        // 是否启用深度遮挡（仅真实深度相机模式下启用；mock 深度纹理为全 1.0 无穷远）
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(garmentProgram, "uHasDepth"),
            if (hasDepthData) 1f else 0f)

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, garmentIndexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    // ==================== 外部更新接口 ====================

    /**
     * 更新摄像头帧（由 CameraManager 线程调用）。
     * 在 onDrawFrame 中上传到 OpenGL 纹理。
     */
    fun updateCameraFrame(bitmap: Bitmap) {
        hasCamera = true
        frameBitmap = bitmap
    }

    /**
     * 更新衣物顶点（由推理线程调用）。
     */
    fun updateGarment(vertices: FloatArray, normals: FloatArray, faces: IntArray) {
        garmentVertices = vertices
        garmentNormals = normals
        faceIndices = faces
    }

    // ==================== 纹理上传 ====================

    private fun uploadCameraTexture() {
        val bitmap = frameBitmap ?: return
        if (bitmap.isRecycled) return

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, cameraTextureId)
        android.opengl.GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        cameraTextureReady = true

        frameBitmap = null // 消费后清除
    }

    // ==================== 初始化辅助 ====================

    private fun setupBackgroundQuad() {
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        bgVao = vaos[0]

        val vbos = IntArray(2)
        GLES30.glGenBuffers(2, vbos, 0)
        bgVbo = vbos[0]
        bgIbo = vbos[1]

        GLES30.glBindVertexArray(bgVao)

        val vertBuf = ByteBuffer.allocateDirect(bgVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertBuf.put(bgVertices)
        vertBuf.flip()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertBuf.capacity() * 4, vertBuf, GLES30.GL_STATIC_DRAW)

        val idxBuf = ByteBuffer.allocateDirect(bgIndices.size)
            .order(ByteOrder.nativeOrder())
        idxBuf.put(bgIndices)
        idxBuf.flip()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, bgIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idxBuf.capacity(), idxBuf, GLES30.GL_STATIC_DRAW)

        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(0)
        // aTexCoord 在 createProgram 中被绑定到 location 2（与衣物着色器统一），
        // 这里必须喂给 location 2。喂到 1 会导致着色器读到未启用的常量属性 (0,0)，
        // 整个背景采样同一个纹素（左下角）
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glEnableVertexAttribArray(2)

        GLES30.glBindVertexArray(0)
    }

    private fun setupGarmentBuffers() {
        val vaos = IntArray(1)
        GLES30.glGenVertexArrays(1, vaos, 0)
        garmentVao = vaos[0]

        val vbos = IntArray(3)
        GLES30.glGenBuffers(3, vbos, 0)
        garmentVbo = vbos[0]
        garmentNbo = vbos[1]
        garmentIbo = vbos[2]

        GLES30.glBindVertexArray(garmentVao)

        // 顶点位置（动态更新）
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, garmentVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 30000 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glEnableVertexAttribArray(0)

        // 法线（动态更新）
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, garmentNbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 30000 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glEnableVertexAttribArray(1)

        // 纹理坐标（占位）
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glEnableVertexAttribArray(2)

        // 索引缓冲：必须挂在 VAO 上（ELEMENT_ARRAY_BUFFER 绑定属于 VAO 状态），
        // 否则 glDrawElements 读到空索引指针导致 native 崩溃
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, garmentIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, 30000 * 4, null, GLES30.GL_DYNAMIC_DRAW)

        GLES30.glBindVertexArray(0)
    }

    // ==================== 着色器/纹理工具 ====================

    private fun loadShaderSource(name: String): String {
        return try {
            val inputStream = appContext.resources.openRawResource(
                appContext.resources.getIdentifier(
                    name.substringBeforeLast("."),
                    "raw",
                    appContext.packageName
                )
            )
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load shader: $name", e)
            ""
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertexShader == 0 || fragmentShader == 0) return 0

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)

        GLES30.glBindAttribLocation(program, 0, "aPosition")
        GLES30.glBindAttribLocation(program, 1, "aNormal")
        GLES30.glBindAttribLocation(program, 2, "aTexCoord")

        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Program link failed: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return 0
        }
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        if (source.isEmpty()) return 0
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createTexture(): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return texIds[0]
    }

    /**
     * 创建深度纹理（16-bit depth，用于遮挡测试）
     */
    private fun createDepthTexture(width: Int, height: Int): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texIds[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // 用 1.0（half-float 0x3C00）填充初始化（表示无穷远，即无遮挡）
        // 注意 0xFF 字节模式是 half-float NaN，会导致着色器比较恒为 false
        val ones = shortArrayOf(0x3C00)
        val shortBuf = ByteBuffer.allocateDirect(width * height * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (i in 0 until width * height) shortBuf.put(ones[0])
        shortBuf.flip()
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, width, height, 0,
            GLES30.GL_RED, GLES30.GL_HALF_FLOAT, shortBuf)
        return texIds[0]
    }

    private fun createSolidColorTexture(r: Float, g: Float, b: Float): Int {
        val pixels = ByteArray(4)
        pixels[0] = (r * 255).toInt().coerceIn(0, 255).toByte()
        pixels[1] = (g * 255).toInt().coerceIn(0, 255).toByte()
        pixels[2] = (b * 255).toInt().coerceIn(0, 255).toByte()
        pixels[3] = (-1).toByte()

        val texId = createTexture()
        val buf = ByteBuffer.wrap(pixels)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        return texId
    }

    fun release() {
        // 清理 GL 资源（需要在 GL 线程调用）
    }
}
