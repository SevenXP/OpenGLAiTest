# 3D OpenGL Cube Renderer (Android)

## Description
This Android application renders a rotating 3D cube using OpenGL ES with proper matrix transformations. The cube is displayed at the correct size to fit perfectly on any screen and animates with smooth rotation around two axes.

## Features
- **3D Cube Rendering**: A complete cube with all 6 faces rendered using OpenGL ES 2.0
- **Matrix Transformations**: Proper implementation of Model, View, and Projection matrices
- **Automatic Screen Scaling**: The cube automatically scales to fit any screen size (50% of the smaller dimension)
- **Smooth Animation**: Continuous rotation around X and Y axes at ~60 FPS
- **Colorful Faces**: Each face has a different color for better visualization:
  - Front (+Z): Blue
  - Back (-Z): Red
  - Top (+Y): Green
  - Bottom (-Y): Orange
  - Right (+X): Cyan
  - Left (-X): Magenta

## File Structure
```
app/
├── src/main/
│   ├── java/com/example/myapplication/
│   │   ├── MainActivity.kt          # Main activity with GLSurfaceView setup
│   │   ├── data/
│   │   │   └── VertexData.kt        # Cube vertex geometry (optional)
│   │   └── opengl/
│   │       ├── CubeRenderer.kt      # OpenGL ES renderer implementation
│   │       └── GLRenderer.kt        # Alternative renderer class (for testing)
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml    # Simple FrameLayout for SurfaceView
│   │   ├── shaders/
│   │   │   ├── vertex.glsl          # Vertex shader source code
│   │   │   └── fragment.glsl        # Fragment shader source code
│   │   └── values/
│   │       └── strings.xml          # App resources (optional)
│   └── AndroidManifest.xml          # Application manifest
├── build.gradle.kts                  # Module-level Gradle configuration
└── settings.gradle.kts               # Project-level Gradle configuration
```

## Building the Application

### Prerequisites
- Android Studio (or command-line with JDK 11+)
- Java Development Kit 11 or higher
- Android SDK with minimum API level 24 (Android 7.0)

### Command Line Build
```bash
cd /Users/sevenxp_mac/Desktop/ProgectTest/MyApplication
./gradlew assembleDebug
```

### Android Studio
1. Open the project in Android Studio
2. Sync Gradle files
3. Build -> Make Project or Ctrl+F9 (Windows/Linux) / Cmd+K (Mac)

## Running on Device or Emulator
1. Connect an Android device via USB OR start an emulator
2. Build and run from Android Studio
3. Alternatively: `./gradlew installDebug`

## How It Works

### Matrix Transformations Explained
The rendering pipeline uses three transformation matrices:

1. **Projection Matrix**: Converts 3D world coordinates to 2D screen coordinates using perspective projection:
   - Field of view: 45 degrees
   - Near plane: 0.1, Far plane: 100.0
   - Creates depth effect where objects closer appear larger

2. **View Matrix**: Positions the camera in 3D space:
   - Camera looks at origin (0,0,0) from position (5,0,0)
   - Provides perspective on the cube

3. **Model Matrix**: Transforms and scales the cube:
   - Scales cube to proper size based on screen dimensions
   - Applies rotation around X and Y axes for animation

### Rendering Loop
1. `onSurfaceCreated()`: Initialize OpenGL context, shaders, and vertex buffers
2. `onSurfaceChanged()`: Update viewport when screen size changes
3. `onDrawFrame()` called continuously:
   - Clear color and depth buffers
   - Set up projection matrix
   - Calculate proper cube size for current screen
   - Apply camera/view transformations
   - Apply rotation transformations (animation)
   - Draw cube using triangle strips (efficient rendering)

### Shader Information

**Vertex Shader (vertex.glsl):**
```glsl
# Input: Vertex positions and texture coordinates
# Uniforms: Model, View, Projection matrices
# Output: Clip-space position for rasterization
```

**Fragment Shader (fragment.glsl):**
```glsl
# Calculates lighting based on texture coordinates
# Applies checker pattern effect for visual interest
# Outputs final RGBA color
```

## Key Code Sections

### Cube Geometry (CubeRenderer.kt)
```kotlin
// 24 vertices total (6 faces × 4 vertices each)
object Geometry {
    val vertexData: FloatArray = FloatArray(TOTAL_VERTICES * 5)
    // Each vertex stores: x, y, z, u, v (position + texture coords)
}
```

### Screen-Fit Calculation
```kotlin
fun calculateCubeSize(screenWidth: Int, screenHeight: Int): Float {
    val minDim = minOf(screenWidth, screenHeight).toFloat()
    return (minDim * 0.5f / CUBE_SIZE) // Fits 50% of smaller dimension
}
```

### Rotation Animation Thread
```kotlin
private fun startAnimationThread() {
    val thread = object : Thread() {
        override fun run() {
            while (isAlive) {
                rotationX += 2.0f
                rotationY += 3.0f
                Thread.sleep(16) // ~60 FPS
            }
        }
    }.apply { start() }
}
```

## OpenGL ES 2.0 Concepts Used
- **Vertex Array Object (VAO)**: Efficiently manages vertex attributes
- **Shader Program**: Custom GLSL code for vertex and fragment processing
- **Matrix Uniforms**: Pass transformation data from CPU to GPU
- **GL_TRIANGLE_STRIP**: Efficient primitive type for polygon rendering

## Troubleshooting
- **Cube not visible**: Check OpenGL initialization logs, ensure shaders compiled successfully
- **Wrong cube size**: Verify `calculateCubeSize()` returns positive value
- **No rotation**: Ensure animation thread is running and updateTransforms() called
- **Black screen**: Check depth testing enabled (`glEnable(GL10.GL_DEPTH_TEST)`)

## License
This code is provided for educational purposes.
