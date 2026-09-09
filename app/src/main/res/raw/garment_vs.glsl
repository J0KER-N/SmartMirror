#version 300 es
precision mediump float;

in vec3 aPosition;
in vec3 aNormal;
in vec2 aTexCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uNormalMatrix;

out vec2 vTexCoord;
out vec3 vNormal;
out vec3 vPosition;
// 法线是否有效（0 = 上游未提供，片元用导数重建）
flat out float vHasNormal;

void main() {
    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
    vTexCoord = aTexCoord;
    // 法线长度为 0 视为无效（避免 normalize(0) 产生 NaN 导致片元全黑）
    vHasNormal = (dot(aNormal, aNormal) > 0.000001) ? 1.0 : 0.0;
    vNormal = normalize(mat3(uNormalMatrix) * aNormal);
    vPosition = aPosition;
}