#version 300 es
precision highp float;

// Uniforms for lighting and color
uniform vec4 uClearColor;    // Background clear color (r, g, b, a)
uniform float uLightIntensity;    // Light source intensity
uniform float uAmbientStrength;   // Ambient light strength

// Input from vertex shader
in vec2 vTexCoord;            // Texture coordinate
in vec3 vColor;               // Vertex color

// Output to screen
out highp vec4 FragColor;     // Final fragment color

void main() {
    // Calculate simple lighting effect based on texture coordinates
    // Create a gradient effect for visual interest
    float lightFactor = uLightIntensity * (0.5 + 0.5 * sin(vTexCoord.y * 3.14159));
    
    // Combine ambient and directional light
    float finalLight = uAmbientStrength + lightFactor;
    
    // Apply lighting to base color
    vec3 finalColor = vColor * finalLight;
    
    // Add texture-based variation (checkers pattern)
    float checkerPattern = sin(vTexCoord.x * 6.28) * sin(vTexCoord.y * 6.28);
    finalColor += checkerPattern * 0.1;
    
    // Output the final color
    FragColor = vec4(finalColor, 1.0);
}
