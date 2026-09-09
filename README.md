# SmartMirror 智能试衣镜

基于深度摄像头的虚拟试衣系统 — Android 前端 + Spring Boot 后端。

用户站在智能镜前，系统实时捕捉身体姿态，将 3D 衣物模型贴合到用户身上，实现"所见即所穿"的沉浸式试衣体验。

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Android 前端 (Kotlin)                       │
│                                                                     │
│  ┌──────────┐   ┌───────────────┐   ┌──────────────┐              │
│  │Camera    │──▶│BodyTracker    │──▶│GarmentDriver │              │
│  │Manager   │   │(MediaPipe/Mock)│   │(LBS/仿射驱动) │              │
│  │(Orbbec/  │   │               │   │              │              │
│  │ Mock)    │   │ 33→21 关节    │   │ OBJ→变形顶点  │              │
│  └──────────┘   └───────────────┘   └──────┬───────┘              │
│       │                                      │                     │
│  RGB+Depth 帧                           顶点+面索引                │
│       │                                      │                     │
│       ▼                                      ▼                     │
│  ┌──────────────────────────────────────────────────────────┐     │
│  │              TryOnRenderer (OpenGL ES 3.0)                │     │
│  │  1. 摄像头背景全屏四边形（关闭深度写入）                     │     │
│  │  2. 衣物网格：Phong 光照 + 深度遮挡 + 半透明合成           │     │
│  └──────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
                              │ Retrofit + Gson
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                   Spring Boot 后端 (Java 17)                       │
│  REST CRUD ── ClothingService ── JPA ── H2/MySQL                  │
│  GET/POST/PUT/DELETE  /api/clothes                                  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 已实现功能

### 前端 UI（Jetpack Compose + Material 3）

