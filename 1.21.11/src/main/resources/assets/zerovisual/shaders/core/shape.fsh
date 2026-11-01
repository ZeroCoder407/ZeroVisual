#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 localPos;
flat in vec4 shape;
flat in float softness;

out vec4 fragColor;

float roundedBoxSdf(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 halfSize = shape.xy;
    float radius = min(shape.z, min(halfSize.x, halfSize.y));
    float thickness = shape.w;
    float d = roundedBoxSdf(localPos, halfSize, radius);
    float aa = max(fwidth(d), 0.0001);

    float alpha;
    if (softness > 0.0) {
        alpha = 1.0 - smoothstep(-softness, softness, d);
    } else {
        alpha = clamp(0.5 - d / aa, 0.0, 1.0);
    }
    if (thickness > 0.0) {
        alpha *= clamp(0.5 + (d + thickness) / aa, 0.0, 1.0);
    }

    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha);
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
