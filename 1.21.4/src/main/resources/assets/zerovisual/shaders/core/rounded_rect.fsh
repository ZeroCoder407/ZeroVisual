#version 150

in vec2 localPos;
in vec4 vertexColor;

uniform vec4 ColorModulator;
uniform vec2 Size;       // half extents
uniform float Radius;
uniform float Thickness; // > 0: outline only
uniform float Softness;  // > 0: blurred edge (shadows / glow)

out vec4 fragColor;

float roundedBoxSdf(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float d = roundedBoxSdf(localPos, Size, Radius);
    float aa = max(fwidth(d), 0.0001);

    float alpha;
    if (Softness > 0.0) {
        alpha = 1.0 - smoothstep(-Softness, Softness, d);
    } else {
        alpha = clamp(0.5 - d / aa, 0.0, 1.0);
    }

    if (Thickness > 0.0) {
        alpha *= clamp(0.5 + (d + Thickness) / aa, 0.0, 1.0);
    }

    vec4 color = vec4(vertexColor.rgb, vertexColor.a * alpha);
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
