package com.example.myapplication.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer that draws and animates a colored 3D cube.
 *
 * Matrix pipeline:
 * 1) Projection matrix (camera lens) is created in [onSurfaceChanged].
 * 2) View matrix (camera transform) is created in [onSurfaceChanged].
 * 3) Model matrix (object transform) is updated each frame in [onDrawFrame].
 * 4) Final MVP matrix = Projection * View * Model.
 */
class CubeRenderer : GLSurfaceView.Renderer {

    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var matrixHandle = 0

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    private var rotationAngle = 0f
    private var lastFrameTimeNs = 0L

    init {
        val vertexBytes = ByteBuffer
            .allocateDirect(CUBE_VERTICES.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        vertexBuffer = vertexBytes.asFloatBuffer().apply {
            put(CUBE_VERTICES)
            position(0)
        }

        val indexBytes = ByteBuffer
            .allocateDirect(CUBE_INDICES.size * SHORT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        indexBuffer = indexBytes.asShortBuffer().apply {
            put(CUBE_INDICES)
            position(0)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        limitTo60Fps()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.setIdentityM(mModelMatrix, 0)
        rotationAngle += 0.8f
        Matrix.rotateM(mModelMatrix, 0, rotationAngle, 1f, 1f, 0.5f)

        // mMatrix is used as final MVP = Projection * View * Model.
        Matrix.multiplyMM(mMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, mMatrix, 0)

        GLES20.glUseProgram(program)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            POSITION_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        vertexBuffer.position(POSITION_COMPONENT_COUNT)
        GLES20.glVertexAttribPointer(
            colorHandle,
            COLOR_COMPONENT_COUNT,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(colorHandle)

        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mMatrix, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            CUBE_INDICES.size,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        checkGlError("onDrawFrame")
    }

    private fun limitTo60Fps() {
        val nowNs = System.nanoTime()
        if (lastFrameTimeNs != 0L) {
            val frameDeltaNs = nowNs - lastFrameTimeNs
            val sleepNs = TARGET_FRAME_DURATION_NS - frameDeltaNs
            if (sleepNs > 0L) {
                val sleepMs = sleepNs / 1_000_000L
                val sleepRemainderNs = (sleepNs % 1_000_000L).toInt()
                Thread.sleep(sleepMs, sleepRemainderNs)
            }
        }
        lastFrameTimeNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val safeHeight = if (height == 0) 1 else height
        val ratio = width.toFloat() / safeHeight.toFloat()

        Matrix.perspectiveM(
            mProjectionMatrix,
            0,
            45f,
            ratio,
            1f,
            10f
        )

        Matrix.setLookAtM(
            mViewMatrix,
            0,
            0f,
            0f,
            7f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f
        )

        checkGlError("onSurfaceChanged")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.1f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        program = linkProgram(vertexShader, fragmentShader)

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        require(positionHandle >= 0) { "aPosition attribute not found." }
        require(colorHandle >= 0) { "aColor attribute not found." }
        require(matrixHandle >= 0) { "uMVPMatrix uniform not found." }
        checkGlError("onSurfaceCreated")
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        require(shader != 0) { "Could not create shader. type=$type" }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val shaderLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $shaderLog")
        }
        return shader
    }

    private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val linkedProgram = GLES20.glCreateProgram()
        require(linkedProgram != 0) { "Could not create OpenGL program." }

        GLES20.glAttachShader(linkedProgram, vertexShader)
        GLES20.glAttachShader(linkedProgram, fragmentShader)
        GLES20.glLinkProgram(linkedProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(linkedProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val programLog = GLES20.glGetProgramInfoLog(linkedProgram)
            GLES20.glDeleteProgram(linkedProgram)
            throw RuntimeException("Program link failed: $programLog")
        }
        return linkedProgram
    }

    private fun checkGlError(label: String) {
        var error = GLES20.glGetError()
        if (error == GLES20.GL_NO_ERROR) {
            return
        }
        val errors = mutableListOf<String>()
        while (error != GLES20.GL_NO_ERROR) {
            errors.add(error.toString())
            error = GLES20.glGetError()
        }
        throw RuntimeException("$label OpenGL error(s): ${errors.joinToString()}")
    }

    private companion object {
        const val TARGET_FRAME_DURATION_NS = 16_666_667L // 60 FPS
        const val FLOAT_SIZE_BYTES = 4
        const val SHORT_SIZE_BYTES = 2
        const val POSITION_COMPONENT_COUNT = 3
        const val COLOR_COMPONENT_COUNT = 4
        const val STRIDE_BYTES = (POSITION_COMPONENT_COUNT + COLOR_COMPONENT_COUNT) * FLOAT_SIZE_BYTES

        // 6 faces * 4 vertices per face. Each vertex: X, Y, Z, R, G, B, A.
        val CUBE_VERTICES = floatArrayOf(
            // Front (red)
            -1f, 1f, 1f, 1f, 0f, 0f, 1f,
            -1f, -1f, 1f, 1f, 0f, 0f, 1f,
            1f, -1f, 1f, 1f, 0f, 0f, 1f,
            1f, 1f, 1f, 1f, 0f, 0f, 1f,
            // Right (green)
            1f, 1f, 1f, 0f, 1f, 0f, 1f,
            1f, -1f, 1f, 0f, 1f, 0f, 1f,
            1f, -1f, -1f, 0f, 1f, 0f, 1f,
            1f, 1f, -1f, 0f, 1f, 0f, 1f,
            // Back (blue)
            1f, 1f, -1f, 0f, 0f, 1f, 1f,
            1f, -1f, -1f, 0f, 0f, 1f, 1f,
            -1f, -1f, -1f, 0f, 0f, 1f, 1f,
            -1f, 1f, -1f, 0f, 0f, 1f, 1f,
            // Left (yellow)
            -1f, 1f, -1f, 1f, 1f, 0f, 1f,
            -1f, -1f, -1f, 1f, 1f, 0f, 1f,
            -1f, -1f, 1f, 1f, 1f, 0f, 1f,
            -1f, 1f, 1f, 1f, 1f, 0f, 1f,
            // Top (cyan)
            -1f, 1f, -1f, 0f, 1f, 1f, 1f,
            -1f, 1f, 1f, 0f, 1f, 1f, 1f,
            1f, 1f, 1f, 0f, 1f, 1f, 1f,
            1f, 1f, -1f, 0f, 1f, 1f, 1f,
            // Bottom (magenta)
            -1f, -1f, 1f, 1f, 0f, 1f, 1f,
            -1f, -1f, -1f, 1f, 0f, 1f, 1f,
            1f, -1f, -1f, 1f, 0f, 1f, 1f,
            1f, -1f, 1f, 1f, 0f, 1f, 1f
        )

        // 6 faces * 2 triangles per face = 12 triangles = 36 indices.
        val CUBE_INDICES = shortArrayOf(
            0, 1, 2, 0, 2, 3,       // Front
            4, 5, 6, 4, 6, 7,       // Right
            8, 9, 10, 8, 10, 11,    // Back
            12, 13, 14, 12, 14, 15, // Left
            16, 17, 18, 16, 18, 19, // Top
            20, 21, 22, 20, 22, 23  // Bottom
        )

        const val VERTEX_SHADER_CODE = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;

            void main() {
                vColor = aColor;
                gl_Position = uMVPMatrix * aPosition;
            }
        """

        const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            varying vec4 vColor;

            void main() {
                gl_FragColor = vColor;
            }
        """
    }
}