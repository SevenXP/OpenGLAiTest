package com.example.myapplication.opengl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CubeRenderer - Renders a 3D rotating cube using OpenGL ES 2.0
 * 
 * Matrix Operations (all from android.opengl.Matrix):
 * - Projection Matrix: Defines the viewing frustum (perspective)
 * - View Matrix: Positions and orients the camera
 * - Model Matrix: Transforms individual objects (rotation, translation, scale)
 * - Combined Matrix: projection × view × model
 */
class CubeRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    // Shader program ID
    private var programId = 0
    
    // Matrix arrays (16 floats each for 4x4 matrices)
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)  // Model-View-Projection combined matrix
    
    // Shader location handles
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    
    // Vertex buffers
    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    
    // Cube geometry data
    private val vertexCount = 36  // 12 triangles × 3 vertices each
    private val colorCount = 36   // Same as vertex count
    
    // Rotation angle for animation
    private var rotationAngle = 0f
    
    /**
     * Cube vertex coordinates (36 vertices = 12 triangles × 3 vertices)
     * Each triangle is defined by 3 vertices for proper rendering with glDrawArrays
     */
    private val vertices = floatArrayOf(
        // Front face (Z = +1.0) - 2 triangles, counter-clockwise from front
         1.0f, -1.0f,  1.0f,   // bottom-right
        -1.0f, -1.0f,  1.0f,   // bottom-left
        -1.0f,  1.0f,  1.0f,   // top-left
         1.0f, -1.0f,  1.0f,   // bottom-right (shared)
        -1.0f,  1.0f,  1.0f,   // top-left (shared)
         1.0f,  1.0f,  1.0f,   // top-right
        
        // Back face (Z = -1.0) - 2 triangles, counter-clockwise from back
        -1.0f, -1.0f, -1.0f,   // bottom-left
         1.0f, -1.0f, -1.0f,   // bottom-right
         1.0f,  1.0f, -1.0f,   // top-right
        -1.0f, -1.0f, -1.0f,   // bottom-left (shared)
         1.0f,  1.0f, -1.0f,   // top-right (shared)
        -1.0f,  1.0f, -1.0f,   // top-left
        
        // Top face (Y = +1.0) - 2 triangles, counter-clockwise from above
        -1.0f,  1.0f,  1.0f,   // front-left
         1.0f,  1.0f,  1.0f,   // front-right
         1.0f,  1.0f, -1.0f,   // back-right
        -1.0f,  1.0f,  1.0f,   // front-left (shared)
         1.0f,  1.0f, -1.0f,   // back-right (shared)
        -1.0f,  1.0f, -1.0f,   // back-left
        
        // Bottom face (Y = -1.0) - 2 triangles, counter-clockwise from below
         1.0f, -1.0f, -1.0f,   // back-right
        -1.0f, -1.0f, -1.0f,   // back-left
        -1.0f, -1.0f,  1.0f,   // front-left
         1.0f, -1.0f, -1.0f,   // back-right (shared)
        -1.0f, -1.0f,  1.0f,   // front-left (shared)
         1.0f, -1.0f,  1.0f,   // front-right
        
        // Right face (X = +1.0) - 2 triangles, counter-clockwise from right side
         1.0f, -1.0f,  1.0f,   // bottom-front
         1.0f, -1.0f, -1.0f,   // bottom-back
         1.0f,  1.0f, -1.0f,   // top-back
         1.0f, -1.0f,  1.0f,   // bottom-front (shared)
         1.0f,  1.0f, -1.0f,   // top-back (shared)
         1.0f,  1.0f,  1.0f,   // top-front
        
        // Left face (X = -1.0) - 2 triangles, counter-clockwise from left side
        -1.0f, -1.0f, -1.0f,   // bottom-back
        -1.0f, -1.0f,  1.0f,   // bottom-front
        -1.0f,  1.0f,  1.0f,   // top-front
        -1.0f, -1.0f, -1.0f,   // bottom-back (shared)
        -1.0f,  1.0f,  1.0f,   // top-front (shared)
        -1.0f,  1.0f, -1.0f    // top-back
    )
    
    /**
     * Colors for each vertex (RGB format)
     * Each face has a distinct color:
     * Front=Red, Back=Green, Top=Blue, Bottom=Yellow, Right=Cyan, Left=Magenta
     */
    private val colors = floatArrayOf(
        // Front face - Red (6 vertices × 2 triangles)
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        
        // Back face - Green (6 vertices × 2 triangles)
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        
        // Top face - Blue (6 vertices × 2 triangles)
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        0.0f, 0.0f, 1.0f, 1.0f,
        
        // Bottom face - Yellow (6 vertices × 2 triangles)
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0.0f, 1.0f,
        
        // Right face - Cyan (6 vertices × 2 triangles)
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        0.0f, 1.0f, 1.0f, 1.0f,
        
        // Left face - Magenta (6 vertices × 2 triangles)
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f,
        1.0f, 0.0f, 1.0f, 1.0f
    )
    
    /**
     * Vertex Shader Source Code
     * Transforms vertex positions using the MVP matrix
     */
    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aColor;
        uniform mat4 uMVPMatrix;
        varying vec4 vColor;
        
        void main() {
            // Apply transformation matrix to position
            gl_Position = uMVPMatrix * aPosition;
            // Pass color to fragment shader
            vColor = aColor;
        }
    """.trimIndent()
    
    /**
     * Fragment Shader Source Code
     * Sets the final pixel color
     */
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        
        void main() {
            // Output the interpolated color
            gl_FragColor = vColor;
        }
    """.trimIndent()
    
    /**
     * Called when the surface is created - Initialize OpenGL ES resources
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d("CubeRenderer", "onSurfaceCreated called")
        
        // Enable depth testing for 3D rendering
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // Compile and link shaders
        programId = createShaderProgram()
        
        // Get shader handle locations
        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(programId, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        
        // Initialize vertex buffer
        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer!!.put(vertices).position(0)
        
        // Initialize color buffer
        val cb = ByteBuffer.allocateDirect(colors.size * 4)
        cb.order(ByteOrder.nativeOrder())
        colorBuffer = cb.asFloatBuffer()
        colorBuffer!!.put(colors).position(0)
    }
    
    /**
     * Called when the surface size changes - Set up projection and view matrices
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d("CubeRenderer", "onSurfaceChanged: ${width}x${height}")
        
        // Calculate aspect ratio for correct perspective
        val aspectRatio = if (height != 0) width.toFloat() / height.toFloat() else 1.0f
        
        // ============================================
        // PROJECTION MATRIX SETUP using android.opengl.Matrix
        // ============================================
        // Creates a perspective projection matrix that defines:
        // - Field of view (45 degrees)
        // - Aspect ratio (width/height)
        // - Near clipping plane (1.0 units from camera)
        // - Far clipping plane (20.0 units from camera)
        Matrix.perspectiveM(mProjectionMatrix, 0, 
            45.0f, aspectRatio, 1.0f, 20.0f)
        
        Log.d("CubeRenderer", "Projection matrix set with FOV=45°, Aspect=$aspectRatio")
        
        // ============================================
        // VIEW MATRIX SETUP using android.opengl.Matrix
        // ============================================
        // Sets up the camera position and orientation:
        // - Camera position: (0, 0, 8) - 8 units back from origin
        // - Look-at point: (0, 0, 0) - center of scene
        // - Up vector: (0, 1, 0) - Y axis is up
        Matrix.setLookAtM(mViewMatrix, 0,
            0.0f, 0.0f, 8.0f,   // Camera position (eyeX, eyeY, eyeZ)
            0.0f, 0.0f, 0.0f,   // Look-at point (centerX, centerY, centerZ)
            0.0f, 1.0f, 0.0f    // Up vector (upX, upY, upZ)
        )
        
        Log.d("CubeRenderer", "View matrix set - Camera at (0,0,8) looking at origin")
    }
    
    /**
     * Called every frame - Apply transformations and render the cube
     */
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen with black background and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Use our shader program
        GLES20.glUseProgram(programId)
        
        // ============================================
        // MODEL MATRIX TRANSFORMATIONS using android.opengl.Matrix
        // ============================================
        // Update rotation angle for animation (1 degree per frame)
        rotationAngle += 1.0f
        if (rotationAngle > 360.0f) {
            rotationAngle -= 360.0f
        }
        
        // Start with identity matrix as base model matrix
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Apply Y-axis rotation (spinning the cube vertically)
        Matrix.rotateM(modelMatrix, 0, rotationAngle, 0.0f, 1.0f, 0.0f)
        
        // Apply X-axis rotation for more interesting tumbling effect
        Matrix.rotateM(modelMatrix, 0, rotationAngle * 0.5f, 1.0f, 0.0f, 0.0f)
        
        Log.v("CubeRenderer", "Model rotation: Y=${rotationAngle}°, X=${rotationAngle * 0.5}°")
        
        // ============================================
        // COMBINE MATRICES using android.opengl.Matrix
        // ============================================
        // Matrix multiplication order is crucial in OpenGL:
        // MVP = Projection × View × Model
        // This applies transformations right-to-left: model first, then view, then projection
        
        // Step 1: tempMatrix = Projection × View
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        
        // Step 2: mMVPMatrix = tempMatrix × Model (i.e., Projection × View × Model)
        Matrix.multiplyMM(mMVPMatrix, 0, tempMatrix, 0, modelMatrix, 0)
        
        Log.v("CubeRenderer", "MVP matrix computed: P × V × M")
        
        // Pass the combined MVP matrix to vertex shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)
        
        // ============================================
        // RENDER THE CUBE
        // ============================================
        // Bind vertex position data
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle,
            3,           // 3 components per vertex (x, y, z)
            GLES20.GL_FLOAT,
            false,       // Don't normalize
            0,           // Stride (0 = tightly packed)
            vertexBuffer!!
        )
        
        // Bind color data
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(
            colorHandle,
            4,           // 4 components per color (r, g, b, a)
            GLES20.GL_FLOAT,
            false,
            0,
            colorBuffer!!
        )
        
        // Draw the cube using triangles
        // Each face is 2 triangles, 6 faces = 12 triangles total (36 vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        
        // Disable vertex arrays after drawing (good practice)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    /**
     * Creates and links a shader program from vertex and fragment shaders
     */
    private fun createShaderProgram(): Int {
        // Compile vertex shader
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        if (vertexShader == 0) return 0
        
        // Compile fragment shader
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }
        
        // Create program object
        var program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(fragmentShader)
            GLES20.glDeleteShader(vertexShader)
            return 0
        }
        
        // Attach shaders to program
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        
        // Link the program
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("CubeRenderer", "Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            program = 0
        }
        
        // Delete compiled shaders (no longer needed after linking)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        
        return program
    }
    
    /**
     * Compiles a single shader and returns its ID
     */
    private fun compileShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader == 0) return 0
        
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e("CubeRenderer", "Could not compile shader ($shaderType): ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        
        return shader
    }
}
