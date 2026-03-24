package com.example.myapplication.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.opengles.GL10

class CubeRenderer : GLSurfaceView.Renderer {
    // Matrix variables
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)  // Combined matrix for shader

    // Cube geometry data
    private val cubeVertices = floatArrayOf(
        // Front face
        -0.5f, -0.5f,  0.5f,
        0.5f, -0.5f,  0.5f,
        0.5f,  0.5f,  0.5f,
        -0.5f,  0.5f,  0.5f,

        // Back face
        -0.5f, -0.5f, -0.5f,
        0.5f, -0.5f, -0.5f,
        0.5f,  0.5f, -0.5f,
        -0.5f,  0.5f, -0.5f
    )

    private val cubeColors = floatArrayOf(
        // Front face (red)
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,
        1.0f, 0.0f, 0.0f, 1.0f,

        // Back face (green)
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f
    )

    private val cubeIndices = shortArrayOf(
        // Front face
        0, 1, 2, 0, 2, 3,
        // Back face
        4, 5, 6, 4, 6, 7,
        // Right face
        1, 5, 6, 1, 6, 2,
        // Left face
        0, 3, 7, 0, 7, 4,
        // Top face
        2, 3, 7, 2, 7, 6,
        // Bottom face
        0, 1, 5, 0, 5, 4
    )

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null

    // Shader variables
    private var program: Int = -1
    private var positionHandle: Int = -1
    private var colorHandle: Int = -1
    private var mvpMatrixHandle: Int = -1

    // Rotation parameters
    private var rotationAngle: Float = 0f

    // Shader code
    private val vertexShaderCode =
        """
        attribute vec4 vPosition;
        attribute vec4 aColor;

        uniform mat4 uMVPMatrix;

        varying vec4 vColor;

        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vColor = aColor;
        }
        """

    private val fragmentShaderCode =
        """
        precision mediump float;

        varying vec4 vColor;

        void main() {
            gl_FragColor = vColor;
        }
        """

    override fun onSurfaceCreated(unused: GL10, config: javax.microedition.khronos.egl.EGLConfig) {
        // Set background color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Initialize buffers for cube data
        vertexBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(cubeVertices)
                position(0)
            }
        }

        colorBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(cubeColors)
                position(0)
            }
        }

        indexBuffer = ByteBuffer.allocateDirect(cubeIndices.size * 2).run {
            order(ByteOrder.nativeOrder())
            asShortBuffer().apply {
                put(cubeIndices)
                position(0)
            }
        }

        // Load shaders and create program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)

            // Check for link errors
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                throw RuntimeException("Error linking program: ${GLES20.glGetProgramInfoLog(it)}")
            }
        }
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        // Set up perspective projection matrix (45° field of view, aspect ratio, near and far planes)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, ratio, 1f, 10f)

        // Set up camera/view matrix: eye position, look-at point, up vector
        Matrix.setLookAtM(
            mViewMatrix, 0,
            0f, 0f, -3f,   // Eye position
            0f, 0f, 0f,    // Look at point (center of cube)
            0f, 1f, 0f      // Up vector
        )
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Set identity matrix for model transformation
        Matrix.setIdentityM(mModelMatrix, 0)

        // Rotate the cube around Y axis for animation effect
        rotationAngle += 1.5f
        if (rotationAngle > 360) rotationAngle -= 360
        Matrix.rotateM(mModelMatrix, 0, rotationAngle, 0f, 1f, 0f)

        // Combine matrices: projection * view * model
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mMVPMatrix, 0, mModelMatrix, 0)

        // Prepare shader program for rendering
        GLES20.glUseProgram(program)

        // Get handles to the uniform and attribute variables in the shader
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        // Set the matrix uniform for the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        // Enable vertex arrays and set pointers to buffers
        GLES20.glEnableVertexAttribArray(positionHandle)
        vertexBuffer?.let { GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, it) }

        GLES20.glEnableVertexAttribArray(colorHandle)
        colorBuffer?.let { GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, it) }

        // Draw the cube using index buffer
        if (indexBuffer != null && vertexBuffer != null && colorBuffer != null) {
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                cubeIndices.size,
                GLES20.GL_UNSIGNED_SHORT,
                indexBuffer
            )
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            if (shader != 0) {
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)

                // Check for compile errors
                val compiled = IntArray(1)
                GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
                if (compiled[0] == 0) {
                    throw RuntimeException("Error compiling shader: ${GLES20.glGetShaderInfoLog(shader)}")
                }
            } else {
                throw RuntimeException("Could not create shader")
            }
        }
    }
}
