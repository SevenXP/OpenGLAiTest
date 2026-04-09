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

class CubeRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // Matrix arrays for transformations
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mVPMatrix = FloatArray(16) // Combined Model-View-Projection matrix

    private var mProgram: Int = 0
    private var mPositionHandle: Int = 0
    private var mColorHandle: Int = 0
    private var mMatrixHandle: Int = 0

    // Cube vertex data: x, y, z, r, g, b
    private val cubeCoords = floatArrayOf(
        -0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f, // Front top left
        -0.5f, -0.5f,  0.5f,  1.0f, 0.0f, 0.0f, // Front bottom left
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f, 0.0f, // Front bottom right
         0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f, // Front top right

        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f, // Back top left
        -0.5f, -0.5f, -0.5f,  0.0f, 1.0f, 0.0f, // Back bottom left
         0.5f, -0.5f, -0.5f,  0.0f, 1.0f, 0.0f, // Back bottom right
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f, // Back top right

        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f, 1.0f, // Left top back
        -0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f, // Left top front
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f, // Left bottom front
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, 1.0f, // Left bottom back

         0.5f,  0.5f, -0.5f,  1.0f, 1.0f, 0.0f, // Right top back
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f, 0.0f, // Right top front
         0.5f, -0.5f,  0.5f,  1.0f, 1.0f, 0.0f, // Right bottom front
         0.5f, -0.5f, -0.5f,  1.0f, 1.0f, 0.0f, // Right bottom back

        -0.5f, -0.5f, -0.5f,  0.0f, 1.0f, 1.0f, // Bottom back left
        -0.5f, -0.5f,  0.5f,  0.0f, 1.0f, 1.0f, // Bottom front left
         0.5f, -0.5f,  0.5f,  0.0f, 1.0f, 1.0f, // Bottom front right
         0.5f, -0.5f, -0.5f,  0.0f, 1.0f, 1.0f, // Bottom back right

        -0.5f,  0.5f, -0.5f,  1.0f, 0.0f, 1.0f, // Top back left
        -0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 1.0f, // Top front left
         0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 1.0f, // Top front right
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f, 1.0f  // Top back right
    )

    private val drawOrder = shortArrayOf(
        0, 1, 2, 0, 2, 3, // front
        4, 5, 6, 4, 6, 7, // back
        8, 9, 10, 8, 10, 11, // left
        12, 13, 14, 12, 14, 15, // right
        16, 17, 18, 16, 18, 19, // bottom
        20, 21, 22, 20, 22, 23  // top
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: java.nio.ShortBuffer

    private var angleX = 0f
    private var angleY = 0f

    private val vertexShaderCode = """
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        uniform mat4 u_Matrix;
        varying vec4 v_Color;
        void main() {
            gl_Position = u_Matrix * a_Position;
            v_Color = a_Color;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 v_Color;
        void main() {
            gl_FragColor = v_Color;
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Initialize buffers
        vertexBuffer = ByteBuffer.allocateDirect(cubeCoords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(cubeCoords)
                position(0)
            }
        }

        indexBuffer = ByteBuffer.allocateDirect(drawOrder.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(drawOrder)
                position(0)
            }
        }

        // Compile shaders and link program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "a_Position")
        mColorHandle = GLES20.glGetAttribLocation(mProgram, "a_Color")
        mMatrixHandle = GLES20.glGetUniformLocation(mProgram, "u_Matrix")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()

        // Set up projection matrix using android.opengl.Matrix
        // perspectiveM(matrix, fovy, aspect, zNear, zFar)
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, ratio, 0.1f, 100f)

        // Set up view matrix: camera at (0,0,-3), looking at origin (0,0,0)
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Update rotation angles for animation
        angleX += 0.5f
        angleY += 0.8f

        // Set up model matrix (Identity -> Rotate X -> Rotate Y)
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.rotateM(mModelMatrix, 0, angleX, 1f, 0f, 0f)
        Matrix.rotateM(mModelMatrix, 0, angleY, 0f, 1f, 0f)

        // Combine matrices: MVP = Projection * View * Model
        // First combine View and Model
        Matrix.multiplyMM(mVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        // Then combine with Projection
        Matrix.multiplyMM(mVPMatrix, 0, mProjectionMatrix, 0, mVPMatrix, 0)

        GLES20.glUseProgram(mProgram)

        // Pass the combined MVP matrix to the shader
        GLES20.glUniformMatrix4fv(mMatrixHandle, 1, false, mVPMatrix, 0)

        // Position attribute: stride is 6 * 4 bytes (x,y,z,r,g,b), offset 0
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(mPositionHandle)
        GLES20.glVertexAttribPointer(mPositionHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, vertexBuffer)

        // Color attribute: stride is 6 * 4 bytes, offset 3 * 4 bytes (after x,y,z)
        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(mColorHandle)
        GLES20.glVertexAttribPointer(mColorHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, vertexBuffer)

        // Draw the cube using indices
        indexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, indexBuffer)

        GLES20.glDisableVertexAttribArray(mPositionHandle)
        GLES20.glDisableVertexAttribArray(mColorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e("CubeRenderer", "Could not compile shader $type: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compilation failed")
            }
        }
    }
}
