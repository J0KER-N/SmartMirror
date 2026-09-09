// garment_jni.cpp
// JNI 层：衣物 OBJ 加载、骨骼-顶点绑定计算、LBS 顶点变换
//
// 编译要求：C++11, Eigen 或 GLM（当前使用纯 C++ 实现）
// 链接：需链接 obsensor_jni 模块（Orbbec SDK）
//
// TODO: 替换 GLM/Eigen 头文件路径

#include <jni.h>
#include <vector>
#include <string>
#include <fstream>
#include <sstream>
#include <cmath>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#define LOG_TAG "GarmentJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ================== 简易 3D 数学（替代 GLM/Eigen） ==================

struct Vec3 {
    float x, y, z;
    Vec3() : x(0), y(0), z(0) {}
    Vec3(float x, float y, float z) : x(x), y(y), z(z) {}
    Vec3 operator+(const Vec3& o) const { return {x+o.x, y+o.y, z+o.z}; }
    Vec3 operator-(const Vec3& o) const { return {x-o.x, y-o.y, z-o.z}; }
    Vec3 operator*(float s) const { return {x*s, y*s, z*s}; }
    float dot(const Vec3& o) const { return x*o.x + y*o.y + z*o.z; }
    Vec3 cross(const Vec3& o) const { return {
        y*o.z - z*o.y,
        z*o.x - x*o.z,
        x*o.y - y*o.x
    };}
    float length() const { return std::sqrt(x*x + y*y + z*z); }
    Vec3 normalized() const {
        float l = length();
        if (l < 1e-6f) return {0, 0, 1};
        return {x/l, y/l, z/l};
    }
};

struct Mat4 {
    float m[16]; // column-major

    Mat4() { for (int i = 0; i < 16; i++) m[i] = 0; }

    static Mat4 identity() {
        Mat4 r;
        r.m[0] = 1; r.m[5] = 1; r.m[10] = 1; r.m[15] = 1;
        return r;
    }

    static Mat4 translation(float tx, float ty, float tz) {
        Mat4 r = identity();
        r.m[12] = tx; r.m[13] = ty; r.m[14] = tz;
        return r;
    }

    // 从两方向向量构造旋转矩阵（从 from 旋转到 to）
    static Mat4 rotationFromTo(const Vec3& from, const Vec3& to) {
        Vec3 f = from.normalized();
        Vec3 t = to.normalized();
        float d = f.dot(t);
        if (d > 0.9999f) return identity();
        if (d < -0.9999f) {
            // 180 度，绕任意轴
            Vec3 axis = Vec3(1, 0, 0).cross(f);
            if (axis.length() < 1e-6f) axis = Vec3(0, 1, 0).cross(f);
            return rotationAxisAngle(axis.normalized(), 3.14159265f);
        }
        Vec3 axis = f.cross(t).normalized();
        float angle = std::acos(d);
        return rotationAxisAngle(axis, angle);
    }

    static Mat4 rotationAxisAngle(const Vec3& axis, float angle) {
        float c = std::cos(angle);
        float s = std::sin(angle);
        float t = 1 - c;
        float x = axis.x, y = axis.y, z = axis.z;
        Mat4 r;
        r.m[0]  = t*x*x + c;   r.m[4]  = t*x*y - z*s; r.m[8]  = t*x*z + y*s; r.m[12] = 0;
        r.m[1]  = t*x*y + z*s; r.m[5]  = t*y*y + c;   r.m[9]  = t*y*z - x*s; r.m[13] = 0;
        r.m[2]  = t*x*z - y*s; r.m[6]  = t*y*z + x*s; r.m[10] = t*z*z + c;   r.m[14] = 0;
        r.m[3]  = 0;            r.m[7]  = 0;            r.m[11] = 0;            r.m[15] = 1;
        return r;
    }

    Vec3 transformPoint(const Vec3& p) const {
        return {
            m[0]*p.x + m[4]*p.y + m[8]*p.z  + m[12],
            m[1]*p.x + m[5]*p.y + m[9]*p.z  + m[13],
            m[2]*p.x + m[6]*p.y + m[10]*p.z + m[14]
        };
    }
};

// ================== 衣物模型数据 ==================

struct GarmentModel {
    std::vector<Vec3> restVertices;   // T-Pose 顶点
    std::vector<Vec3> restNormals;    // T-Pose 法线
    std::vector<int>   faces;         // 三角形面（flat index）
    std::vector<int>   boneIndices;   // 每个顶点绑定的骨骼索引（0=躯干, 1=左臂, 2=右臂）
    std::vector<float> boneWeights;   // 每个顶点绑定的骨骼权重（当前固定 1.0）
};

