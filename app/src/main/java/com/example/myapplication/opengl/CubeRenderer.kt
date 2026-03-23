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
    companion object {
        private const val TAG = "CubeRenderer"
        private  val VERTEX_SHADER_CODE =
            "attribute vec4 aPosition;\n" +
                    "            attribute vec4 aColor;\n" +
                    "            uniform mat4 uMVPMatrix;\n" +
                    "            varying vec4 vColor;\n" +
                    "            void main() {\n" +
                    "                gl_Position = uMVPMatrix * aPosition;\n" +
                    "                vColor = aColor;\n" +
                    "            }".trimIndent()

        private  val FRAGMENT_SHADER_CODE =
            "precision mediump float;\n" +
                    "            varying vec4 vColor;\n" +
                    "            void main() {\n" +
                    "                gl_FragColor = vColor;\n" +
                    "            }"
            .trimIndent()
    }

    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)

    private var rotationX = 0f
    private var rotationY = 0f

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated called")

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        setupBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: width=$width, height=$height")

        GLES20.glViewport(0, 0, width, height)

        val aspectRatio = if (height > 0) width.toFloat() / height else 1f

        Matrix.setIdentityM(mProjectionMatrix, 0)
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, aspectRatio, 0.1f, 100f)

        Matrix.setIdentityM(mViewMatrix, 0)
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        rotationX += 0.5f
        rotationY += 0.3f

        Matrix.setIdentityM(mMatrix, 0)
        Matrix.rotateM(mMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(mMatrix, 0, rotationY, 0f, 1f, 0f)

        Matrix.multiplyMM(mMatrix, 0, mViewMatrix, 0, mMatrix, 0)
        Matrix.multiplyMM(mMatrix, 0, mProjectionMatrix, 0, mMatrix, 0)

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(colorHandle)

        vertexBuffer?.let { buffer ->
            GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                buffer
            )
        }

        colorBuffer?.let { buffer ->
            GLES20.glVertexAttribPointer(
                colorHandle,
                4,
                GLES20.GL_FLOAT,
                false,
                0,
                buffer
            )
        }

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMatrix, 0)

        indexBuffer?.let { buffer ->
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                buffer.limit(),
                GLES20.GL_UNSIGNED_SHORT,
                buffer
            )
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not compile shader: ${GLES20.glGetShaderInfoLog(shader)}")
        }

        return shader
    }

    private fun setupBuffers() {
        val vertices = floatArrayOf(
            -1f, -1f, -1f,
            1f, -1f, -1f,
            1f, 1f, -1f,
            -1f, 1f, -1f,
            -1f, -1f, 1f,
            1f, -1f, 1f,
            1f, 1f, 1f,
            -1f, 1f, 1f
        )

        val colors = floatArrayOf(
            1f, 0f, 0f, 1f,
            0f, 1f, 0f, 1f,
            0f, 0f, 1f, 1f,
            1f, 1f, 0f, 1f,
            1f, 0f, 1f, 1f,
            0f, 1f, 1f, 1f,
            1f, 1f, 1f, 1f,
            0f, 0f, 0f, 1f
        )

        val indices = shortArrayOf(
            0, 1, 2,
            0, 2, 3,
            4, 5, 6,
            4, 6, 7,
            0, 1, 5,
            0, 5, 4,
            2, 3, 7,
            2, 7, 6,
            1, 2, 6,
            1, 6, 5,
            4, 7, 3,
            4, 3, 0
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); rewind() }

        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(colors); rewind() }

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply { put(indices); rewind() }
    }
}