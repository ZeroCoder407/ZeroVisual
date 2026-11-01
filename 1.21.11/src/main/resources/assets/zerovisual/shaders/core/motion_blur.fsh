#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// the previous frames, blended over the current one with the share kept in ColorModulator.a
void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, ColorModulator.a);
}