static GarmentModel s_model;
static bool s_initialized = false;

// ================== OBJ 加载 ==================

bool loadObjFromAsset(AAssetManager* mgr, const char* path, GarmentModel& model) {
    AAsset* asset = AAssetManager_open(mgr, path, AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Cannot open asset: %s", path);
        return false;
    }
    const char* data = static_cast<const char*>(AAsset_getBuffer(asset));
    off_t len = AAsset_getLength(asset);
    std::string content(data, len);
    AAsset_close(asset);

    std::vector<Vec3> verts, norms;
    std::vector<int> faces;
    std::istringstream stream(content);
    std::string line;

    while (std::getline(stream, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream ls(line);
        std::string prefix;
        ls >> prefix;

        if (prefix == "v") {
            float x, y, z;
            ls >> x >> y >> z;
            verts.push_back({x, y, z});
        } else if (prefix == "vn") {
            float x, y, z;
            ls >> x >> y >> z;
            norms.push_back({x, y, z});
        } else if (prefix == "f") {
            // 简单支持 "f v1 v2 v3" 或 "f v1/t1 v2/t2 v3/t3"
            std::string p1, p2, p3;
            ls >> p1 >> p2 >> p3;
            auto parseVert = [](const std::string& s) -> int {
                auto pos = s.find('/');
                if (pos == std::string::npos) return std::stoi(s) - 1;
                return std::stoi(s.substr(0, pos)) - 1;
            };
            faces.push_back(parseVert(p1));
            faces.push_back(parseVert(p2));
            faces.push_back(parseVert(p3));
        }
    }

    if (verts.empty()) {
        LOGE("No vertices loaded from OBJ");
        return false;
    }

    model.restVertices = verts;
    model.faces = faces;

    // 如果没有法线，自动计算
    if (norms.empty()) {
        norms.resize(verts.size(), {0, 0, 0});
        for (size_t i = 0; i < faces.size(); i += 3) {
            int i0 = faces[i], i1 = faces[i+1], i2 = faces[i+2];
            Vec3 e1 = verts[i1] - verts[i0];
            Vec3 e2 = verts[i2] - verts[i0];
            Vec3 n = e1.cross(e2).normalized();
            norms[i0] = norms[i0] + n;
            norms[i1] = norms[i1] + n;
            norms[i2] = norms[i2] + n;
        }
        for (auto& n : norms) n = n.normalized();
    }
    model.restNormals = norms;

    LOGI("Loaded OBJ: %zu vertices, %zu faces", verts.size(), faces.size() / 3);
    return true;
}

// ================== 骨骼-顶点绑定计算 ==================

void computeBoneWeights(GarmentModel& model) {
    size_t n = model.restVertices.size();
    model.boneIndices.resize(n, 0);
    model.boneWeights.resize(n, 1.0f);

    // 按 Y 和 X 轴分区域
    float minY = 1e9, maxY = -1e9;
    for (auto& v : model.restVertices) {
        if (v.y < minY) minY = v.y;
        if (v.y > maxY) maxY = v.y;
    }
    float midY = (minY + maxY) * 0.5f;
    float maxAbsX = 0;
    for (auto& v : model.restVertices) {
        if (std::abs(v.x) > maxAbsX) maxAbsX = std::abs(v.x);
    }
    float armThresh = maxAbsX * 0.6f;

    for (size_t i = 0; i < n; i++) {
        auto& v = model.restVertices[i];
        if (v.y > midY && std::abs(v.x) > armThresh) {
            model.boneIndices[i] = (v.x < 0) ? 1 : 2; // 左臂 / 右臂
        } else {
            model.boneIndices[i] = 0; // 躯干
        }
    }
    LOGI("Bone weights computed: %zu vertices", n);
}

// ================== 顶点变换 ==================

void updateVertices(const std::vector<Vec3>& currentJoints,
                    const std::vector<Vec3>& tPoseJoints,
                    std::vector<Vec3>& outVerts,
                    std::vector<Vec3>& outNorms) {

    size_t n = s_model.restVertices.size();
    outVerts.resize(n);
    outNorms.resize(n);

    // 计算各关节变换矩阵
    // 关节索引：0=nose, 1=L_shoulder, 2=R_shoulder, 3=L_elbow, 4=R_elbow
    //           7=L_hip, 8=R_hip, 19=upper_spine, 20=mid_shoulder

    // 躯干变换：基于骨盆和肩部中心
    Vec3 tPoseTorsoCenter = {
        (tPoseJoints[7].x + tPoseJoints[8].x + tPoseJoints[19].x + tPoseJoints[20].x) * 0.25f,
        (tPoseJoints[7].y + tPoseJoints[8].y + tPoseJoints[19].y + tPoseJoints[20].y) * 0.25f,
        (tPoseJoints[7].z + tPoseJoints[8].z + tPoseJoints[19].z + tPoseJoints[20].z) * 0.25f
    };
    Vec3 curTorsoCenter = {
        (currentJoints[7].x + currentJoints[8].x + currentJoints[19].x + currentJoints[20].x) * 0.25f,
        (currentJoints[7].y + currentJoints[8].y + currentJoints[19].y + currentJoints[20].y) * 0.25f,
        (currentJoints[7].z + currentJoints[8].z + currentJoints[19].z + currentJoints[20].z) * 0.25f
    };

    Mat4 torsoMat = Mat4::translation(
        curTorsoCenter.x - tPoseTorsoCenter.x,
        curTorsoCenter.y - tPoseTorsoCenter.y,
        curTorsoCenter.z - tPoseTorsoCenter.z
    );

    // 左臂变换
    Vec3 tPoseArmDirL = tPoseJoints[3] - tPoseJoints[1];
    Vec3 curArmDirL  = currentJoints[3] - currentJoints[1];
    Vec3 lShoulderDelta = currentJoints[1] - tPoseJoints[1];

    Mat4 lArmRot = Mat4::rotationFromTo(tPoseArmDirL, curArmDirL);
    Mat4 lArmMat = Mat4::translation(lShoulderDelta.x, lShoulderDelta.y, lShoulderDelta.z)
                 * lArmRot;

    // 右臂变换
    Vec3 tPoseArmDirR = tPoseJoints[4] - tPoseJoints[2];
    Vec3 curArmDirR  = currentJoints[4] - currentJoints[2];
    Vec3 rShoulderDelta = currentJoints[2] - tPoseJoints[2];

    Mat4 rArmRot = Mat4::rotationFromTo(tPoseArmDirR, curArmDirR);
    Mat4 rArmMat = Mat4::translation(rShoulderDelta.x, rShoulderDelta.y, rShoulderDelta.z)
                 * rArmRot;

    // 应用变换
    for (size_t i = 0; i < n; i++) {
        Vec3 restV = s_model.restVertices[i];
        Vec3 restN = s_model.restNormals[i];

        int bone = s_model.boneIndices[i];
        Mat4 transform;
        switch (bone) {
            case 1: // 左臂
                transform = lArmMat;
                break;
            case 2: // 右臂
                transform = rArmMat;
                break;
            default: // 躯干
                transform = torsoMat;
                break;
        }

        outVerts[i] = transform.transformPoint(restV);
        // 法线只旋转不平移
        Vec3 rotatedN = Mat4::rotationFromTo({0,0,1}, {transform.m[2], transform.m[6], transform.m[10]})
                            .transformPoint(restN);
        outNorms[i] = rotatedN.normalized();
    }
}

// ================== 生成立方体占位 ==================

void generateCubeModel(GarmentModel& model) {
    float s = 0.15f;
    model.restVertices = {
        {-s, -s,  s}, { s, -s,  s}, { s,  s,  s}, {-s,  s,  s},
        {-s, -s, -s}, { s, -s, -s}, { s,  s, -s}, {-s,  s, -s}
    };
    int faces[] = {
        0,1,2, 0,2,3, 1,5,6, 1,6,2, 5,4,7, 5,7,6,
        4,0,3, 4,3,7, 3,2,6, 3,6,7, 4,5,1, 4,1,0
    };
    model.faces.assign(faces, faces + 36);

    model.restNormals.clear();
    for (auto& v : model.restVertices) {
        float l = v.length();
        if (l > 1e-6f) model.restNormals.push_back({v.x/l, v.y/l, v.z/l});
        else model.restNormals.push_back({0, 0, 1});
    }
}

// ================== JNI 接口 ==================

extern "C" {

JNIEXPORT void JNICALL
Java_com_smartmirror_app_algorithm_GarmentJNI_nativeInit(
    JNIEnv* env, jclass cls, jobject assetManager, jstring modelPath) {

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("Failed to get AAssetManager");
        return;
    }
    const char* path = env->GetStringUTFChars(modelPath, nullptr);

    s_model = GarmentModel();
    bool ok = loadObjFromAsset(mgr, path, s_model);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ok) {
        LOGW("OBJ load failed, generating cube fallback");
        generateCubeModel(s_model);
    }

    computeBoneWeights(s_model);
    s_initialized = true;
    LOGI("GarmentJNI nativeInit OK, vertices=%zu", s_model.restVertices.size());
}

