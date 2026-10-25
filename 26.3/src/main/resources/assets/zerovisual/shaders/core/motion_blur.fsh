#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

// the previous frames, blended over the current one with the share kept in ColorModulator.a
void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, ColorModulator.a);
}
