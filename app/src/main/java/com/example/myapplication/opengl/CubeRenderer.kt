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

/**
 * OpenGL ES 2.0 renderer for a colored indexed cube (6 faces, 12 triangles).
 *
 * Matrix pipeline (column vectors, so combined transform is **P × V × M**):
 * - [mProjectionMatrix]: maps frustum to clip space (set in [onSurfaceChanged] via [Matrix.perspectiveM]).
 * - [mViewMatrix]: moves world so the camera sits at the origin looking down −Z ([Matrix.setLookAtM]).
 * - [mMatrix]: **model** matrix — identity plus per-frame [Matrix.rotateM] for animation.
 * - [mTempMatrix] / [mMVPMatrix]: scratch for [Matrix.multiplyMM] (operands must not alias the result).
 */
class CubeRenderer : GLSurfaceView.Renderer {

    /** Column-major 4×4 projection matrix (clip space frustum). */
    private val mProjectionMatrix = FloatArray(16)

    /** Column-major 4×4 view matrix (camera / world transform). */
    private val mViewMatrix = FloatArray(16)

    /** Column-major 4×4 model matrix (object pose; rotated each frame). */
    private val mMatrix = FloatArray(16)

    /** Holds **view × model** between multiply steps. */
    private val mTempMatrix = FloatArray(16)

    /** Final **projection × view × model** passed to the vertex shader. */
    private val mMVPMatrix = FloatArray(16)

    private var program = 0
    private var aPositionHandle = 0
    private var aColorHandle = 0
    private var uMatrixHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private var vertexBufferId = 0
    private var indexBufferId = 0

    companion object {
        private const val TAG = "CubeRenderer"

        /** Interleaved: xyz + rgba per vertex. */
        private const val FLOATS_PER_VERTEX = 7
        private const val BYTES_PER_FLOAT = 4
        private const val VERTEX_STRIDE = FLOATS_PER_VERTEX * BYTES_PER_FLOAT

        private const val VERTEX_SHADER = """
            uniform mat4 u_Matrix;
            attribute vec4 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = u_Matrix * a_Position;
                v_Color = a_Color;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 v_Color;
            void main() {
                gl_FragColor = v_Color;
            }
        """

        /** 12 triangles × 3 indices. */
        private const val INDEX_COUNT = 36
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        program = buildProgram()
        if (program == 0) {
            Log.e(TAG, "Program creation failed; draw calls will be skipped.")
            return
        }

        GLES20.glUseProgram(program)
        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        aColorHandle = GLES20.glGetAttribLocation(program, "a_Color")
        uMatrixHandle = GLES20.glGetUniformLocation(program, "u_Matrix")
        checkGlError("onSurfaceCreated attrib/uniform lookup")

        // View: eye at +Z looking at origin, Y up (right-handed world).
        Matrix.setLookAtM(
            mViewMatrix, 0,
            0f, 0f, 4f,
            0f, 0f, 0f,
            0f, 1f, 0f,
        )

        createInterleavedVertexBuffer()
        createIndexBuffer()
        uploadBuffersToGpu()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        if (width == 0 || height == 0) return

        val aspect = width.toFloat() / height.toFloat()
        // Near/far chosen so the unit cube at origin stays inside the frustum with camera at z=4.
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspect, 0.1f, 100f)
        checkGlError("onSurfaceChanged perspective")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (program == 0 || vertexBufferId == 0 || indexBufferId == 0) return

        val t = SystemClock.uptimeMillis() / 1000f
        val angleDeg = t * 45f

        // Model: start from identity, then accumulate rotations (Y then X) on the model matrix.
        Matrix.setIdentityM(mMatrix, 0)
        Matrix.rotateM(mMatrix, 0, angleDeg, 0f, 1f, 0f)
        Matrix.rotateM(mMatrix, 0, angleDeg * 0.5f, 1f, 0f, 0f)

