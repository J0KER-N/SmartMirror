#version 300 es
precision mediump float;

in vec2 vTexCoord;
in vec3 vNormal;
in vec3 vPosition;
flat in float vHasNormal;

uniform sampler2D uColorTexture;
uniform float uOpacity;
uniform vec3 uLightDir;

// 深度遮挡测试
uniform sampler2D uDepthTexture;
uniform mat4 uProjectionMatrix;
uniform vec2 uDepthTextureSize;
uniform float uHasDepth;

out vec4 fragColor;

void main() {
    // 1. 深度遮挡测试：将衣服片段的 3D 位置投影到 NDC，
    //    与深度图中对应像素比较，如果衣服在深度图表示的表面后面，则丢弃
    if (uHasDepth > 0.5) {
        vec4 clipPos = uProjectionMatrix * vec4(vPosition, 1.0);
        vec3 ndc = clipPos.xyz / clipPos.w;

        // NDC [-1,1] 转纹理坐标 [0,1]（y 翻转）
        vec2 depthCoord = vec2(ndc.x * 0.5 + 0.5, 1.0 - (ndc.y * 0.5 + 0.5));

        // 当前片段深度（NDC z 归一化到 [0,1]）
        float currentDepth = ndc.z * 0.5 + 0.5;

        // 采样深度图
        float sceneDepth = texture(uDepthTexture, depthCoord).r;

        // 深度图中 1.0 表示无穷远（无有效数据），此时不做遮挡
        if (sceneDepth < 0.999) {
            // 衣物片段在场景表面后面：丢弃（被身体遮挡）
            // 给予 2cm 的容差避免 z-fighting
            if (currentDepth > sceneDepth + 0.001) {
                discard;
            }
        }
    }

    // 2. 光照计算（简易 Phong）
    // 上游法线无效时，用屏幕空间导数重建平面法线（flat shading）
    vec3 normal = vNormal;
    if (vHasNormal < 0.5) {
        normal = normalize(cross(dFdx(vPosition), dFdy(vPosition)));
    } else {
        normal = normalize(normal);
    }
    vec3 lightDir = normalize(uLightDir);

    // 确保法线朝向相机（背面法线取反，避免自阴影问题）
    if (!gl_FrontFacing) {
        normal = -normal;
    }

    float diff = max(dot(normal, -lightDir), 0.0);
    float ambient = 0.3;
    float specular = 0.0;

    // 简易高光（Blinn-Phong）
    vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0));
    vec3 halfDir = normalize(-lightDir + viewDir);
    specular = pow(max(dot(normal, halfDir), 0.0), 32.0) * 0.3;

    // 3. 采样颜色纹理
    vec4 texColor = texture(uColorTexture, vTexCoord);
    vec3 baseColor = texColor.rgb;

    // 纹理采样为全黑或默认时使用蓝色调
    if (length(baseColor) < 0.01) {
        baseColor = vec3(0.2, 0.5, 0.9);
    }

    // 4. 合成
    vec3 finalColor = baseColor * (ambient + diff * 0.7) + specular * vec3(1.0, 1.0, 1.0);
    fragColor = vec4(finalColor, uOpacity);
}
