package com.example.myapplication.opengl

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
import kotlin.math.sqrt

/**
 * OpenGL ES 2.0 renderer: colored cube with MVP and indexed drawing.
 */
class CubeRenderer : GLSurfaceView.Renderer {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var program = 0
    private var aPositionHandle = 0
    private var aColorHandle = 0
    private var uMvpHandle = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: ShortBuffer

    private var angleDeg = 0f
    private var lastFrameTimeNs = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.08f, 0.08f, 0.12f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE)
        program = linkProgram(vs, fs)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        checkGlError("onSurfaceCreated program")

        GLES20.glUseProgram(program)
        aPositionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        aColorHandle = GLES20.glGetAttribLocation(program, "a_Color")
        uMvpHandle = GLES20.glGetUniformLocation(program, "u_MVP")
        require(aPositionHandle >= 0) { "a_Position location invalid" }
        require(aColorHandle >= 0) { "a_Color location invalid" }
        require(uMvpHandle >= 0) { "u_MVP location invalid" }

        buildBuffers()
        checkGlError("onSurfaceCreated end")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, ratio, 0.1f, 100f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
        checkGlError("onSurfaceChanged")
    }

    override fun onDrawFrame(gl: GL10?) {
        limitTo60Fps()

        angleDeg += 0.8f
        if (angleDeg >= 360f) angleDeg -= 360f

        Matrix.setIdentityM(modelMatrix, 0)
        val ax = 1f
        val ay = 1f
        val az = 0.5f
        val len = sqrt(ax * ax + ay * ay + az * az)
        Matrix.rotateM(modelMatrix, 0, angleDeg, ax / len, ay / len, az / len)

        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvpHandle, 1, false, mvpMatrix, 0)

        val stride = (POSITION_COMPONENTS + COLOR_COMPONENTS) * BYTES_PER_FLOAT
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            POSITION_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            stride,
            vertexBuffer
        )
        vertexBuffer.position(POSITION_COMPONENTS)
        GLES20.glVertexAttribPointer(
            aColorHandle,
            COLOR_COMPONENTS,
            GLES20.GL_FLOAT,
            false,
            stride,
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

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aColorHandle)
        checkGlError("onDrawFrame")
    }

    private fun limitTo60Fps() {
        val now = System.nanoTime()
        if (lastFrameTimeNs != 0L) {
            val elapsed = now - lastFrameTimeNs
            if (elapsed < TARGET_FRAME_NS) {
                val sleepNs = TARGET_FRAME_NS - elapsed
                val ms = sleepNs / 1_000_000
                val ns = (sleepNs % 1_000_000).toInt()
                try {
                    Thread.sleep(ms, ns)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
        lastFrameTimeNs = System.nanoTime()
    }

    private fun buildBuffers() {
        val s = 0.5f
        val interleaved = floatArrayOf(
            // +Z (front) red
            -s, -s, s, 1f, 0f, 0f, 1f,
            s, -s, s, 1f, 0f, 0f, 1f,
            s, s, s, 1f, 0f, 0f, 1f,
            -s, s, s, 1f, 0f, 0f, 1f,
            // -Z (back) green
            s, -s, -s, 0f, 1f, 0f, 1f,
            -s, -s, -s, 0f, 1f, 0f, 1f,
            -s, s, -s, 0f, 1f, 0f, 1f,
            s, s, -s, 0f, 1f, 0f, 1f,
            // +X blue
            s, -s, -s, 0f, 0f, 1f, 1f,
            s, -s, s, 0f, 0f, 1f, 1f,
            s, s, s, 0f, 0f, 1f, 1f,
            s, s, -s, 0f, 0f, 1f, 1f,
            // -X yellow
            -s, -s, s, 1f, 1f, 0f, 1f,
            -s, -s, -s, 1f, 1f, 0f, 1f,
            -s, s, -s, 1f, 1f, 0f, 1f,
            -s, s, s, 1f, 1f, 0f, 1f,
            // +Y cyan
            -s, s, s, 0f, 1f, 1f, 1f,
            s, s, s, 0f, 1f, 1f, 1f,
            s, s, -s, 0f, 1f, 1f, 1f,
            -s, s, -s, 0f, 1f, 1f, 1f,
            // -Y magenta
            -s, -s, -s, 1f, 0f, 1f, 1f,
            s, -s, -s, 1f, 0f, 1f, 1f,
            s, -s, s, 1f, 0f, 1f, 1f,
            -s, -s, s, 1f, 0f, 1f, 1f
        )
        val vbb = ByteBuffer.allocateDirect(interleaved.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
        vertexBuffer = vbb.asFloatBuffer()
        vertexBuffer.put(interleaved)
        vertexBuffer.position(0)

        val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3,
            4, 5, 6, 4, 6, 7,
            8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15,
            16, 17, 18, 16, 18, 19,
            20, 21, 22, 20, 22, 23
        )
        val ibb = ByteBuffer.allocateDirect(indices.size * BYTES_PER_SHORT)
            .order(ByteOrder.nativeOrder())
        indexBuffer = ibb.asShortBuffer()
        indexBuffer.put(indices)
        indexBuffer.position(0)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "compileShader failed type=$type: $log")
            GLES20.glDeleteShader(shader)
            error("Shader compile failed")
        }
        checkGlError("compileShader")
        return shader
    }

    private fun linkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vertexShader)
        GLES20.glAttachShader(prog, fragmentShader)
        GLES20.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            Log.e(TAG, "linkProgram failed: $log")
            GLES20.glDeleteProgram(prog)
            error("Program link failed")
        }
        checkGlError("linkProgram")
        return prog
    }

    private fun checkGlError(op: String) {
        var err: Int
        while (GLES20.glGetError().also { err = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError 0x${Integer.toHexString(err)}")
        }
    }

    companion object {
        private const val TAG = "CubeRenderer"
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 4
        private const val BYTES_PER_FLOAT = 4
        private const val BYTES_PER_SHORT = 2
        private const val INDEX_COUNT = 36
        private const val TARGET_FRAME_NS = 16_666_667L

        private const val VERTEX_SHADER_SOURCE = """
            uniform mat4 u_MVP;
            attribute vec4 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = u_MVP * a_Position;
                v_Color = a_Color;
            }
        """

        private const val FRAGMENT_SHADER_SOURCE = """
            precision mediump float;
            varying vec4 v_Color;
            void main() {
                gl_FragColor = v_Color;
            }
        """
    }
}
