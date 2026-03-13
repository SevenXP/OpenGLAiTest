#version 300 es
precision highp float;

// Uniforms for matrix transformations
uniform mat4 uModelMatrix;   // Object's position, rotation, scale
uniform mat4 uViewMatrix;    // Camera position and orientation
uniform mat4 uProjectionMatrix; // Perspective/projection transformation

// Vertex attributes
in vec3 aPosition;            // 3D vertex position (x, y, z)
in vec2 aTexCoord;            // Texture coordinates (u, v)

// Output to fragment shader
out vec2 vTexCoord;           // Pass texture coord to fragment shader
out vec3 vColor;              // Vertex color for lighting

void main() {
    // Transform vertex: Model -> View -> Projection
    gl_Position = uProjectionMatrix * uViewMatrix * uModelMatrix * vec4(aPosition, 1.0);
    
    // Pass texture coordinate and color to fragment shader
    vTexCoord = aTexCoord;
    vColor = vec3(1.0, 1.0, 1.0); // White default color
}
