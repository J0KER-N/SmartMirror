package com.smartmirror.app.algorithm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.nio.ShortBuffer

/**
 * Orbbec Gemini 2 深度摄像头管理器。
 *
 * 支持两种模式：
 * - [useMockData] = false（默认）：尝试连接真实 Orbbec 设备。
 *   需要 Orbbec SDK (libOrbbecSDK.so) 和 obsensor_jni 模块。
 * - [useMockData] = true：通过协程定时发送模拟 CombinedFrame，
 *   包含从 drawable 加载的测试图片和全 1500mm 深度图。
 *
 * 使用 Kotlin Flow 发送 [CombinedFrame] 和 [CameraState]。
 * 初始化失败时自动切换到模拟降级。
 *
 * ## Orbbec SDK 集成步骤：
 * 1. 从 https://www.orbbec.com/developers/orbbec-sdk/ 下载 Android SDK
 * 2. 将 libOrbbecSDK.so 放到 app/src/main/jniLibs/arm64-v8a/
 * 3. 将 OrbbecSDK.aar 放到 app/libs/
 * 4. 在 obsensor_jni 模块中实现 JNI 包装
 * 5. 取消注释下方 tryStartRealCamera() 中的 TODO 代码
 */
class CameraManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "CameraManager"
        private const val ORBBEC_VENDOR_ID = 0x2BC5
        private const val COLOR_WIDTH = 640
        private const val COLOR_HEIGHT = 480
        private const val FPS = 30

        @Volatile
        private var instance: CameraManager? = null

        fun getInstance(context: Context): CameraManager {
            return instance ?: synchronized(this) {
                instance ?: CameraManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ==================== 开关 ====================

    /** 设为 true 则跳过真实相机，用协程发送模拟帧 */
    @Volatile
    var useMockData: Boolean = false

    /** 模拟帧的背景图片资源 ID（如 R.drawable.test_person），0 表示用纯色 */
    @Volatile
    var mockDrawableResId: Int = 0

    // ==================== Flow ====================

    private val _frameFlow = MutableSharedFlow<CombinedFrame>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frameFlow: Flow<CombinedFrame> = _frameFlow.asSharedFlow()

    private val _cameraStateFlow = MutableSharedFlow<CameraState>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cameraStateFlow: Flow<CameraState> = _cameraStateFlow.asSharedFlow()

    // ==================== 真实相机组件 ====================
    // Orbbec SDK 对象（集成 SDK 后取消注释）
    // private var obContext: com.orbbec.obsensor.Context? = null
    // private var pipeline: com.orbbec.obsensor.Pipeline? = null
    // private var device: com.orbbec.obsensor.Device? = null

    private val realColorBuffer: ByteBuffer = ByteBuffer.allocateDirect(COLOR_WIDTH * COLOR_HEIGHT * 3)
    private val realDepthBuffer: ShortBuffer = ShortBuffer.allocate(COLOR_WIDTH * COLOR_HEIGHT)

    private var isRunning = false
    private var cameraThread: Thread? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var isUsbReceiverRegistered = false

    // ==================== 模拟数据缓存 ====================

    /** 预分配的模拟 Color ByteBuffer（RGB888） */
    private var mockColorBuffer: ByteBuffer = ByteBuffer.allocateDirect(COLOR_WIDTH * COLOR_HEIGHT * 3)

    /** 预分配的模拟 Depth ShortBuffer（全 1500mm） */
    private var mockDepthBuffer: ShortBuffer = ShortBuffer.allocate(COLOR_WIDTH * COLOR_HEIGHT)

    // mock 缓冲延迟到 start() 时初始化（见 start() 注释）

    /**
     * 初始化模拟 Buffer。
     * color 用纯色（深灰蓝）填充，depth 全部填充 1500mm。
     * 如果设置了 mockDrawableResId，尝试将图片缩放后填入 colorBuffer。
     */
    private fun initMockBuffers() {
        // --- depth: 全 1500mm ---
        val depthArray = ShortArray(COLOR_WIDTH * COLOR_HEIGHT) { 1500 }
        mockDepthBuffer = ShortBuffer.wrap(depthArray)

        // --- color: 尝试从 drawable 加载 ---
        // 注意：test_person 是 vector drawable（XML），BitmapFactory 无法解码，
        // 必须走 AppCompatResources（应用主题为 Theme.AppCompat 系，资源可用）
        var bitmapLoaded = false
        if (mockDrawableResId != 0) {
            try {
                val bitmap = decodeDrawableAsBitmap(mockDrawableResId)
                if (bitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, COLOR_WIDTH, COLOR_HEIGHT, true)
                    val argb = IntArray(COLOR_WIDTH * COLOR_HEIGHT)
                    scaled.getPixels(argb, 0, COLOR_WIDTH, 0, 0, COLOR_WIDTH, COLOR_HEIGHT)

                    // ARGB -> RGB888
                    mockColorBuffer.clear()
                    for (i in argb.indices) {
                        val pixel = argb[i]
                        mockColorBuffer.put(i * 3, ((pixel shr 16) and 0xFF).toByte())     // R
                        mockColorBuffer.put(i * 3 + 1, ((pixel shr 8) and 0xFF).toByte())  // G
                        mockColorBuffer.put(i * 3 + 2, (pixel and 0xFF).toByte())          // B
                    }
                    bitmapLoaded = true
                    scaled.recycle()
                    bitmap.recycle()
                    Log.i(TAG, "Loaded mock drawable, scaled to ${COLOR_WIDTH}x$COLOR_HEIGHT")
                } else {
                    Log.w(TAG, "mock drawable $mockDrawableResId decoded to null → solid color")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load mock drawable, using solid color", e)
            }
        }

        if (!bitmapLoaded) {
            mockColorBuffer.clear()
            // 纯色填充：深灰蓝 (R=50, G=70, B=100)
            for (i in 0 until COLOR_WIDTH * COLOR_HEIGHT) {
                mockColorBuffer.put(i * 3, 50.toByte())
                mockColorBuffer.put(i * 3 + 1, 70.toByte())
                mockColorBuffer.put(i * 3 + 2, 100.toByte())
            }
        }

        Log.i(TAG, "Mock buffers initialized: ${COLOR_WIDTH}x$COLOR_HEIGHT")
    }

    /**
     * 解码 drawable 为 Bitmap，同时支持位图和 vector drawable。
     * BitmapFactory 解不了矢量图，矢量图走 AppCompatResources + Canvas 渲染。
     */
    private fun decodeDrawableAsBitmap(resId: Int): Bitmap? {
        return try {
            val drawable = androidx.appcompat.content.res.AppCompatResources
                .getDrawable(appContext, resId)
            if (drawable != null) {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: COLOR_WIDTH
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: COLOR_HEIGHT
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bmp
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Vector decode failed, falling back to BitmapFactory: ${e.message}")
            // 矢量解码失败再退回普通位图解码（覆盖纯 PNG/JPEG 资源的情况）
            try {
                BitmapFactory.decodeResource(appContext.resources, resId)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 将当前 mock RGB 缓冲转为 Bitmap（用于渲染器 mock 背景）。
     * 返回新的 Bitmap 副本，调用方负责管理生命周期。
     */
    fun peekColorBitmap(): Bitmap? {
        if (!useMockData) return null
        return try {
            val buf = mockColorBuffer.duplicate()
            buf.rewind()
            val pixels = IntArray(COLOR_WIDTH * COLOR_HEIGHT)
            for (i in pixels.indices) {
                val r = buf.get().toInt() and 0xFF
                val g = buf.get().toInt() and 0xFF
                val b = buf.get().toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            Bitmap.createBitmap(pixels, COLOR_WIDTH, COLOR_HEIGHT, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.w(TAG, "peekColorBitmap failed", e)
            null
        }
    }

    // ==================== 启动 ====================

    fun start(): Boolean {
        Log.i(TAG, "CameraManager start() — useMockData=$useMockData")

        if (useMockData) {
            // mock 缓冲在此初始化而非构造函数：mockDrawableResId 由调用方在
            // getInstance() 之后、start() 之前设置，构造时机太早会拿不到资源
            initMockBuffers()
            _cameraStateFlow.tryEmit(CameraState.Connected)
            isRunning = true
            startMockCaptureLoop()
            return true
        }

        return tryStartRealCamera()
    }

    private fun tryStartRealCamera(): Boolean {
        return try {
            // 尝试加载 Orbbec SDK 本地库
            val sdkAvailable = try {
                System.loadLibrary("obsensor_jni")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Orbbec SDK native library not found: ${e.message}")
                false
            }

            if (!sdkAvailable) {
                Log.w(TAG, "Orbbec SDK not available, falling back to mock. " +
                    "Please add libobsensor_jni.so to app/src/main/jniLibs/arm64-v8a/")
                _cameraStateFlow.tryEmit(CameraState.Error("Orbbec SDK 未安装"))
                return false
            }

            // ============================================================
            // TODO: Orbbec SDK 初始化 — 集成 SDK 后取消下方注释
            // ============================================================
            // val obContext = com.orbbec.obsensor.Context.create(appContext)
            // this.obContext = obContext
            //
            // val deviceList = obContext.queryDeviceList()
            // if (deviceList.deviceCount == 0) {
            //     Log.w(TAG, "No Orbbec device found")
            //     _cameraStateFlow.tryEmit(CameraState.Disconnected)
            //     return false
            // }
            //
            // device = deviceList.getDevice(0)
            // pipeline = com.orbbec.obsensor.Pipeline(device)
            //
            // // 配置彩色 + 深度流（对齐到彩色）
            // val config = com.orbbec.obsensor.Config().apply {
            //     enableColorStream(com.orbbec.obsensor.StreamType.COLOR, COLOR_WIDTH, COLOR_HEIGHT, com.orbbec.obsensor.Format.RGB888, FPS)
            //     enableDepthStream(com.orbbec.obsensor.StreamType.DEPTH, COLOR_WIDTH, COLOR_HEIGHT, com.orbbec.obsensor.Format.Y16, FPS)
            //     setAlignMode(com.orbbec.obsensor.AlignMode.ALIGN_D2C)
            // }
            //
            // pipeline?.start(config)
            // startRealFrameCaptureLoop()
            // ============================================================

            Log.w(TAG, "Real camera SDK code is commented out — uncomment after adding Orbbec SDK")
            _cameraStateFlow.tryEmit(CameraState.Disconnected)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start real camera", e)
            _cameraStateFlow.tryEmit(CameraState.Error("初始化失败: ${e.message}"))
            false
        }
    }

    // ==================== 模拟帧循环 ====================

    private fun startMockCaptureLoop() {
        cameraThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

            while (isRunning) {
                try {
                    // 复用预分配的 Buffer，创建副本发送
                    mockColorBuffer.rewind()
                    val colorDup = ByteBuffer.allocateDirect(COLOR_WIDTH * COLOR_HEIGHT * 3)
                    colorDup.put(mockColorBuffer)
                    colorDup.flip()

                    mockDepthBuffer.rewind()
                    val depthDup = ShortBuffer.allocate(COLOR_WIDTH * COLOR_HEIGHT)
                    depthDup.put(mockDepthBuffer)
                    depthDup.flip()

                    val frame = CombinedFrame(
                        color = colorDup,
                        depth = depthDup,
                        width = COLOR_WIDTH,
                        height = COLOR_HEIGHT,
                        timestamp = System.nanoTime()
                    )

                    _frameFlow.tryEmit(frame)

                    // 30fps: ~33ms
                    Thread.sleep(33)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (isRunning) Log.e(TAG, "Mock capture error", e)
                    break
                }
            }
        }.apply {
            name = "MockCameraLoop"
            isDaemon = true
        }.also { it.start() }

        Log.i(TAG, "Mock capture loop started")
    }

    // ==================== 真实帧循环（集成 Orbbec SDK 后取消注释） ====================

    private fun startRealFrameCaptureLoop() {
        cameraThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

            while (isRunning) {
                try {
                    // ============================================================
                    // TODO: Orbbec SDK 帧获取 — 集成 SDK 后取消下方注释
                    // ============================================================
                    // val frameSet = pipeline?.waitForFrameSet(1000) ?: continue
                    //
                    // val colorFrame = frameSet.getColorFrame()
                    // val depthFrame = frameSet.getDepthFrame()
                    //
                    // if (colorFrame != null && depthFrame != null) {
                    //     val colorData = colorFrame.getData() // ByteBuffer
                    //     val depthData = depthFrame.getData() // ShortBuffer
                    //
                    //     val frame = CombinedFrame(
                    //         color = colorData,
                    //         depth = depthData,
                    //         width = COLOR_WIDTH,
                    //         height = COLOR_HEIGHT,
                    //         timestamp = System.nanoTime()
                    //     )
                    //
                    //     _frameFlow.tryEmit(frame)
                    //     _cameraStateFlow.tryEmit(CameraState.Connected)
                    // }
                    // frameSet.close()
                    // ============================================================
                } catch (e: Exception) {
                    Log.e(TAG, "Frame capture error", e)
                    _cameraStateFlow.tryEmit(CameraState.Error("帧捕获错误: ${e.message}"))
                    break
                }
            }
        }.apply {
            name = "CameraFrameCapture"
            isDaemon = true
        }.also { it.start() }
    }

    // ==================== USB 热插拔 ====================

    private fun registerUsbReceiver() {
        if (isUsbReceiverRegistered) return
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device == null || device.vendorId != ORBBEC_VENDOR_ID) return
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        Log.i(TAG, "Orbbec device attached, reconnecting...")
                        _cameraStateFlow.tryEmit(CameraState.Connected)
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        Log.w(TAG, "Orbbec device detached")
                        isRunning = false
                        _cameraStateFlow.tryEmit(CameraState.Disconnected)
                    }
                }
            }
        }
        appContext.registerReceiver(usbReceiver, filter)
        isUsbReceiverRegistered = true
    }

    // ==================== 停止 & 释放 ====================

    fun stop() {
        Log.i(TAG, "CameraManager stop()")
        isRunning = false
        cameraThread?.interrupt()
        cameraThread = null

        if (isUsbReceiverRegistered) {
            try { appContext.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
            isUsbReceiverRegistered = false
        }

        try {
            // pipeline?.stop()
            // pipeline?.close()
            // device?.close()
            // obContext?.close()
        } catch (_: Exception) {}
    }

    sealed class CameraState {
        data object Connected : CameraState()
        data object Disconnected : CameraState()
        data class Error(val message: String) : CameraState()
    }
}