- **衣橱目录页**：2 列瀑布流卡片、Hero Banner、分类筛选（上装/外套/裙装/下装）、模糊搜索、交错入场动画、骨架屏加载态、空状态提示
- **商品详情页**：全出血大图、悬浮信息卡、颜色选择器、尺码筛选、星级评分、材质/洗护标签、底部吸底栏（价格 + 虚拟试穿按钮）
- **添加衣物页**：内置 6 款矢量插图选择、基础信息表单（名称/品牌/分类/价格）、详情信息（材质/尺码/颜色/标签/描述/洗护）、必填校验
- **设计语言**：高定试衣沙龙风格 — 天鹅绒紫主色 (#4A1A6B)、暖金点缀 (#C8A45C)、暖奶白底色 (#FBF7F2)，Noto Serif 标题字体，0dp 扁平卡片，大圆角

### AR 试衣管线

- **深度摄像头**：Orbbec Gemini 2 USB 深度相机管理器，含 USB 热插拔检测（vendor ID 0x2BC5）、权限请求、自动降级到 Mock
- **姿态估计**：MediaPipe Pose Landmarker（GPU 推理、LIVE_STREAM 模式），33 关键点 → 21 关节映射，深度图对齐投影（针孔模型 + 3×3 中值回退），指数平滑滤波
- **衣物驱动**：OBJ 模型加载、T-Pose 参考骨骼计算、分区域顶点变形（躯干平移 + 手臂旋转缩放）；双路径：JNI LBS（C++ 线性混合蒙皮）优先，Kotlin 分块仿射回退
- **3D 渲染**：OpenGL ES 3.0 渲染器，VAO/VBO 动态更新，深度遮挡测试（NDC 深度比较 + 2cm 容差），Blinn-Phong 光照，零法线时屏幕空间导数重建（`dFdx/dFdy` flat shading），背面法线自动翻转，半透明合成
- **Mock 模式**：模拟器自动检测并启用；静态人形矢量图背景、1500mm 常值深度、手臂抬举/摆动动画；UI 显示 Mock 徽章

### 后端（Spring Boot 3.3.5）

- RESTful CRUD：`GET /api/clothes/user/{userId}`、`GET /api/clothes/{id}`、`POST /api/clothes`、`PUT /api/clothes/{id}`、`DELETE /api/clothes/{id}`
- 统一响应包装 `Result<T>`（code + msg + data）
- JPA 实体 18 字段（含 JSON 字符串存储的 sizeJson/colorsJson/tagsJson）
- H2 文件数据库开发模式，MySQL 生产就绪（驱动已配置）
- CORS 全开 + Actuator 健康检查

### 基础设施

- **一键启动脚本**：自动检测 JDK → 启动后端 → 编译 APK → ADB 安装 → 启动应用
- **模拟器修复脚本**：杀残留进程 → 清 AVD 锁/缓存 → 重启 ADB
- **开机自启**：`BootReceiver` 监听 `BOOT_COMPLETED`，Kiosk 模式
- **沉浸模式**：系统栏隐藏（滑动临时唤出）

---

## 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 前端框架 | Jetpack Compose + Material 3 | BOM 2024.02.00 |
| 导航 | Navigation Compose | 2.7.7 |
| 网络 | Retrofit 2 + OkHttp 4 + Gson | 2.9.0 / 4.12.0 |
| 姿态估计 | MediaPipe tasks-vision | 0.10.14 |
| 3D 渲染 | OpenGL ES 3.0 (GLSurfaceView) | — |
| 原生计算 | C++11 JNI (CMake + NDK) | — |
| 后端框架 | Spring Boot | 3.3.5 |
| ORM | Spring Data JPA + Hibernate | — |
| 数据库 | H2 (dev) / MySQL (prod) | — |
| 构建前端 | Gradle 8.4 + AGP 8.2.2 + Kotlin 1.9.22 | JDK 21 |
| 构建后端 | Maven (Spring Boot parent) | JDK 17 |
| 目标硬件 | RK3588 (arm64-v8a) + Orbbec Gemini 2 | — |

---

## 项目结构

```
SmartMirror/
├── app/                          # Android 应用模块
│   ├── src/main/
│   │   ├── java/com/smartmirror/app/
│   │   │   ├── MainActivity.kt          # Compose 导航宿主
│   │   │   ├── TryOnActivity.kt         # AR 试衣 Activity
│   │   │   ├── BootReceiver.kt          # 开机自启
│   │   │   ├── algorithm/
│   │   │   │   ├── CameraManager.kt     # 深度摄像头管理（Orbbec/Mock）
│   │   │   │   ├── CombinedFrame.kt     # RGB+Depth 对齐帧
│   │   │   │   ├── GarmentDriver.kt     # OBJ 加载 + 骨骼驱动
│   │   │   │   ├── GarmentJNI.kt        # JNI 桥接
│   │   │   │   ├── IBodyTracker.kt      # 姿态接口
│   │   │   │   ├── Joint3D.kt           # 3D 关节
│   │   │   │   ├── MediaPipeBodyTracker.kt  # MediaPipe + 深度投影
│   │   │   │   ├── MockJointProvider.kt     # 模拟关节动画
│   │   │   │   └── TryOnRenderer.kt     # OpenGL ES 3.0 渲染器
│   │   │   ├── data/                    # 数据模型 + StateFlow 数据源
│   │   │   ├── network/                 # Retrofit + DTO + Repository
│   │   │   ├── ui/screens/              # List / Detail / AddClothes
│   │   │   └── ui/theme/               # Material 3 主题
│   │   ├── assets/
│   │   │   ├── tshirt.obj               # T 恤 3D 模型 (52 顶点)
│   │   │   └── pose_landmarker_lite.task # MediaPipe 模型 (5.6 MB)
│   │   ├── cpp/
│   │   │   └── garment_jni.cpp          # JNI: OBJ 加载 + LBS 蒙皮
│   │   ├── res/raw/
│   │   │   ├── camera_bg_vs/fs.glsl     # 背景着色器
│   │   │   └── garment_vs/fs.glsl       # 衣物着色器（Phong + 遮挡）
│   │   └── res/drawable/                # 17 个矢量插图 + UI 形状
│   └── build.gradle
├── backend/                      # Spring Boot 后端
│   ├── src/main/java/.../
│   │   ├── controller/ClothingController.java
│   │   ├── service/ClothingService.java
│   │   ├── entity/Clothing.java
│   │   ├── repository/ClothingRepository.java
│   │   ├── common/Result.java
│   │   └── config/WebConfig.java
│   ├── src/main/resources/application.yml
│   └── pom.xml
├── obsensor_jni/                 # Orbbec SDK JNI 包装（占位，未启用）
├── 一键启动.bat                   # 一键启动全链路
├── 启动智能试衣镜.bat             # 交互式菜单
├── 修复模拟器.bat                # 模拟器问题修复
├── BUILD.md                     # 构建指南
└── 设计优化说明.md               # UI 设计决策文档
```

---

## 构建与运行

详细步骤见 [BUILD.md](BUILD.md)，快速开始：

### 1. 启动后端

```bash
cd backend
mvnw.cmd package -DskipTests    # Windows
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

后端启动在 `http://localhost:8080`，H2 控制台 `/h2-console`。

### 2. 构建并安装前端

```bash
gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. 一键启动（Windows）

双击 `一键启动.bat`，自动完成后端启动 → APK 编译 → 安装 → 启动。

### Mock 模式

在模拟器上运行时自动启用 Mock 模式（无需真实摄像头）。Mock 模式下：
- 背景显示矢量人形轮廓
- 衣物模型叠加在人形上，随模拟手臂动画变形
- 右臂 4 秒周期抬举/放下，左臂轻幅摆动

---

## 待实现功能

### 核心试衣体验

| 优先级 | 功能 | 说明 |
|:---:|---|---|
| P0 | Orbbec SDK 真实集成 | `CameraManager` 中 SDK 初始化和帧获取代码已写好但注释掉，需下载 SDK 取消注释并调试 |
| P0 | 深度遮挡管线打通 | `TryOnRenderer` 已有深度纹理和着色器遮挡逻辑，但 `updateGarment()` 不接受深度数据，需将 `CameraManager` 的深度帧接入渲染器 |
| P0 | 多衣物模型切换 | 当前硬编码 `tshirt.obj`，需按选中商品加载对应 OBJ 模型 |
| P1 | 衣物纹理贴图 | 当前衣物纹理为纯色 1×1 像素，需将商品图片作为纹理加载到 `garmentTextureId` |
| P1 | JNI LBS 蒙皮验证 | `garment_jni.cpp` 已实现 LBS，但未在真机上验证；需安装 NDK 并启用 `obsensor_jni` 模块 |

### UI 与交互

| 优先级 | 功能 | 说明 |
|:---:|---|---|
| P1 | 尺码指南弹窗 | DetailScreen "Size Guide" 按钮 `onClick` 为空 |
| P1 | 分享功能 | DetailScreen "Share" 按钮 `onClick` 为空 |
| P2 | 试衣截图保存 | 截取 GLSurfaceView 内容保存到相册 |
| P2 | 试衣录制 | 录制短视频供社交分享 |
| P2 | 多人同时试穿 | MediaPipe `setNumPoses(1)` 需调大，管线需支持多骨架 |
| P3 | 衣物拖拽微调 | 手势拖拽调整衣物在身体上的位置偏移 |
| P3 | 镜面翻转 | 前置摄像头镜像模式切换 |

### 后端

| 优先级 | 功能 | 说明 |
|:---:|---|---|
| P1 | MySQL 生产切换 | 驱动已配置，需在 `application.yml` 切换数据源并建表 |
| P1 | 用户认证 | 当前 `userId` 硬编码为 1，需接入登录/注册 |
| P1 | 图片上传 | `imageUrl` 字段已预留但未使用，需 OSS/本地存储 + 上传接口 |
| P2 | 3D 模型管理 | 衣物 OBJ 文件的上传、存储、版本管理 |
| P2 | 试穿历史 | 记录用户试穿记录和偏好 |

---

## 优化方向

### 渲染性能

- **GPU 蒙皮计算**：将 LBS 从 CPU/JNI 移到顶点着色器（传入骨骼矩阵 uniform 数组），消除每帧 CPU→GPU 顶点回读瓶颈
- **实例化渲染**：多人试穿时用 `glDrawElementsInstanced` 避免逐人切换 VBO
- **纹理压缩**：衣物纹理使用 ASTC/ETC2 压缩格式，减少上传带宽和显存占用
- **帧率自适应**：根据 GPU 负载动态调整 `renderMode`（CONTINUOUSLY ↔ DIRTY），低端设备降帧
- **PBO 异步纹理上传**：用 Pixel Buffer Object 避免 `texImage2D` 阻塞 GL 管线

### 姿态估计

- **MediaPipe 模型升级**：从 `pose_landmarker_lite`（5.6 MB）换到 `heavy`（更高精度，代价稍大）
- **多帧时序滤波**：指数平滑之外加入卡尔曼滤波或一阶低通，抑制关节抖动
- **关键点置信度阈值调优**：当前固定 0.5，可按关节重要性差异化（手腕 > 脊柱）
- **相机内参标定**：从 Orbbec SDK 读取实际内参，替代硬编码 `(fx=620, cx=320, ...)`

### 衣物驱动

- **真 LBS 权重**：当前 C++ JNI 按区域硬分权重（0/1），应改为基于测地距离的平滑蒙皮权重
- **多部位驱动**：当前只驱动躯干+双臂，需扩展到腿部（裙装/裤装）、颈部（高领）
- **褶皱模拟**：顶点着色器中基于关节角度添加简谐褶皱位移，模拟衣物折叠
- **碰撞检测**：衣物与身体网格的简单碰撞响应，防止衣物穿透身体

### 工程质量

- **GL 资源释放**：`TryOnRenderer.release()` 为空，需在 GL 线程中释放 VAO/VBO/纹理/着色器
- **生命周期安全**：`CameraManager` 单例跨 Activity 不释放，应改为 ViewModel-scoped
- **错误恢复**：GL 上下文丢失（`EGL_CONTEXT_LOST`）后的自动重建
- **着色器编译缓存**：首次编译后缓存 program binary，后续启动零编译开销
- **单元测试**：当前只有空壳测试，需覆盖 `GarmentDriver`（OBJ 解析、区域划分、变形正确性）、`MockJointProvider`（关节连续性）、Repository（DTO 映射）
- **CI/CD**：GitHub Actions 自动构建 + lint + 测试

### 用户体验

- **衣物加载动画**：OBJ 加载/纹理上传时显示过渡动画而非卡顿
- **AR 指引**：首次使用时显示"请站在镜前 1-2 米"引导蒙层
- **语音交互**："换一件"/"下一件" 语音指令切衣
- **暗色模式**：`values-night/themes.xml` 已存在但未适配试衣画面

---

## 许可

本项目仅供学习和研究使用。
