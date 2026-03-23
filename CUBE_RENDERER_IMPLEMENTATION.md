# CubeRenderer Implementation Guide

## Overview

This document explains the implementation of a 3D cube renderer using Android OpenGL ES 2.0 with proper matrix transformations.

## Core Requirements

✅ **CubeRenderer class for 3D cube rendering**
✅ **android.opengl.Matrix class for all matrix operations**
✅ **android.opengl.GLES20 for OpenGL ES 2.0 rendering**
✅ **Proper matrix transformations: projection, view, and model matrices**
✅ **6 faces (12 triangles) with vertex coordinates, colors, and indices**

## Matrix Operations Sequence

### 1. Projection Matrix (onSurfaceChanged)

```kotlin
Matrix.setIdentityM(mProjectionMatrix, 0)
Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspectRatio, 0.1f, 100f)
```

**Purpose**: Defines the viewing frustum (camera lens)
- `45f`: Field of view in degrees
- `aspectRatio`: Width/height ratio of the screen
- `0.1f`: Near clipping plane (objects closer than this are clipped)
- `100f`: Far clipping plane (objects farther than this are clipped)

### 2. View Matrix (onSurfaceChanged)

```kotlin
Matrix.setIdentityM(mViewMatrix, 0)
Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
```

**Purpose**: Positions the camera in 3D space
- Eye position: (0, 0, 5) - camera is 5 units in front of origin
- Target position: (0, 0, 0) - looking at the origin
- Up vector: (0, 1, 0) - defines "up" direction (positive Y)

### 3. Model Matrix (onDrawFrame)

```kotlin
Matrix.setIdentityM(mMatrix, 0)
Matrix.rotateM(mMatrix, 0, rotationX, 1f, 0f, 0f)
Matrix.rotateM(mMatrix, 0, rotationY, 0f, 1f, 0f)
```

**Purpose**: Applies transformations to the cube itself
- `rotationX`: Continuous rotation around X-axis
- `rotationY`: Continuous rotation around Y-axis
- Angles are in degrees

### 4. Matrix Multiplication (onDrawFrame)

```kotlin
Matrix.multiplyMM(mMatrix, 0, mViewMatrix, 0, mMatrix, 0)
Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, mMatrix, 0)
```

**Purpose**: Combines all matrices into final MVP (Model-View-Projection) matrix

**Important**: Matrix multiplication order is **right-to-left**:
- First multiply: view × model → transforms model to world space
- Second multiply: projection × (view × model) → transforms to clip space

## Cube Structure

### Vertices (8 points in 3D space)

```
-1, -1, -1  ← Bottom-left-back
 1, -1, -1  ← Bottom-right-back
 1,  1, -1  ← Top-right-back
-1,  1, -1  ← Top-left-back
-1, -1,  1  ← Bottom-left-front
 1, -1,  1  ← Bottom-right-front
 1,  1,  1  ← Top-right-front
-1,  1,  1  ← Top-left-front
```

### Colors (RGBA for each vertex)

```
Red:    (1, 0, 0, 1)
Green:  (0, 1, 0, 1)
Blue:   (0, 0, 1, 1)
Yellow: (1, 1, 0, 1)
Magenta:(1, 0, 1, 1)
Cyan:   (0, 1, 1, 1)
White:  (1, 1, 1, 1)
Black:  (0, 0, 0, 1)
```

### Indices (12 triangles forming 6 faces)

```
Face 1: 0, 1, 2, 0, 2, 3  ← Back face
Face 2: 4, 5, 6, 4, 6, 7  ← Front face
Face 3: 0, 1, 5, 0, 5, 4  ← Bottom face
Face 4: 2, 3, 7, 2, 7, 6  ← Top face
Face 5: 1, 2, 6, 1, 6, 5  ← Right face
Face 6: 0, 3, 7, 0, 7, 4  ← Left face
```

## OpenGL ES 2.0 Pipeline

### Vertex Shader

```glsl
attribute vec4 aPosition;    // Input vertex position
attribute vec4 aColor;       // Input vertex color
uniform mat4 uMVPMatrix;     // Combined transformation matrix
varying vec4 vColor;         // Output color to fragment shader

void main() {
    gl_Position = uMVPMatrix * aPosition;  // Apply transformation
    vColor = aColor;                        // Pass color to fragment shader
}
```

**Key Operations**:
- `uMVPMatrix * aPosition`: Applies all transformations to position
- `vColor`: Passes vertex color to fragment shader for interpolation

### Fragment Shader

```glsl
precision mediump float;
varying vec4 vColor;  // Interpolated color from vertex shader

void main() {
    gl_FragColor = vColor;  // Output the interpolated color
}
```

**Key Operations**:
- `gl_FragColor`: Outputs final color for each pixel

## Buffer Management

### FloatBuffer for Vertices and Colors

```kotlin
vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .apply { put(vertices); rewind() }
```

**Why native order?**
- Native byte order ensures optimal performance on the device's CPU
- Avoids byte-swapping operations during rendering