JNIEXPORT jfloatArray JNICALL
Java_com_smartmirror_app_algorithm_GarmentJNI_nativeUpdateVertices(
    JNIEnv* env, jclass cls, jfloatArray jointArray) {

    if (!s_initialized) return nullptr;

    int nJoints = env->GetArrayLength(jointArray) / 3;
    if (nJoints < 21) {
        LOGE("Need at least 21 joints, got %d", nJoints);
        return nullptr;
    }

    jfloat* jointData = env->GetFloatArrayElements(jointArray, nullptr);

    std::vector<Vec3> currentJoints(21);
    std::vector<Vec3> tPoseJoints(21);
    for (int i = 0; i < 21; i++) {
        currentJoints[i] = {jointData[i*3], jointData[i*3+1], jointData[i*3+2]};
    }

    env->ReleaseFloatArrayElements(jointArray, jointData, JNI_ABORT);

    // 生成 T-Pose 参考关节（与 MediaPipeBodyTracker.generateMockTPoseJoints 一致）
    float h = 1.7f;
    float armSpan = 0.8f;
    float shoulderY = -0.15f;
    float hipY = 0.4f;
    float depthZ = 1.5f;
    float upperArm = 0.3f;
    float forearm = 0.28f;

    tPoseJoints[0]  = {0, 0, depthZ};
    tPoseJoints[1]  = {-armSpan, shoulderY, depthZ};
    tPoseJoints[2]  = {armSpan, shoulderY, depthZ};
    tPoseJoints[3]  = {-armSpan - upperArm, shoulderY + 0.05f, depthZ};
    tPoseJoints[4]  = {armSpan + upperArm, shoulderY + 0.05f, depthZ};
    tPoseJoints[5]  = {-armSpan - upperArm - forearm, shoulderY + 0.1f, depthZ};
    tPoseJoints[6]  = {armSpan + upperArm + forearm, shoulderY + 0.1f, depthZ};
    tPoseJoints[7]  = {-0.15f, hipY, depthZ};
    tPoseJoints[8]  = {0.15f, hipY, depthZ};
    tPoseJoints[9]  = {-0.15f, 0.85f, depthZ};
    tPoseJoints[10] = {0.15f, 0.85f, depthZ};
    tPoseJoints[11] = {-0.15f, 1.3f, depthZ};
    tPoseJoints[12] = {0.15f, 1.3f, depthZ};
    tPoseJoints[13] = {-0.03f, -0.05f, depthZ};
    tPoseJoints[14] = {0.03f, -0.05f, depthZ};
    tPoseJoints[15] = {-0.08f, -0.02f, depthZ};
    tPoseJoints[16] = {0.08f, -0.02f, depthZ};
    tPoseJoints[17] = {-0.05f, 0.05f, depthZ};
    tPoseJoints[18] = {0.05f, 0.05f, depthZ};
    tPoseJoints[19] = {0, -0.1f, depthZ};
    tPoseJoints[20] = {0, shoulderY, depthZ};

    std::vector<Vec3> outVerts, outNorms;
    updateVertices(currentJoints, tPoseJoints, outVerts, outNorms);

    size_t n = outVerts.size();
    jfloatArray result = env->NewFloatArray(n * 3);
    std::vector<jfloat> buf(n * 3);
    for (size_t i = 0; i < n; i++) {
        buf[i*3]   = outVerts[i].x;
        buf[i*3+1] = outVerts[i].y;
        buf[i*3+2] = outVerts[i].z;
    }
    env->SetFloatArrayRegion(result, 0, n * 3, buf.data());

    return result;
}

JNIEXPORT void JNICALL
Java_com_smartmirror_app_algorithm_GarmentJNI_nativeRelease(
    JNIEnv* env, jclass cls) {
    s_model = GarmentModel();
    s_initialized = false;
    LOGI("GarmentJNI released");
}

} // extern "C"