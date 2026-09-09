package com.smartmirror.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.smartmirror.app.algorithm.CameraManager
import com.smartmirror.app.algorithm.CombinedFrame
import com.smartmirror.app.algorithm.GarmentDriver
import com.smartmirror.app.algorithm.IBodyTracker
import com.smartmirror.app.algorithm.MediaPipeBodyTracker
import com.smartmirror.app.algorithm.MockJointProvider
import com.smartmirror.app.algorithm.TryOnRenderer
import com.smartmirror.app.databinding.ActivityTryOnBinding
import com.smartmirror.app.algorithm.Joint3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TryOnActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SmartMirrorTryOn"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_NAME = "extra_item_name"
        const val EXTRA_USE_MOCK = "extra_use_mock"
        const val EXTRA_USE_MOCK_JOINTS = "extra_use_mock_joints"

        private const val REQUEST_CAMERA = 2001
        private const val ORBBEC_VENDOR_ID = 0x2BC5
        private const val TARGET_FPS = 30
        private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS
    }

    private lateinit var binding: ActivityTryOnBinding
    private var itemName: String = ""
    private var itemId: Int = -1

    // --- 模拟开关 ---
    private var useMockCamera: Boolean = false
    private var useMockJoints: Boolean = false

    // --- 管线组件 ---
    private var cameraManager: CameraManager? = null
    private var bodyTracker: IBodyTracker? = null
    private var garmentDriver: GarmentDriver? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: TryOnRenderer? = null
    private var pipelineJob: Job? = null

    // --- 状态 ---
    private var hasCamera = false

    // ==================== 生命周期 ==========================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        readIntentExtras()
        detectEmulatorAndAutoMock()

        binding = ActivityTryOnBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        enterImmersiveMode()
        setupGLSurfaceView()
        initPipeline()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        pipelineJob?.cancel()
        cameraManager?.stop()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
    }

    override fun onDestroy() {
        pipelineJob?.cancel()
        cameraManager?.stop()
        bodyTracker?.release()
        garmentDriver?.release()
        renderer?.release()
        super.onDestroy()
    }

    // ==================== 初始化 ============================================

    private fun readIntentExtras() {
        useMockCamera = intent.getBooleanExtra(EXTRA_USE_MOCK, false)
        useMockJoints = intent.getBooleanExtra(EXTRA_USE_MOCK_JOINTS, false)
        itemName = intent.getStringExtra(EXTRA_ITEM_NAME).orEmpty()
        itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
    }

    private fun detectEmulatorAndAutoMock() {
        if (!useMockCamera && isRunningOnEmulator()) {
            Log.i(TAG, "Emulator detected → auto-enabling mock mode")
            useMockCamera = true
            useMockJoints = true
        }
    }

    private fun setupUI() {
        binding.tryOnTitleTextView.text = getString(R.string.main_title)
        binding.backButton.setOnClickListener { finish() }

        if (useMockCamera || useMockJoints) {
            binding.mockModeBadge.visibility = View.VISIBLE
        }

        if (itemName.isNotEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.try_on_message, itemName),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 纯新 API 沉浸模式（移除废弃的 systemUiVisibility） */
    private fun enterImmersiveMode() {
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enter immersive mode", e)
        }
    }

    private fun setupGLSurfaceView() {
        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            renderer = TryOnRenderer(this@TryOnActivity)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        binding.cameraContainer.removeAllViews()
        binding.cameraContainer.addView(glSurfaceView)
        binding.cameraContainer.visibility = View.VISIBLE
        binding.degradedLayout.visibility = View.GONE
    }

    // ==================== 管线初始化 ========================================

    /**
     * 决策树：
     *   useMockCamera = true
     *     ├─ useMockJoints = true  → CameraManager mock + MockJointProvider
     *     └─ useMockJoints = false → CameraManager mock + MediaPipeBodyTracker
     *   useMockCamera = false
     *     └─ USB 深度摄像头检测 → 权限请求 → 真实管线 或 fallback mock
     */
    private fun initPipeline() {
        if (useMockCamera) {
            Log.i(TAG, "MOCK MODE: camera=${useMockCamera}, joints=${useMockJoints}")
            initMockPipeline()
        } else {
            Log.i(TAG, "REAL MODE: checking USB depth camera")
            if (isDepthCameraConnected()) {
                checkCameraPermissionAndInit()
            } else {
                Log.w(TAG, "No depth camera → falling back to mock")
                initMockPipeline()
            }
        }
    }

    private fun checkCameraPermissionAndInit() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA,
            )
            return
        }
        initRealPipeline()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initRealPipeline()
            } else {
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
                initMockPipeline()
            }
        }
    }

    // ── 模拟管线 ──────────────────────────────────────────────────────────

    private fun initMockPipeline() {
        try {
            val cm = CameraManager.getInstance(applicationContext)
            cm.useMockData = true
            cm.mockDrawableResId = resolveMockDrawable()
            cm.start()

            cameraManager = cm
            hasCamera = true
            renderer?.hasCamera = true

            garmentDriver = GarmentDriver(applicationContext)

            bodyTracker = if (useMockJoints) {
                MockBodyTracker()
            } else {
                MediaPipeBodyTracker(applicationContext)
            }

            startPipelineLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Mock pipeline init failed", e)
            enterDegradedMode("初始化失败: ${e.message}")
        }
    }

    private fun resolveMockDrawable(): Int {
        return try {
            resources.getIdentifier("test_person", "drawable", packageName).also { id ->
                if (id == 0) Log.w(TAG, "test_person drawable not found → using solid color")
            }
        } catch (_: Exception) {
            0
        }
    }

    // ── 真实管线 ──────────────────────────────────────────────────────────

    private fun initRealPipeline() {
        Log.i(TAG, "Initializing real camera pipeline")
        try {
            val cm = CameraManager.getInstance(applicationContext)
            cm.useMockData = false
            if (!cm.start()) {
                initMockPipeline()
                return
            }
            cameraManager = cm
            hasCamera = true
            renderer?.hasCamera = true

            bodyTracker = MediaPipeBodyTracker(applicationContext)
            garmentDriver = GarmentDriver(applicationContext)
            renderer?.hasDepthData = true

            startPipelineLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Real pipeline init failed", e)
            initMockPipeline()
        }
    }

    // ==================== 管线循环 ==========================================

    private fun startPipelineLoop() {
        pipelineJob = lifecycleScope.launch(Dispatchers.Default) {
            val tracker = bodyTracker ?: return@launch
            val driver = garmentDriver ?: return@launch
            val ren = renderer ?: return@launch

            // MockJointProvider 独立管线（不依赖 CameraManager 帧流）
            if (useMockJoints) {
                runMockJointPipeline(tracker, driver, ren)
                return@launch
            }

            // 正常 CameraManager 帧流管线
            val cm = cameraManager ?: return@launch
            Log.i(TAG, "Pipeline loop started")
            runFramePipeline(cm, tracker, driver, ren)
        }
    }

    private suspend fun runFramePipeline(
        cm: CameraManager,
        tracker: IBodyTracker,
        driver: GarmentDriver,
        ren: TryOnRenderer,
    ) {
        var frameCount = 0
        var fpsTimer = System.nanoTime()

        cm.frameFlow.collect { frame ->
            val frameStart = System.nanoTime()

            // 1. 姿态估计
            val joints = tracker.update(frame)
            // 2. 衣物驱动
            val vertices = driver.bindSkeleton(joints)
            val faces = driver.getFaceIndices()
            // 3. 更新渲染器
            ren.updateGarment(vertices, FloatArray(0), faces)

            // 4. FPS 统计
            frameCount++
            trackFps(frameCount, fpsTimer) { count, timer ->
                frameCount = count
                fpsTimer = timer
            }

            // 5. 帧率节流
            throttleFrame(frameStart)
        }
    }

    private suspend fun runMockJointPipeline(
        tracker: IBodyTracker,
        driver: GarmentDriver,
        ren: TryOnRenderer,
    ) {
        Log.i(TAG, "Mock joint pipeline started")
        var frameCount = 0
        var fpsTimer = System.nanoTime()
        // mock 背景图：从 CameraManager 的 RGB 缓冲转出，只转一次（内容静态）
        var mockBackgroundSent = false

        while (currentCoroutineContext().isActive) {
            val frameStart = System.nanoTime()

            // mock 摄像头背景：发送一次静态画面到渲染器
            if (!mockBackgroundSent) {
                cameraManager?.peekColorBitmap()?.let { bmp ->
                    ren.updateCameraFrame(bmp)
                    mockBackgroundSent = true
                }
            }

            val joints = MockJointProvider.generate(frameStart)
            val vertices = driver.bindSkeleton(joints)
            val faces = driver.getFaceIndices()
            ren.updateGarment(vertices, driver.currentNormals, faces)

            frameCount++
            trackFps(frameCount, fpsTimer) { count, timer ->
                frameCount = count
                fpsTimer = timer
            }

            throttleFrame(frameStart)
        }
    }

    // ── FPS 统计 ──────────────────────────────────────────────────────────

    private inline fun trackFps(
        frameCount: Int,
        fpsTimer: Long,
        reset: (Int, Long) -> Unit,
    ) {
        val now = System.nanoTime()
        val elapsed = (now - fpsTimer) / 1e9f
        if (elapsed >= 1.0f) {
            Log.d(TAG, "Pipeline FPS: $frameCount")
            reset(0, now)
        }
    }

    /** 将帧率限制在 [TARGET_FPS] */
    private suspend fun throttleFrame(frameStartNano: Long) {
        val elapsed = (System.nanoTime() - frameStartNano) / 1_000_000f
        val remaining = (FRAME_INTERVAL_MS - elapsed).toLong()
        if (remaining > 0) delay(remaining.coerceAtMost(FRAME_INTERVAL_MS))
    }

    // ==================== 工具方法 ==========================================

    private fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.HARDWARE == "ranchu"
    }

    private fun isDepthCameraConnected(): Boolean {
        return try {
            val usbManager = getSystemService(USB_SERVICE) as UsbManager
            usbManager.deviceList.any { (_, device) -> device.vendorId == ORBBEC_VENDOR_ID }
        } catch (e: Exception) {
            false
        }
    }

    private fun enterDegradedMode(message: String) {
        runOnUiThread {
            binding.cameraContainer.visibility = View.GONE
            binding.degradedLayout.visibility = View.VISIBLE
            binding.degradedMessageTextView.text = message.ifEmpty {
                getString(R.string.degraded_mode_message)
            }
        }
    }
}

// =========================================================================
// MockBodyTracker — 跳过 MediaPipe，直接用 MockJointProvider 生成关节数据
// =========================================================================

class MockBodyTracker : IBodyTracker {
    override fun update(frame: CombinedFrame): List<Joint3D> {
        return MockJointProvider.generate(frame.timestamp)
    }

    override fun release() {
        // 无资源需要释放
    }
}
