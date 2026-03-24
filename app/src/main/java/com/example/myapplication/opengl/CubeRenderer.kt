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
 * Renders a colored 3D cube using OpenGL ES 2.0.
 *
 * All matrix math uses [android.opengl.Matrix] exclusively — no third-party libraries.
 * The cube has 6 differently-colored faces (12 triangles) and rotates continuously.
 */
class CubeRenderer : GLSurfaceView.Renderer {

    // ── Inline shaders ──────────────────────────────────────────────────────────

    private val vertexShaderCode = """
        uniform mat4 u_MVPMatrix;
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        varying vec4 v_Color;
        void main() {
            gl_Position = u_MVPMatrix * a_Position;
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

    // ── Matrix arrays ───────────────────────────────────────────────────────────

    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mTempMatrix = FloatArray(16)

    // ── Buffers & handles ───────────────────────────────────────────────────────

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private var programHandle = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0

    private var angle = 0f
    private var lastFrameTimeNs = 0L

    // ── Vertex data: position (x,y,z) + color (r,g,b,a) per vertex ──────────
    // 24 vertices total — 4 per face so each face gets its own solid color.

    private val vertexData = floatArrayOf(
        // Front face — red
        -0.5f, -0.5f,  0.5f,   1f, 0f, 0f, 1f,
         0.5f, -0.5f,  0.5f,   1f, 0f, 0f, 1f,
         0.5f,  0.5f,  0.5f,   1f, 0f, 0f, 1f,
        -0.5f,  0.5f,  0.5f,   1f, 0f, 0f, 1f,

        // Back face — green
        -0.5f, -0.5f, -0.5f,   0f, 1f, 0f, 1f,
        -0.5f,  0.5f, -0.5f,   0f, 1f, 0f, 1f,
         0.5f,  0.5f, -0.5f,   0f, 1f, 0f, 1f,
         0.5f, -0.5f, -0.5f,   0f, 1f, 0f, 1f,

        // Top face — blue
        -0.5f,  0.5f, -0.5f,   0f, 0f, 1f, 1f,
        -0.5f,  0.5f,  0.5f,   0f, 0f, 1f, 1f,
         0.5f,  0.5f,  0.5f,   0f, 0f, 1f, 1f,
         0.5f,  0.5f, -0.5f,   0f, 0f, 1f, 1f,

        // Bottom face — yellow
        -0.5f, -0.5f, -0.5f,   1f, 1f, 0f, 1f,
         0.5f, -0.5f, -0.5f,   1f, 1f, 0f, 1f,
         0.5f, -0.5f,  0.5f,   1f, 1f, 0f, 1f,
        -0.5f, -0.5f,  0.5f,   1f, 1f, 0f, 1f,

        // Right face — cyan
         0.5f, -0.5f, -0.5f,   0f, 1f, 1f, 1f,
         0.5f,  0.5f, -0.5f,   0f, 1f, 1f, 1f,
         0.5f,  0.5f,  0.5f,   0f, 1f, 1f, 1f,
         0.5f, -0.5f,  0.5f,   0f, 1f, 1f, 1f,

        // Left face — magenta
        -0.5f, -0.5f, -0.5f,   1f, 0f, 1f, 1f,
        -0.5f, -0.5f,  0.5f,   1f, 0f, 1f, 1f,
        -0.5f,  0.5f,  0.5f,   1f, 0f, 1f, 1f,
        -0.5f,  0.5f, -0.5f,   1f, 0f, 1f, 1f,
    )

    // 36 indices → 12 triangles → 6 faces
    private val indexData = shortArrayOf(
         0,  1,  2,   0,  2,  3,  // front
         4,  5,  6,   4,  6,  7,  // back
         8,  9, 10,   8, 10, 11,  // top
        12, 13, 14,  12, 14, 15,  // bottom
        16, 17, 18,  16, 18, 19,  // right
        20, 21, 22,  20, 22, 23,  // left
    )

    // ── GLSurfaceView.Renderer callbacks ────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        initBuffers()
        initShaderProgram()
        bindVertexAttributes()
        setupViewMatrix()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        setupProjectionMatrix(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val frameStartNs = System.nanoTime()

        // Time-based rotation: constant 42°/s regardless of actual frame rate
        val deltaSec = if (lastFrameTimeNs == 0L) {
            0f
        } else {
            (frameStartNs - lastFrameTimeNs) / 1_000_000_000f
        }
        lastFrameTimeNs = frameStartNs

        angle += ROTATION_DEGREES_PER_SEC * deltaSec

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.rotateM(mModelMatrix, 0, angle, 1f, 1f, 0.5f)

        // MVP = Projection × View × Model
        Matrix.multiplyMM(mTempMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mTempMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            indexData.size,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )

        // Throttle to ~60 FPS: sleep for remaining time if frame finished early
        val elapsedMs = (System.nanoTime() - frameStartNs) / 1_000_000L
        val sleepMs = FRAME_INTERVAL_MS - elapsedMs
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Allocates direct native-order buffers and copies vertex / index data into them.
     * Direct buffers are required by OpenGL ES for efficient GPU access.
     */
    private fun initBuffers() {
        vertexBuffer = ByteBuffer
            .allocateDirect(vertexData.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }

        indexBuffer = ByteBuffer
            .allocateDirect(indexData.size * BYTES_PER_SHORT)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indexData)
                position(0)
            }
    }

    /** Compiles both shaders, links them into a program, and activates it. */
    private fun initShaderProgram() {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programHandle = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(prog)
                GLES20.glDeleteProgram(prog)
                throw RuntimeException("Program link failed: $log")
            }
        }

        GLES20.glUseProgram(programHandle)
    }

    private fun compileShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compile failed: $log")
            }
        }
    }

    /**
     * Binds the interleaved vertex buffer to the position and color attributes.
     * Layout per vertex: [x, y, z, r, g, b, a] — stride = 7 × 4 = 28 bytes.
     */
    private fun bindVertexAttributes() {
        positionHandle = GLES20.glGetAttribLocation(programHandle, "a_Position")
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
            false, STRIDE, vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        colorHandle = GLES20.glGetAttribLocation(programHandle, "a_Color")
        vertexBuffer.position(COORDS_PER_VERTEX) // skip past xyz to reach rgba
        GLES20.glVertexAttribPointer(
            colorHandle, COLORS_PER_VERTEX, GLES20.GL_FLOAT,
            false, STRIDE, vertexBuffer
        )
        GLES20.glEnableVertexAttribArray(colorHandle)

        mvpMatrixHandle = GLES20.glGetUniformLocation(programHandle, "u_MVPMatrix")
    }

    /**
     * Perspective projection: 45° vertical FOV, near plane at 1, far plane at 10.
     * Uses [Matrix.perspectiveM] which builds the projection from FOV + aspect ratio
     * instead of explicit frustum bounds.
     */
    private fun setupProjectionMatrix(width: Int, height: Int) {
        val aspect = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspect, 1f, 10f)
    }

    /**
     * Camera positioned slightly above and in front of the origin so the cube
     * is clearly visible with a slight top-down perspective.
     * Eye = (0, 2, 4), Center = (0, 0, 0), Up = (0, 1, 0).
     */
    private fun setupViewMatrix() {
        Matrix.setLookAtM(
            mViewMatrix, 0,
            0f, 2f, 4f,  // eye position
            0f, 0f, 0f,  // look-at target
            0f, 1f, 0f   // up vector
        )
    }

    companion object {
        private const val COORDS_PER_VERTEX = 3
        private const val COLORS_PER_VERTEX = 4
        private const val BYTES_PER_FLOAT = 4
        private const val BYTES_PER_SHORT = 2
        private const val STRIDE = (COORDS_PER_VERTEX + COLORS_PER_VERTEX) * BYTES_PER_FLOAT

        private const val TARGET_FPS = 60
        private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS  // ~16 ms
        private const val ROTATION_DEGREES_PER_SEC = 42f
    }
}
