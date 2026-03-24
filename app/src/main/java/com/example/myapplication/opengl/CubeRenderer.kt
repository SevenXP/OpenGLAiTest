package com.example.myapplication.opengl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders a solid-colored cube (6 faces, 12 triangles) using OpenGL ES 2.0.
 *
 * Matrix pipeline (column-major, right-handed):
 * - [mProjectionMatrix]: maps frustum clip space (perspective from field of view and aspect).
 * - [mViewMatrix]: moves the world so the camera sits at the eye point (setLookAtM).
 * - [mMatrix]: per-frame model matrix (identity + rotations on Y and X).
 * Combined shader matrix: MVP = P * V * M, applied as gl_Position = MVP * position.
 */
class CubeRenderer(@Suppress("UNUSED_PARAMETER") context: Context) : GLSurfaceView.Renderer {

    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)
    /** P * V * M — passed to the vertex shader. */
    private val mMVPMatrix = FloatArray(16)
    /** Holds V * M before multiplying by P. */
    private val mTempMatrix = FloatArray(16)

    private var program = 0
    private var aPositionHandle = 0
    private var aColorHandle = 0
    private var uMVPMatrixHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private var startTimeMs: Long = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        val glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "unknown"
        Log.d(TAG, "OpenGL ES version: $glVersion")

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        checkGlError("createProgram")

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aColorHandle = GLES20.glGetAttribLocation(program, "aColor")
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        checkGlError("get locations")

        // Camera: eye in front of the cube along -Z, looking at origin, Y up.
        Matrix.setLookAtM(
            mViewMatrix, 0,
            0f, 0f, 5f,
            0f, 0f, 0f,
            0f, 1f, 0f
        )

        buildBuffers()
        startTimeMs = SystemClock.uptimeMillis()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = if (height > 0) width.toFloat() / height.toFloat() else 1f
        // Near/far chosen so the unit cube (~0.5 from center) is inside the frustum.
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspect, 0.1f, 100f)
        checkGlError("perspectiveM")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(program)
        checkGlError("glUseProgram")

        // Model: identity, then rotate around Y for a simple animation.
        val elapsedSec = (SystemClock.uptimeMillis() - startTimeMs) / 1000f
        val angleDeg = elapsedSec * 45f

        Matrix.setIdentityM(mMatrix, 0)
        Matrix.rotateM(mMatrix, 0, angleDeg, 0f, 1f, 0f)
        Matrix.rotateM(mMatrix, 0, elapsedSec * 20f, 1f, 0f, 0f)

        // mTempMatrix = mViewMatrix * mMatrix (model in eye space).
        Matrix.multiplyMM(mTempMatrix, 0, mViewMatrix, 0, mMatrix, 0)
        // mMVPMatrix = mProjectionMatrix * (mViewMatrix * mMatrix).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mTempMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        checkGlError("glUniformMatrix4fv")

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            vertexBuffer
        )
        vertexBuffer.position(COORDS_PER_VERTEX)
        GLES20.glVertexAttribPointer(
            aColorHandle,
            COLOR_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            VERTEX_STRIDE_BYTES,
            vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glEnableVertexAttribArray(aColorHandle)

        indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            INDEX_COUNT,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer
        )
        checkGlError("glDrawElements")

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aColorHandle)
    }

    /**
     * Interleaved [x,y,z, r,g,b,a] for 24 vertices (4 per face, solid color per face).
     * Indices draw 12 triangles (two per face).
     */
    private fun buildBuffers() {
        val half = 0.5f
        val vertexData = floatArrayOf(
            // +Z (front) — red
            -half, -half, half, 1f, 0f, 0f, 1f,
            half, -half, half, 1f, 0f, 0f, 1f,
            half, half, half, 1f, 0f, 0f, 1f,
            -half, half, half, 1f, 0f, 0f, 1f,
            // -Z (back) — green
            half, -half, -half, 0f, 1f, 0f, 1f,
            -half, -half, -half, 0f, 1f, 0f, 1f,
            -half, half, -half, 0f, 1f, 0f, 1f,
            half, half, -half, 0f, 1f, 0f, 1f,
            // +X (right) — blue
            half, -half, half, 0f, 0f, 1f, 1f,
            half, -half, -half, 0f, 0f, 1f, 1f,
            half, half, -half, 0f, 0f, 1f, 1f,
            half, half, half, 0f, 0f, 1f, 1f,
            // -X (left) — yellow
            -half, -half, -half, 1f, 1f, 0f, 1f,
            -half, -half, half, 1f, 1f, 0f, 1f,
            -half, half, half, 1f, 1f, 0f, 1f,
            -half, half, -half, 1f, 1f, 0f, 1f,
            // +Y (top) — cyan
            -half, half, half, 0f, 1f, 1f, 1f,
            half, half, half, 0f, 1f, 1f, 1f,
            half, half, -half, 0f, 1f, 1f, 1f,
            -half, half, -half, 0f, 1f, 1f, 1f,
            // -Y (bottom) — magenta
            -half, -half, -half, 1f, 0f, 1f, 1f,
            half, -half, -half, 1f, 0f, 1f, 1f,
            half, -half, half, 1f, 0f, 1f, 1f,
            -half, -half, half, 1f, 0f, 1f, 1f
        )

        val bb = ByteBuffer.allocateDirect(vertexData.size * BYTES_PER_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertexData)
        vertexBuffer.position(0)

        val indexData = shortArrayOf(
            0, 1, 2, 0, 2, 3,
            4, 5, 6, 4, 6, 7,
            8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15,
            16, 17, 18, 16, 18, 19,
            20, 21, 22, 20, 22, 23
        )
        val ib = ByteBuffer.allocateDirect(indexData.size * BYTES_PER_SHORT)
        ib.order(ByteOrder.nativeOrder())
        indexBuffer = ib.asShortBuffer()
        indexBuffer.put(indexData)
        indexBuffer.position(0)
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        checkGlError("glCreateShader")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            Log.e(TAG, "Shader compile failed: $log")
            throw IllegalStateException("Could not compile shader: $log")
        }
        return shader
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        checkGlError("glCreateProgram")
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            Log.e(TAG, "Program link failed: $log")
            throw IllegalStateException("Could not link program: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun checkGlError(op: String) {
        var error = GLES20.glGetError()
        while (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
            error = GLES20.glGetError()
        }
    }

    companion object {
        private const val TAG = "CubeRenderer"

        private const val COORDS_PER_VERTEX = 3
        private const val COLOR_COMPONENTS = 4
        private const val FLOATS_PER_VERTEX = COORDS_PER_VERTEX + COLOR_COMPONENTS
        private const val VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * 4
        private const val INDEX_COUNT = 36
        private const val BYTES_PER_FLOAT = 4
        private const val BYTES_PER_SHORT = 2

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """
    }
}