        // Combined MVP = P * (V * M). First multiplyMM computes VM; second applies P.
        Matrix.multiplyMM(mTempMatrix, 0, mViewMatrix, 0, mMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mTempMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMatrixHandle, 1, false, mMVPMatrix, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(
            aPositionHandle, 3, GLES20.GL_FLOAT, false,
            VERTEX_STRIDE, 0,
        )
        GLES20.glEnableVertexAttribArray(aColorHandle)
        GLES20.glVertexAttribPointer(
            aColorHandle, 4, GLES20.GL_FLOAT, false,
            VERTEX_STRIDE, 3 * BYTES_PER_FLOAT,
        )

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, INDEX_COUNT, GLES20.GL_UNSIGNED_SHORT, 0)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aColorHandle)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        checkGlError("onDrawFrame")
    }

    private fun buildProgram(): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        if (vs == 0) return 0
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (fs == 0) {
            GLES20.glDeleteShader(vs)
            return 0
        }

        val p = GLES20.glCreateProgram()
        if (p == 0) {
            Log.e(TAG, "glCreateProgram failed")
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return 0
        }
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link failed: ${GLES20.glGetProgramInfoLog(p)}")
            GLES20.glDeleteProgram(p)
            GLES20.glDeleteShader(vs)
            GLES20.glDeleteShader(fs)
            return 0
        }

        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        checkGlError("buildProgram")
        return p
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "glCreateShader failed for type $type")
            return 0
        }
        GLES20.glShaderSource(shader, source.trimIndent())
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        checkGlError("compileShader")
        return shader
    }

    private fun createInterleavedVertexBuffer() {
        val h = 0.5f
        // 24 vertices: 4 per face × 6 faces; each face has its own RGBA (CCW winding, outward normals).
        val data = floatArrayOf(
            // +Z (red)
            -h, -h, h, 1f, 0f, 0f, 1f,
            h, -h, h, 1f, 0f, 0f, 1f,
            h, h, h, 1f, 0f, 0f, 1f,
            -h, h, h, 1f, 0f, 0f, 1f,
            // −Z (green)
            h, -h, -h, 0f, 1f, 0f, 1f,
            -h, -h, -h, 0f, 1f, 0f, 1f,
            -h, h, -h, 0f, 1f, 0f, 1f,
            h, h, -h, 0f, 1f, 0f, 1f,
            // +X (blue)
            h, -h, h, 0f, 0f, 1f, 1f,
            h, -h, -h, 0f, 0f, 1f, 1f,
            h, h, -h, 0f, 0f, 1f, 1f,
            h, h, h, 0f, 0f, 1f, 1f,
            // −X (yellow)
            -h, -h, -h, 1f, 1f, 0f, 1f,
            -h, -h, h, 1f, 1f, 0f, 1f,
            -h, h, h, 1f, 1f, 0f, 1f,
            -h, h, -h, 1f, 1f, 0f, 1f,
            // +Y (cyan)
            -h, h, h, 0f, 1f, 1f, 1f,
            h, h, h, 0f, 1f, 1f, 1f,
            h, h, -h, 0f, 1f, 1f, 1f,
            -h, h, -h, 0f, 1f, 1f, 1f,
            // −Y (magenta)
            -h, -h, -h, 1f, 0f, 1f, 1f,
            h, -h, -h, 1f, 0f, 1f, 1f,
            h, -h, h, 1f, 0f, 1f, 1f,
            -h, -h, h, 1f, 0f, 1f, 1f,
        )

        val bb = ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT).order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(data)
        vertexBuffer.position(0)
    }

    private fun createIndexBuffer() {
        val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3,
            4, 5, 6, 4, 6, 7,
            8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15,
            16, 17, 18, 16, 18, 19,
            20, 21, 22, 20, 22, 23,
        )
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        indexBuffer = ib.asShortBuffer()
        indexBuffer.put(indices)
        indexBuffer.position(0)
    }

    private fun uploadBuffersToGpu() {
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vertexBufferId = ids[0]
        indexBufferId = ids[1]

        val vBytes = vertexBuffer.capacity() * BYTES_PER_FLOAT
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        vertexBuffer.position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vBytes, vertexBuffer, GLES20.GL_STATIC_DRAW)

        val iBytes = indexBuffer.capacity() * 2
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        indexBuffer.position(0)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, iBytes, indexBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        checkGlError("uploadBuffersToGpu")
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(error)}")
        }
    }
}
