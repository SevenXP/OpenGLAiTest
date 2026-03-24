package com.example.myapplication.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView.Renderer
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * CubeRenderer implements OpenGL ES 2.0 rendering for a 3D cube.
 * 
 * This implementation uses ONLY android.opengl.* packages for all matrix operations:
 * - Matrix.setIdentityM() for identity matrices
 * - Matrix.perspectiveM() for projection matrix
 * - Matrix.setLookAtM() for view/camera matrix
 * - Matrix.multiplyMM() for matrix multiplication
 * - Matrix.rotateM() for model transformations
 */
class CubeRenderer : Renderer {
    // Matrix arrays as specified in requirements
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMatrix = FloatArray(16)

    // Program and shader handles
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    // Vertex data
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var colorBuffer: FloatBuffer

    // Cube rotation angle
    private var cubeRotation: Float = 0f

    companion object {
        // Vertex coordinates (X, Y, Z) for a cube with 6 faces
        private val CUBE_COORDINATES = floatArrayOf(
            // Front face
            -1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 1.0f,
            1.0f, -1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,

            // Right face
            1.0f, 1.0f, 1.0f,
            1.0f, -1.0f, 1.0f,
            1.0f, 1.0f, -1.0f,
            1.0f, -1.0f, 1.0f,
            1.0f, -1.0f, -1.0f,
            1.0f, 1.0f, -1.0f,

            // Back face
            1.0f, 1.0f, -1.0f,
            1.0f, -1.0f, -1.0f,
            -1.0f, 1.0f, -1.0f,
            1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f,
            -1.0f, 1.0f, -1.0f,

            // Left face
            -1.0f, 1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f,
            -1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, 1.0f,
            -1.0f, 1.0f, 1.0f,

            // Top face
            -1.0f, 1.0f, -1.0f,
            -1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, -1.0f,
            -1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, -1.0f,

            // Bottom face
            1.0f, -1.0f, -1.0f,
            1.0f, -1.0f, 1.0f,
            -1.0f, -1.0f, -1.0f,
            1.0f, -1.0f, 1.0f,
            -1.0f, -1.0f, 1.0f,
            -1.0f, -1.0f, -1.0f
        )

        // Color data (R, G, B, A) for each vertex
        private val CUBE_COLORS = floatArrayOf(
            // Front face (red)
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,

            // Right face (green)
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,

            // Back face (blue)
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,

            // Left face (yellow)
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,

            // Top face (cyan)
            0.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 1.0f, 1.0f,
            0.0f, 1.0f, 1.0f, 1.0f,

            // Bottom face (magenta)
            1.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 1.0f, 1.0f
        )

        // Vertex shader in GLSL
        private const val VERTEX_SHADER = """
            uniform mat4 u_MVPMatrix;
            attribute vec4 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            
            void main() {
                gl_Position = u_MVPMatrix * a_Position;
                v_Color = a_Color;
            }
        """

        // Fragment shader in GLSL
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec4 v_Color;
            
            void main() {
                gl_FragColor = v_Color;
            }
        """
    }

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        // Set the background clear color to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Enable depth testing for proper 3D rendering
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Compile and link shaders
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        // Initialize vertex buffers
        initBuffers()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        // Set the viewport based on the surface dimensions
        GLES20.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height.toFloat()

        // Matrix operation: Create projection matrix using Matrix.perspectiveM()
        // This defines the camera's view frustum (field of view, aspect ratio, near/far planes)
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, ratio, 0.1f, 100f)

        // Matrix operation: Create view matrix using Matrix.setLookAtM()
        // This sets up the camera position and orientation (eye position, center point, up vector)
        Matrix.setLookAtM(mViewMatrix, 0, 
            0f, 0f, -5f,  // Camera position
            0f, 0f, 0f,   // Look at origin
            0f, 1f, 0f)   // Up vector (Y axis)
    }

    override fun onDrawFrame(unused: GL10?) {
        // Clear the screen and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Matrix operation: Set model matrix to identity
        Matrix.setIdentityM(mMatrix, 0)

        // Matrix operation: Apply rotation transformation using Matrix.rotateM()
        // Rotate the cube around the X and Y axes for animation
        Matrix.rotateM(mMatrix, 0, cubeRotation, 1f, 1f, 0f)

        // Combine matrices: projection * view * model
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, mViewMatrix, 0, mMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mProjectionMatrix, 0, mvpMatrix, 0)

        // Draw the cube
        drawCube(mvpMatrix)

        // Update rotation angle for next frame
        cubeRotation += 1.2f
    }

    /**
     * Initialize vertex and color buffers from the cube data.
     */
    private fun initBuffers() {
        // Create FloatBuffer for vertex coordinates
        vertexBuffer = ByteBuffer.allocateDirect(CUBE_COORDINATES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(CUBE_COORDINATES)
        vertexBuffer.position(0)

        // Create FloatBuffer for color data
        colorBuffer = ByteBuffer.allocateDirect(CUBE_COLORS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(CUBE_COLORS)
        colorBuffer.position(0)
    }

    /**
     * Draw the cube using the provided MVP matrix.
     */
    private fun drawCube(mvpMatrix: FloatArray) {
        // Use the program object
        GLES20.glUseProgram(program)

        // Get handle to vertex shader's position attribute
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        GLES20.glEnableVertexAttribArray(positionHandle)

        // Prepare the coordinate data
        GLES20.glVertexAttribPointer(
            positionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        // Get handle to fragment shader's color attribute
        colorHandle = GLES20.glGetAttribLocation(program, "a_Color")
        GLES20.glEnableVertexAttribArray(colorHandle)

        // Prepare the color data
        GLES20.glVertexAttribPointer(
            colorHandle,
            4,
            GLES20.GL_FLOAT,
            false,
            0,
            colorBuffer
        )

        // Get handle to shape's transformation matrix
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")

        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Draw the cube (6 faces, 12 triangles, 36 vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, CUBE_COORDINATES.size / 3)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    /**
     * Compiles and links a shader program.
     */
    private fun createProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)

        val program = GLES20.glCreateProgram()
        check(program != 0) { "Could not create new program" }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            throw RuntimeException("Error linking program: " + 
                    GLES20.glGetProgramInfoLog(program))
        }

        return program
    }

    /**
     * Compiles a shader.
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "Could not create new shader" }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            throw RuntimeException("Error compiling shader: " + 
                    GLES20.glGetShaderInfoLog(shader))
        }

        return shader
    }
}