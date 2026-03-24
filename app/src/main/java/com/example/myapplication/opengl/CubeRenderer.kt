package com.example.myapplication.opengl

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
import kotlin.math.max

/**
 * OpenGL ES 2.0 renderer for a colored cube (6 faces, 12 triangles).
 *
 * Matrix pipeline (column vectors, right-handed):
 * - [mProjectionMatrix]: maps frustum to clip space (set in [onSurfaceChanged] via [Matrix.perspectiveM]).
 * - [mViewMatrix]: places the camera with [Matrix.setLookAtM] (eye, center, up).
 * - [mMatrix]: model matrix — identity plus per-frame [Matrix.rotateM] for animation.
 *
 * Clip-space position: clip = P × V × M × localPosition (implemented as two [Matrix.multiplyMM] steps).
 */
class CubeRenderer : GLSurfaceView.Renderer {

    /** Column-major 4×4 projection matrix (clip from view space). */
    private val mProjectionMatrix = FloatArray(MATRIX_SIZE)

    /** Column-major 4×4 view matrix (view from world space). */
    private val mViewMatrix = FloatArray(MATRIX_SIZE)

    /** Column-major 4×4 model matrix (object in world space). */
    private val mMatrix = FloatArray(MATRIX_SIZE)

    /** Reusable: P × V */
    private val mProjectionViewMatrix = FloatArray(MATRIX_SIZE)

