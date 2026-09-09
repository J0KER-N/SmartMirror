#version 300 es
precision mediump float;

in vec2 vTexCoord;
uniform sampler2D uCameraTexture;
uniform float uHasCamera;

out vec4 fragColor;

void main() {
    if (uHasCamera > 0.5) {
        // 摄像头 RGB 纹理（y 翻转）
        vec2 coord = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
        fragColor = texture(uCameraTexture, coord);
    } else {
        // 无摄像头：纯灰背景
        fragColor = vec4(0.15, 0.15, 0.15, 1.0);
    }
}