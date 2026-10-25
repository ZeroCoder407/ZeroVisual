#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

// ZeroVisual rounded shapes. The shape parameters come with every vertex, so many shapes batch together.
// All lengths are in "shape units": the caller scales them so the shape is a few hundred units across.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;      // position relative to the shape center
layout(location = 3) in vec2 UV3;      // corner radius, outline thickness (0 = filled)
layout(location = 4) in ivec2 UV1;     // half width, half height
layout(location = 5) in float LineWidth; // edge softness (0 = sharp, > 0 = blurred glow/shadow)

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) flat out vec4 shape; // half width, half height, radius, thickness
layout(location = 3) flat out float softness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    shape = vec4(vec2(UV1), UV3);
    softness = LineWidth;
}