**Why rewind()?**
- Resets buffer position to 0 for each draw call
- Ensures correct data is read from the beginning

### ShortBuffer for Indices

```kotlin
indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
    .order(ByteOrder.nativeOrder())
    .asShortBuffer()
    .apply { put(indices); rewind() }
```

**Why ShortBuffer?**
- Indices are 16-bit values (0-65535)
- More memory-efficient than using IntBuffer

## Rendering Pipeline (onDrawFrame)

```kotlin
override fun onDrawFrame(gl: GL10?) {
    // 1. Clear screen
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
    GLES20.glClearColor(0f, 0f, 0f, 1f)

    // 2. Update rotation
    rotationX += 0.5f
    rotationY += 0.3f

    // 3. Apply model transformations
    Matrix.setIdentityM(mMatrix, 0)
    Matrix.rotateM(mMatrix, 0, rotationX, 1f, 0f, 0f)
    Matrix.rotateM(mMatrix, 0, rotationY, 0f, 1f, 0f)

    // 4. Combine matrices: view × model
    Matrix.multiplyMM(mMatrix, 0, mViewMatrix, 0, mMatrix, 0)

    // 5. Combine matrices: projection × (view × model)
    Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, mMatrix, 0)

    // 6. Use shader program
    GLES20.glUseProgram(program)

    // 7. Enable vertex attributes
    GLES20.glEnableVertexAttribArray(positionHandle)
    GLES20.glEnableVertexAttribArray(colorHandle)

    // 8. Set vertex data
    GLES20.glVertexAttribPointer(
        positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer
    )
    GLES20.glVertexAttribPointer(
        colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer
    )

    // 9. Pass MVP matrix to shader
    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMatrix, 0)

    // 10. Draw cube
    GLES20.glDrawElements(
        GLES20.GL_TRIANGLES,
        indexBuffer.limit(),
        GLES20.GL_UNSIGNED_SHORT,
        indexBuffer
    )

    // 11. Disable vertex attributes
    GLES20.glDisableVertexAttribArray(positionHandle)
    GLES20.glDisableVertexAttribArray(colorHandle)
}
```

## Shader Compilation and Linking

### Vertex Shader Compilation

```kotlin
private fun loadShader(type: Int, shaderCode: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, shaderCode)
    GLES20.glCompileShader(shader)

    val compileStatus = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
    if (compileStatus[0] != GLES20.GL_TRUE) {
        Log.e(TAG, "Could not compile shader: ${GLES20.glGetShaderInfoLog(shader)}")
    }

    return shader
}
```

### Program Linking

```kotlin
val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

program = GLES20.glCreateProgram()
GLES20.glAttachShader(program, vertexShader)
GLES20.glAttachShader(program, fragmentShader)
GLES20.glLinkProgram(program)

val linkStatus = IntArray(1)
GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
if (linkStatus[0] != GLES20.GL_TRUE) {
    Log.e(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
}
```

## Key Matrix Methods Used

| Method | Purpose | Parameters |
|--------|---------|------------|
| `Matrix.setIdentityM()` | Reset matrix to identity | matrix, offset |
| `Matrix.perspectiveM()` | Set perspective projection | matrix, offset, fovy, aspect, near, far |
| `Matrix.setLookAtM()` | Set camera position | matrix, offset, eye, center, up |
| `Matrix.rotateM()` | Apply rotation | matrix, offset, angle, x, y, z |
| `Matrix.multiplyMM()` | Multiply two matrices | result, resultOffset, a, aOffset, b, bOffset |

## Verification Points

✅ All matrix operations use `android.opengl.Matrix`
✅ No external math libraries imported
✅ Matrix multiplication order is correct (projection × view × model)
✅ Proper buffer management for performance
✅ Full Kotlin implementation with null safety
✅ Proper error handling for OpenGL operations

## Performance Considerations

1. **Buffer Reuse**: Buffers are allocated once and reused for all draw calls
2. **Native Byte Order**: Optimizes CPU access patterns
3. **Shader Compilation**: Shaders are compiled once during `onSurfaceCreated()`
4. **Matrix Precomputation**: View and projection matrices are set once during `onSurfaceChanged()`
5. **Attribute Binding**: Vertex attributes are enabled/disabled efficiently

## Common Issues and Solutions

### Issue: Cube appears distorted
**Solution**: Check aspect ratio calculation in `onSurfaceChanged()`

### Issue: Cube is not visible
**Solution**: Verify near/far clipping planes and camera position

### Issue: Colors are wrong
**Solution**: Check color buffer order and vertex data alignment

### Issue: Performance issues
**Solution**: Ensure buffers use native byte order and are not reallocated each frame

## Conclusion

This implementation demonstrates a complete 3D cube renderer using pure Android OpenGL ES 2.0 with proper matrix transformations. All matrix operations use only the `android.opengl.Matrix` class, ensuring compatibility and performance without external dependencies.
