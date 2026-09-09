# SmartMirror - Required Assets
# ================================

## 1. MediaPipe Pose Model (姿态检测)
文件名: pose_landmarker_lite.task
路径:   app/src/main/assets/pose_landmarker_lite.task
下载:   https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task

说明:   MediaPipe Pose Landmarker 模型，用于从 RGB 图像检测人体 33 个关键点。
       如果缺少此文件，应用会降级为静态 T-Pose 模拟模式。

快速下载:
  Linux/macOS:  ./download_models.sh
  Windows:      浏览器打开上述 URL 下载，放到 assets/ 目录

## 2. Garment 3D Model (衣物模型)
文件名: tshirt.obj
路径:   app/src/main/assets/tshirt.obj
状态:   ✅ 已包含（52 顶点 T恤模型）

格式要求:
  - v x y z   (顶点坐标，单位为米)
  - vn nx ny nz (法线，可选)
  - f v1 v2 v3 (三角面，1-based 索引)

如果缺少此文件，应用会自动生成立方体占位模型。

## 3. Orbbec SDK Libraries (深度摄像头驱动)
路径:   app/src/main/jniLibs/arm64-v8a/
文件:
  - libOrbbecSDK.so    (Orbbec 官方 SDK)
  - libobsensor_jni.so  (项目 JNI 包装层，由 obsensor_jni 模块编译)

状态:   ⚠️ 需要从 Orbbec 官网下载 SDK 并集成
说明:   未安装时应用自动降级为模拟摄像头模式

## 4. Clothing Images (衣物图片)
路径:   app/src/main/res/drawable/
状态:   ✅ 已包含（6 件衣服的矢量占位图）

建议:   替换为真实的衣服照片/渲染图以获得更好的展示效果
