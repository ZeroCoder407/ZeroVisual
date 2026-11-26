#version 120

// the texture coordinate holds the position relative to the rectangle center, in GUI units
varying vec2 localPos;
varying vec4 vertexColor;

void main() {
    gl_Position = ftransform();
    localPos = gl_MultiTexCoord0.xy;
    vertexColor = gl_Color;
}
