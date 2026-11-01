#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// ZeroVisual rounded shapes. The shape parameters come with every vertex, so many shapes batch together.
// All lengths are in "shape units": the caller scales them so the shape is a few hundred units across.
in vec3 Position;
in vec4 Color;
in vec2 UV0;      // position relative to the shape center
in ivec2 UV1;     // half width, half height
in ivec2 UV2;     // corner radius * 4, outline thickness * 16 (0 = filled)
in float LineWidth; // edge softness (0 = sharp, > 0 = blurred glow/shadow)

out vec4 vertexColor;
out vec2 localPos;
flat out vec4 shape; // half width, half height, radius, thickness
flat out float softness;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    shape = vec4(vec2(UV1), vec2(UV2) / vec2(4.0, 16.0));
    softness = LineWidth;
}
