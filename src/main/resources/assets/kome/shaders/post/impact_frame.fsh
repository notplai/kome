#version 330
#extension GL_ARB_separate_shader_objects : require

// An anime impact frame: the scene struck down to flat black and white, with ink lines along its edges.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ImpactConfig {
    // How bright a pixel must be to come out white.
    float Threshold;
    // 0 for light things white on black, 1 for the reverse.
    float Invert;
};

layout(location = 0) out vec4 fragColor;

float luma(vec2 uv) {
    return dot(texture(InSampler, uv).rgb, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 texel = 1.0 / InSize;
    float centre = luma(texCoord);
    float edge = abs(luma(texCoord + vec2(texel.x, 0.0)) - luma(texCoord - vec2(texel.x, 0.0)))
               + abs(luma(texCoord + vec2(0.0, texel.y)) - luma(texCoord - vec2(0.0, texel.y)));
    float ink = step(Threshold, centre) * (1.0 - step(0.12, edge));
    fragColor = vec4(vec3(mix(ink, 1.0 - ink, Invert)), 1.0);
}
