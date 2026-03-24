package com.example.myapplication.opengl

import android.content.Context
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
class CubeRenderer(private val context: Context) : Renderer {
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

    // Rotation angle for animation
    private var angle: Float = 0f
    
    // FPS counter and timing
    private var frameCount: Int = 0
    private var lastTime: Long = System.currentTimeMillis()
    private var fps: Int = 0

    // Cube vertices (8 vertices, 3 coordinates each)
    companion object {
        private val cubeVertices = floatArrayOf(
            // Front face
            -1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 1.0f,
            1.0f, 1.0f, 1.0f,
            1.0f, -1.0f, 1.0f,
            // Back face
            -1.0f, 1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f,
            1.0f, 1.0f, -1.0f,
            1.0f, -1.0f, -1.0f
        )

        // Vertex colors (RGB)
        private val cubeColors = floatArrayOf(
            // Front face (white)
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            // Back face (yellow)
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 1.0f
        )

        // Indices for 6 faces (12 triangles)
        private val cubeIndices = intArrayOf(
            // Front face
            0, 1, 2, 2, 1, 3,
            // Back face
            4, 5, 6, 6, 5, 7,
            // Left face
            4, 0, 6, 6, 0, 2,
            // Right face
            1, 3, 5, 5, 3, 7,
            // Top face
            4, 6, 0, 0, 6, 2,
            // Bottom face
            1, 5, 3, 3, 5, 7
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

        // Enable depth testing for 3D rendering
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Compile and link shaders
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        // Initialize vertex buffers
        initBuffers()
    }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        val ratio = width.toFloat() / height.toFloat()

        // Set up projection matrix using Matrix.perspectiveM()
        // Field of view: 45 degrees
        // Aspect ratio: width/height
        // Near clip plane: 1.0
        // Far clip plane: 10.0
        Matrix.perspectiveM(mProjectionMatrix, 0, 45f, ratio, 1f, 10f)

        // Set up view/camera matrix using Matrix.setLookAtM()
        // Eye position: (0, 0, -5) - looking at origin from negative z-axis
        // Center point: (0, 0, 0) - look at the origin
        // Up vector: (0, 1, 0) - positive y axis is up
        Matrix.setLookAtM(mViewMatrix, 0, 
            0f, 0f, -5f,  // Eye position
            0f, 0f, 0f,   // Center point
            0f, 1f, 0f)   // Up vector
    }

    override fun onDrawFrame(unused: GL10?) {
        // Clear screen and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Create a new model matrix (identity matrix)
        val mModelMatrix = FloatArray(16)
        Matrix.setIdentityM(mModelMatrix, 0)

        // Apply rotation to the model matrix using Matrix.rotateM()
        // Rotate around x and y axes for interesting animation
        // Rotation speed optimized for 60 FPS: 2 degrees per frame = 120 degrees per second
        Matrix.rotateM(mModelMatrix, 0, angle, 1f, 1f, 0f)
        angle += 2f  // Increment angle for continuous rotation

        // Calculate the model-view-projection matrix:
        // MVP = Projection * View * Model
        val mMVPMatrix = FloatArray(16)
        Matrix.multiplyMM(mMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMatrix, 0)

        // Draw the cube
        drawCube(mMVPMatrix)

        // FPS counter (for debugging/optimization)
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime >= 1000) {
            fps = frameCount
            frameCount = 0
            lastTime = currentTime
            // Log FPS for debugging (can be removed in production)
            // Log.d("CubeRenderer", "FPS: $fps")
        }
    }

    /**
     * Initialize vertex and color buffers for the cube.
     */
    private fun initBuffers() {
        // Create FloatBuffer for vertices with direct byte ordering
        val vertexByteBuffer = ByteBuffer.allocateDirect(cubeVertices.size * 4)
            .order(ByteOrder.nativeOrder())
        vertexBuffer = vertexByteBuffer.asFloatBuffer().apply {
            put(cubeVertices)
            position(0)
        }

        // Create FloatBuffer for colors with direct byte ordering
        val colorByteBuffer = ByteBuffer.allocateDirect(cubeColors.size * 4)
            .order(ByteOrder.nativeOrder())
        colorBuffer = colorByteBuffer.asFloatBuffer().apply {
            put(cubeColors)
            position(0)
        }
    }

    /**
     * Draw the cube using the provided MVP matrix.
     */
    private fun drawCube(mvpMatrix: FloatArray) {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(program)

        // Get handles for vertex attributes and uniform matrix
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        colorHandle = GLES20.glGetAttribLocation(program, "a_Color")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_MVPMatrix")

        // Enable vertex attribute arrays
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(colorHandle)

        // Pass the position data to OpenGL
        GLES20.glVertexAttribPointer(
            positionHandle, 3,
            GLES20.GL_FLOAT,
            false,
            0,
            vertexBuffer
        )

        // Pass the color data to OpenGL
        GLES20.glVertexAttribPointer(
            colorHandle, 4,
            GLES20.GL_FLOAT,
            false,
            0,
            colorBuffer
        )

        // Pass the MVP matrix to the shader
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // Draw the cube (6 faces = 36 vertices = 12 triangles)
        val indexBuffer = ByteBuffer.allocateDirect(cubeIndices.size * 4).order(ByteOrder.nativeOrder())
        indexBuffer.asIntBuffer().put(cubeIndices).position(0)
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            cubeIndices.size,
            GLES20.GL_UNSIGNED_INT,
            indexBuffer
        )

        // Disable vertex attribute arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    /**
     * Compile and link a shader program.
     */
    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)

        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShaderHandle)
            GLES20.glAttachShader(program, fragmentShaderHandle)
            GLES20.glLinkProgram(program)

            // Check for link errors
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                throw RuntimeException("Error linking program: " + 
                    GLES20.glGetProgramInfoLog(program))
            }

            // Clean up shaders
            GLES20.glDeleteShader(vertexShaderHandle)
            GLES20.glDeleteShader(fragmentShaderHandle)
        }
    }

    /**
     * Compile a shader.
     */
    private fun compileShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            // Check for compilation errors
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                throw RuntimeException("Error compiling shader: " + 
                    GLES20.glGetShaderInfoLog(shader))
            }
        }
    }
}