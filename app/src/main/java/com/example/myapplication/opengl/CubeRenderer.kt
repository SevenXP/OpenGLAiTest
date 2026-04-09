package com.example.myapplication.opengl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CubeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // Vertex Shader Source
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec4 vColor;
        varying vec4 _vColor;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            _vColor = vColor;
        }
    """.trimIndent()

    // Fragment Shader Source
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 _vColor;
        void main() {
            gl_FragColor = _vColor;
        }
    """.trimIndent()

    // Matrix arrays for transformations (as requested)
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16) // This will hold the final combined MVP matrix

    // Buffers for vertex data
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    // OpenGL program and handles
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    // Rotation angle for animation
    private var mAngle = 0f

    // FPS Control
    private var mLastFrameTimeNanos: Long = 0
    private val targetFrameTimeNanos: Long = 1_000_000_000L / 60 // ~16.6ms for 60 FPS

    /**
     * Cube vertex coordinates (x, y, z)
     */
    private val cubeCoords = floatArrayOf(
        -0.5f,  0.5f,  0.5f,   // 0: top-left-front
        -0.5f, -0.5f,  0.5f,   // 1: bottom-left-front
         0.5f, -0.5f,  0.5f,   // 2: bottom-right-front
         0.5f,  0.5f,  0.5f,   // 3: top-right-front
        -0.5f,  0.5f, -0.5f,   // 4: top-left-back
        -0.5f, -0.5f, -0.5f,   // 5: bottom-left-back
         0.5f, -0.5f, -0.5f,   // 6: bottom-right-back
         0.5f,  0.5f, -0.5f    // 7: top-right-back
    )

    /**
     * Color values for each vertex (r, g, b, a)
     */
    private val cubeColors = floatArrayOf(
        1.0f, 0.0f, 0.0f, 1.0f, // Red
        0.0f, 1.0f, 0.0f, 1.0f, // Green
        0.0f, 0.0f, 1.0f, 1.0f, // Blue
        1.0f, 1.0f, 0.0f, 1.0f, // Yellow
        1.0f, 0.0f, 1.0f, 1.0f, // Magenta
        0.0f, 1.0f, 1.0f, 1.0f, // Cyan
        1.0f, 1.0f, 1.0f, 1.0f, // White
        0.5f, 0.5f, 0.5f, 1.0f  // Gray
    )

    /**
     * Indices for the 12 triangles forming the cube faces (6 faces * 2 triangles/face * 3 vertices/triangle = 36 indices)
     */
    private val cubeIndices = shortArrayOf(
        0, 1, 2,      0, 2, 3,   // Front face
        5, 4, 7,      5, 7, 6,   // Back face
        4, 0, 3,      4, 3, 7,   // Top face
        5, 1, 2,      5, 2, 6,   // Bottom face
        4, 5, 1,      4, 1, 0,   // Left face
        3, 2, 6,      3, 6, 7    // Right face
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Enable depth testing to ensure correct occlusion of faces
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        // Set the background clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Initialize vertex position buffer
        vertexBuffer = ByteBuffer.allocateDirect(cubeCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(cubeCoords)
                position(0)
            }
        }

        // Initialize color buffer
        colorBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(cubeColors)
                position(0)
            }
        }

        // Initialize index buffer
        indexBuffer = ByteBuffer.allocateDirect(cubeIndices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(cubeIndices)
                position(0)
            }
        }

        // Compile shaders and create the OpenGL ES program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        if (vertexShader == 0) {
            Log.e("CubeRenderer", "Error compiling vertex shader")
        }

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        if (fragmentShader == 0) {
            Log.e("CubeRenderer", "Error compiling fragment shader")
        }

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                Log.e("CubeRenderer", "Error linking program: ${GLES20.glGetProgramInfoLog(it)}")
            }
        }

        // Retrieve handles to shader attributes and uniforms
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Set the viewport to match the surface dimensions
        GLES20.glViewport(0, 0, width, height)

        // Compute and set projection matrix using perspective projection
        val ratio = width.toFloat() / height.toFloat()
        // Use 45 degrees FOV for a more natural view
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, ratio, 0.1f, 10.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear color and depth buffers
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Use the shader program
        GLES20.glUseProgram(program)

        // 1. Setup View Matrix using setLookAtM
        // Camera is at (0, 0, -5), looking towards the origin
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, -5f,  // Eye position
                         0f, 0f, 0f,    // Look-at target point
                         0f, 1f, 0f)    // Up vector

        // 2. Create Model Matrix for rotation animation
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Rotate around Y axis
        Matrix.rotateM(modelMatrix, 0, mAngle, 0f, 1f, 0f)
        // Also rotate around X axis to make it look more dynamic
        Matrix.rotateM(modelMatrix, 0, mAngle * 0.5f, 1f, 0f, 0f)

        // 3. Combine matrices: Projection * View * Model
        // First, multiply Projection and View to get a temporary matrix (P*V) in mMatrix
        Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        // Then, multiply (P*V) by the Model matrix to get final MVP: (P*V)*M
        Matrix.multiplyMM(mMatrix, 0, mMatrix, 0, modelMatrix, 0)

        // 4. Pass the combined MVP matrix to the shader's uniform variable
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMatrix, 0)

        // 5. Set and enable the vertex position attribute
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        // 6. Set and enable the vertex color attribute
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 16, colorBuffer)

        // 7. Perform the actual draw call using indices
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cubeIndices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        // Disable attribute arrays to clean up state
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)

        // Increment rotation angle for the next frame's animation
        mAngle += 0.5f
    }

    /**
     * Helper function to compile a shader from source code
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}