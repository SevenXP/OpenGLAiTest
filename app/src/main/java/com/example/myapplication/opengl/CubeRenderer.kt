package com.example.myapplication.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CubeRenderer : GLSurfaceView.Renderer {

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec4 aColor;
        varying vec4 vColor;

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

    private val cubeCoords = floatArrayOf(
        -1f, 1f, 1f,  -1f, -1f, 1f,   1f, -1f, 1f,   1f, 1f, 1f,
        -1f, 1f, -1f, -1f, -1f, -1f, -1f, -1f, 1f,  -1f, 1f, 1f,
        1f, 1f, 1f,    1f, -1f, 1f,   1f, -1f, -1f,  1f, 1f, -1f,
        -1f, 1f, -1f, -1f, 1f, 1f,    1f, 1f, 1f,    1f, 1f, -1f,
        -1f, -1f, 1f, -1f, -1f, -1f,  1f, -1f, -1f,  1f, -1f, 1f,
        1f, 1f, -1f,   1f, -1f, -1f, -1f, -1f, -1f, -1f, 1f, -1f
    )

    private val cubeColors = floatArrayOf(
        1f, 0f, 0f, 1f,  1f, 0f, 0f, 1f,  1f, 0f, 0f, 1f,  1f, 0f, 0f, 1f,
        0f, 1f, 0f, 1f,  0f, 1f, 0f, 1f,  0f, 1f, 0f, 1f,  0f, 1f, 0f, 1f,
        0f, 0f, 1f, 1f,  0f, 0f, 1f, 1f,  0f, 0f, 1f, 1f,  0f, 0f, 1f, 1f,
        1f, 1f, 0f, 1f,  1f, 1f, 0f, 1f,  1f, 1f, 0f, 1f,  1f, 1f, 0f, 1f,
        1f, 0f, 1f, 1f,  1f, 0f, 1f, 1f,  1f, 0f, 1f, 1f,  1f, 0f, 1f, 1f,
        0f, 1f, 1f, 1f,  0f, 1f, 1f, 1f,  0f, 1f, 1f, 1f,  0f, 1f, 1f, 1f
    )

    private val drawOrder = shortArrayOf(
        0, 1, 2, 0, 2, 3,
        4, 5, 6, 4, 6, 7,
        8, 9, 10, 8, 10, 11,
        12, 13, 14, 12, 14, 15,
        16, 17, 18, 16, 18, 19,
        20, 21, 22, 20, 22, 23
    )

    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(cubeCoords.size * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(cubeCoords)
            position(0)
        }

    private val colorBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(cubeColors.size * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(cubeColors)
            position(0)
        }

    private val drawListBuffer: ShortBuffer = ByteBuffer
        .allocateDirect(drawOrder.size * BYTES_PER_SHORT)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
        .apply {
            put(drawOrder)
            position(0)
        }

    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var matrixHandle = 0
    private var angle = 0f
    private var lastFrameTimeMs = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also { createdProgram ->
            if (createdProgram == 0) {
                throw RuntimeException("Unable to create OpenGL program.")
            }
            GLES20.glAttachShader(createdProgram, vertexShader)
            checkGlError("glAttachShader(vertex)")
            GLES20.glAttachShader(createdProgram, fragmentShader)
            checkGlError("glAttachShader(fragment)")
            GLES20.glLinkProgram(createdProgram)
        }

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $log")
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        matrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        if (positionHandle < 0 || colorHandle < 0 || matrixHandle < 0) {
            throw RuntimeException("Required shader handles were not found.")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val aspectRatio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspectRatio, 1f, 10f)

        Matrix.setLookAtM(
            mViewMatrix,
            0,
            0f, 0f, 5f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        val deltaTimeSeconds = limitFrameRate()

        angle = (angle + ROTATION_SPEED_DEGREES_PER_SECOND * deltaTimeSeconds) % 360f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Model matrix: start from identity and rotate the cube each frame.
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, angle, 1f, 1f, 0.5f)

        // Combine view and model first, then apply projection.
        Matrix.multiplyMM(modelViewMatrix, 0, mViewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, modelViewMatrix, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            COORDS_PER_VERTEX * BYTES_PER_FLOAT,
            vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        colorBuffer.position(0)
        GLES20.glVertexAttribPointer(
            colorHandle,
            COLORS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            COLORS_PER_VERTEX * BYTES_PER_FLOAT,
            colorBuffer
        )
        GLES20.glEnableVertexAttribArray(colorHandle)

        GLES20.glUniformMatrix4fv(matrixHandle, 1, false, mMatrix, 0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            drawOrder.size,
            GLES20.GL_UNSIGNED_SHORT,
            drawListBuffer
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        checkGlError("glDrawElements")

    }

    private fun limitFrameRate(): Float {
        val now = SystemClock.elapsedRealtime()
        if (lastFrameTimeMs != 0L) {
            val elapsedMs = now - lastFrameTimeMs
            val sleepMs = FRAME_TIME_MS - elapsedMs
            if (sleepMs > 0L) {
                SystemClock.sleep(sleepMs)
            }
        }
        val frameTimeMs = SystemClock.elapsedRealtime()
        val deltaTimeSeconds = if (lastFrameTimeMs == 0L) {
            0f
        } else {
            (frameTimeMs - lastFrameTimeMs) / 1000f
        }
        lastFrameTimeMs = frameTimeMs
        return deltaTimeSeconds
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("Unable to create shader of type $type.")
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $log")
        }

        return shader
    }

    private fun checkGlError(operation: String) {
        var error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val errors = StringBuilder()
            while (error != GLES20.GL_NO_ERROR) {
                if (errors.isNotEmpty()) {
                    errors.append(", ")
                }
                errors.append(error)
                error = GLES20.glGetError()
            }
            throw RuntimeException("$operation failed with GL error(s): $errors")
        }
    }

    companion object {
        private const val COORDS_PER_VERTEX = 3
        private const val COLORS_PER_VERTEX = 4
        private const val BYTES_PER_FLOAT = 4
        private const val BYTES_PER_SHORT = 2
        private const val FRAME_TIME_MS = 1000L / 60L
        private const val ROTATION_SPEED_DEGREES_PER_SECOND = 60f
    }
}