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

    private val TAG = "CubeRenderer"

    // Matrix arrays for transformations
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)

    // Shader program
    private var mProgram: Int = 0

    // Vertex and fragment shader source code
    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;
        uniform mat4 uMVPMatrix;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vColor = aColor;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()

    // Cube vertex data
    private val cubeVertices = floatArrayOf(
        // Front face (z = 1.0)
        -1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f,
        // Back face (z = -1.0)
        -1.0f, -1.0f, -1.0f, -1.0f,  1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f, -1.0f,
        // Top face (y = 1.0)
        -1.0f,  1.0f, -1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,
        // Bottom face (y = -1.0)
        -1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f,
        // Right face (x = 1.0)
         1.0f, -1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,  1.0f,
        // Left face (x = -1.0)
        -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,  1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f
    )

    // Cube color data (one color per vertex)
    private val cubeColors = floatArrayOf(
        // Front face - Red
        1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        // Back face - Green
        0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 1.0f,
        // Top face - Blue
        0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
        // Bottom face - Yellow
        1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        // Right face - Cyan
        0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
        // Left face - Magenta
        1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f
    )

    // Cube indices (12 triangles = 36 indices)
    private val cubeIndices = shortArrayOf(
        0, 1, 2, 0, 2, 3,    // Front face
        4, 5, 6, 4, 6, 7,    // Back face
        8, 9, 10, 8, 10, 11, // Top face
        12, 13, 14, 12, 14, 15, // Bottom face
        16, 17, 18, 16, 18, 19, // Right face
        20, 21, 22, 20, 22, 23  // Left face
    )

    // Buffer objects
    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null

    // Shader locations
    private var aPositionLocation: Int = 0
    private var aColorLocation: Int = 0
    private var uMVPMatrixLocation: Int = 0

    // Animation state
    private var rotationX = 0f
    private var rotationY = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated: OpenGL ES 2.0 initialized")

        // Set up vertex and fragment shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // Create program
        mProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(mProgram, vertexShader)
        GLES20.glAttachShader(mProgram, fragmentShader)
        GLES20.glLinkProgram(mProgram)

        // Check for link errors
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GLES20.GL_FALSE) {
            Log.e(TAG, "Error linking shader program: ${GLES20.glGetProgramInfoLog(mProgram)}")
        }

        // Get shader attribute and uniform locations
        aPositionLocation = GLES20.glGetAttribLocation(mProgram, "aPosition")
        aColorLocation = GLES20.glGetAttribLocation(mProgram, "aColor")
        uMVPMatrixLocation = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

        // Set up vertex buffers
        vertexBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(cubeVertices)
            .position(0) as FloatBuffer?

        colorBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(cubeColors)
            .position(0) as FloatBuffer?

        indexBuffer = ByteBuffer.allocateDirect(cubeIndices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(cubeIndices)
            .position(0) as ShortBuffer?

        // Set clear color (light gray background)
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: width=$width, height=$height")

        // Set the viewport
        GLES20.glViewport(0, 0, width, height)

        // Calculate aspect ratio
        val aspect = if (height > 0) width.toFloat() / height else 1.0f

        // Set up projection matrix using Matrix.perspectiveM()
        // FOV: 45 degrees, aspect ratio, near plane: 0.1, far plane: 100.0
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspect, 0.1f, 100.0f)

        // Set up view matrix using Matrix.setLookAtM()
        // Camera position: (0, 0, -5), looking at (0, 0, 0), up vector: (0, 1, 0)
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, -5f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear the color buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Use the shader program
        GLES20.glUseProgram(mProgram)

        // Update rotation
        rotationX += 0.5f
        rotationY += 0.7f

        // Reset model matrix
        Matrix.setIdentityM(mModelMatrix, 0)

        // Apply rotation using Matrix.rotateM()
        // Rotate around X axis
        Matrix.rotateM(mModelMatrix, 0, rotationX, 1f, 0f, 0f)
        // Rotate around Y axis
        Matrix.rotateM(mModelMatrix, 0, rotationY, 0f, 1f, 0f)

        // Combine matrices: projection * view * model
        // First multiply view and model matrices
        Matrix.multiplyMM(mMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        // Then multiply with projection matrix
        Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, mMatrix, 0)

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Set up vertex attributes
        GLES20.glVertexAttribPointer(aPositionLocation, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPositionLocation)

        GLES20.glVertexAttribPointer(aColorLocation, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glEnableVertexAttribArray(aColorLocation)

        // Pass the combined matrix to the shader
        GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mMatrix, 0)

        // Draw the cube
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cubeIndices.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        // Disable vertex attributes
        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aColorLocation)
    }

    /**
     * Helper method to compile a shader
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Check for compilation errors
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == GLES20.GL_FALSE) {
            Log.e(TAG, "Error compiling shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }
}