    /** Combined MVP passed to the vertex shader. */
    private val mMVPMatrix = FloatArray(MATRIX_SIZE)

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        // Camera: eye above and behind, looking at origin, Y up.
        Matrix.setIdentityM(mViewMatrix, 0)
        Matrix.setLookAtM(
            mViewMatrix,
            0,
            0f, 1.2f, 4f, // eye
            0f, 0f, 0f, // center
            0f, 1f, 0f, // up
        )

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            Log.e(TAG, "Program link failed; draw calls will no-op")
            return
        }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        checkGlError("onSurfaceCreated attrib/uniform")

        allocateBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = max(width, 1).toFloat() / max(height, 1).toFloat()

        // Vertical field of view in degrees; near/far clip planes in view-space units.
        Matrix.setIdentityM(mProjectionMatrix, 0)
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, ratio, 0.1f, 100f)
        checkGlError("onSurfaceChanged perspective")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (program == 0) return

        GLES20.glUseProgram(program)

        // Model: start from identity each frame, then apply time-based rotation around Y and X.
        Matrix.setIdentityM(mMatrix, 0)
        val timeMs = SystemClock.uptimeMillis()
        val angleY = (timeMs % ROTATION_PERIOD_MS) * 360f / ROTATION_PERIOD_MS
        val angleX = angleY * 0.5f
        Matrix.rotateM(mMatrix, 0, angleY, 0f, 1f, 0f)
        Matrix.rotateM(mMatrix, 0, angleX, 1f, 0f, 0f)

        // MVP = P × V × M (column-major multiply: first P×V, then (P×V)×M).
        Matrix.multiplyMM(mProjectionViewMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionViewMatrix, 0, mMatrix, 0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )
        GLES20.glEnableVertexAttribArray(positionHandle)

        vertexBuffer.position(COORDS_PER_VERTEX)
        GLES20.glVertexAttribPointer(
            colorHandle,
            COLOR_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            STRIDE_BYTES,
            vertexBuffer,
        )
        GLES20.glEnableVertexAttribArray(colorHandle)

        indexBuffer.position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            INDEX_COUNT,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer,
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        checkGlError("onDrawFrame")
    }

    private fun allocateBuffers() {
        // 24 vertices: 4 per face × 6 faces; each vertex: xyz + rgb (interleaved).
        val bb = ByteBuffer.allocateDirect(CUBE_VERTEX_DATA.size * BYTES_PER_FLOAT)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(CUBE_VERTEX_DATA)
        vertexBuffer.position(0)

        val ib = ByteBuffer.allocateDirect(CUBE_INDICES.size * BYTES_PER_SHORT)
        ib.order(ByteOrder.nativeOrder())
        indexBuffer = ib.asShortBuffer()
        indexBuffer.put(CUBE_INDICES)
        indexBuffer.position(0)
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }
        val prog = GLES20.glCreateProgram()
        if (prog == 0) {
            Log.e(TAG, "glCreateProgram failed")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link log: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        checkGlError("buildProgram")
        return prog
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "glCreateShader failed for type $type")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile log: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        checkGlError("loadShader")
        return shader
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
        }
    }

    companion object {
        private const val TAG = "CubeRenderer"
        private const val MATRIX_SIZE = 16
        private const val COORDS_PER_VERTEX = 3
        private const val COLOR_COMPONENTS = 3
        private const val FLOATS_PER_VERTEX = COORDS_PER_VERTEX + COLOR_COMPONENTS
        private const val BYTES_PER_FLOAT = 4
        private const val STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        private const val BYTES_PER_SHORT = 2
        private const val INDEX_COUNT = 36
        private const val ROTATION_PERIOD_MS = 10_000L

        private val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vColor = aColor;
            }
        """.trimIndent()

        private val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 vColor;
            void main() {
                gl_FragColor = vColor;
            }
        """.trimIndent()

        /**
         * Half-extent of the cube (centered at origin); faces at ±h.
         */
        private const val H = 0.5f

        /**
         * Interleaved vertex data: x, y, z, r, g, b per vertex.
         * 24 vertices (4 per face) so each face can have a solid color.
         */
        private val CUBE_VERTEX_DATA = floatArrayOf(
            // +Z (front) — blue
            -H, -H, H, 0.2f, 0.4f, 1f,
            H, -H, H, 0.2f, 0.4f, 1f,
            H, H, H, 0.2f, 0.4f, 1f,
            -H, H, H, 0.2f, 0.4f, 1f,
            // -Z (back) — yellow
            H, -H, -H, 1f, 0.9f, 0.2f,
            -H, -H, -H, 1f, 0.9f, 0.2f,
            -H, H, -H, 1f, 0.9f, 0.2f,
            H, H, -H, 1f, 0.9f, 0.2f,
            // +X (right) — green
            H, -H, H, 0.2f, 1f, 0.35f,
            H, -H, -H, 0.2f, 1f, 0.35f,
            H, H, -H, 0.2f, 1f, 0.35f,
            H, H, H, 0.2f, 1f, 0.35f,
            // -X (left) — magenta
            -H, -H, -H, 1f, 0.2f, 0.8f,
            -H, -H, H, 1f, 0.2f, 0.8f,
            -H, H, H, 1f, 0.2f, 0.8f,
            -H, H, -H, 1f, 0.2f, 0.8f,
            // +Y (top) — red
            -H, H, H, 1f, 0.25f, 0.25f,
            H, H, H, 1f, 0.25f, 0.25f,
            H, H, -H, 1f, 0.25f, 0.25f,
            -H, H, -H, 1f, 0.25f, 0.25f,
            // -Y (bottom) — cyan
            -H, -H, -H, 0.2f, 0.85f, 1f,
            H, -H, -H, 0.2f, 0.85f, 1f,
            H, -H, H, 0.2f, 0.85f, 1f,
            -H, -H, H, 0.2f, 0.85f, 1f,
        )

        /** 12 triangles (two per face), CCW from outside; indices into the 24 vertices above. */
        private val CUBE_INDICES = shortArrayOf(
            // +Z (view from +Z)
            0, 3, 2, 0, 2, 1,
            // -Z (view from -Z)
            5, 6, 7, 5, 7, 4,
            // +X
            9, 10, 11, 9, 11, 8,
            // -X
            12, 15, 14, 12, 14, 13,
            // +Y
            16, 19, 18, 16, 18, 17,
            // -Y
            20, 23, 22, 20, 22, 21,
        )
    }
}
