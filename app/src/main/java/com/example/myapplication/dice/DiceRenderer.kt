package com.example.myapplication.dice

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for the dice game.
 * Manages the scene, camera, and rendering of two dice.
 */
class DiceRenderer : GLSurfaceView.Renderer {
    
    // ==================== DICE ====================
    
    /** First die */
    private val die1 = Die()
    
    /** Second die */
    private val die2 = Die()
    
    // ==================== SHADER PROGRAM ====================
    
    /** Shader program */
    private var program = 0
    
    // ==================== TEXTURES ====================
    
    /** Texture IDs for each face of the dice */
    private var textureIds = IntArray(6)
    
    // ==================== CAMERA & PROJECTION ====================
    
    /** Camera position */
    private val cameraPosition = FloatArray(3)
    
    /** View matrix */
    private val viewMatrix = FloatArray(16)
    
    /** Projection matrix */
    private val projectionMatrix = FloatArray(16)
    
    /** MVP matrix */
    private val mvpMatrix = FloatArray(16)
    
    /** Model matrix for each die */
    private val modelMatrix1 = FloatArray(16)
    private val modelMatrix2 = FloatArray(16)
    
    // ==================== INITIALIZATION ====================
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set background color (dark gray)
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        
        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        // Compile and link shaders
        program = compileAndLinkProgram(
            DiceConfig.VERTEX_SHADER,
            DiceConfig.FRAGMENT_SHADER
        )
        
        // Generate textures
        generateDiceTextures()
        
        // Reset dice
        die1.reset()
        die2.reset()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Set viewport
        GLES20.glViewport(0, 0, width, height)
        
        // Calculate aspect ratio
        val aspectRatio = if (height > 0) width.toFloat() / height else 1.0f
        
        // Set up projection matrix
        Matrix.perspectiveM(
            projectionMatrix,
            0,
            DiceConfig.CAMERA_FOV,
            aspectRatio,
            DiceConfig.CAMERA_NEAR,
            DiceConfig.CAMERA_FAR
        )
        
        // Set up camera
        cameraPosition[0] = 0.0f
        cameraPosition[1] = 0.0f
        cameraPosition[2] = DiceConfig.CAMERA_Z
        
        // Look-at matrix
        Matrix.setLookAtM(
            viewMatrix,
            0,
            cameraPosition[0],
            cameraPosition[1],
            cameraPosition[2],
            0.0f, 0.0f, 0.0f, // Look at origin
            0.0f, 1.0f, 0.0f  // Up vector
        )
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Set up MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        
        // Update dice
        val deltaTime = DiceConfig.INITIAL_DELTA_TIME
        die1.update(deltaTime)
        die2.update(deltaTime)
        
        // Set up model matrices
        Matrix.setIdentityM(modelMatrix1, 0)
        Matrix.translateM(modelMatrix1, 0, 0.0f, DiceConfig.DIE1_OFFSET_Y, 0.0f)
        
        Matrix.setIdentityM(modelMatrix2, 0)
        Matrix.translateM(modelMatrix2, 0, 0.0f, DiceConfig.DIE2_OFFSET_Y, 0.0f)
        
        // Draw dice
        drawDie(die1, program, mvpMatrix, modelMatrix1, textureIds)
        drawDie(die2, program, mvpMatrix, modelMatrix2, textureIds)
    }
    
    // ==================== DRAWING ====================
    
    /**
     * Draw a single die
     */
    private fun drawDie(die: Die, program: Int, mvpMatrix: FloatArray, modelMatrix: FloatArray, textureIds: IntArray) {
        // Set up vertex attributes
        val positionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val normalLoc = GLES20.glGetAttribLocation(program, "aNormal")
        val texCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        
        val mvpLoc = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val modelLoc = GLES20.glGetUniformLocation(program, "uModelMatrix")
        val lightDirLoc = GLES20.glGetUniformLocation(program, "uLightDir")
        val ambientLoc = GLES20.glGetUniformLocation(program, "uAmbientColor")
        val diffuseLoc = GLES20.glGetUniformLocation(program, "uDiffuseColor")
        
        // Set MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)
        
        // Set model matrix
        GLES20.glUniformMatrix4fv(modelLoc, 1, false, die.rotationMatrix, 0)
        
        // Set light direction
        GLES20.glUniform3f(lightDirLoc, 1.0f, 2.0f, 1.5f)
        
        // Set ambient color
        GLES20.glUniform3f(ambientLoc, 0.38f, 0.38f, 0.38f)
        
        // Set diffuse color
        GLES20.glUniform3f(diffuseLoc, 0.62f, 0.62f, 0.62f)
        
        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glEnableVertexAttribArray(normalLoc)
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        
        // Set vertex data
        GLES20.glVertexAttribPointer(positionLoc, 3, GLES20.GL_FLOAT, false, DiceConfig.VERTEX_STRIDE, die.vertexBuffer)
        GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, DiceConfig.VERTEX_STRIDE, die.vertexBuffer)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, DiceConfig.VERTEX_STRIDE, die.vertexBuffer)
        
        // Draw each face with its texture
        for (i in 0 until 6) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i])
            
            // Set texture uniform
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), i)
            
            // Draw face
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, die.indexBuffer)
        }
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(normalLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
    }
    
    // ==================== TEXTURE GENERATION ====================
    
    /**
     * Generate textures for each face of the dice
     */
    private fun generateDiceTextures() {
        // Generate texture IDs
        GLES20.glGenTextures(6, textureIds, 0)
        
        // Create texture for each face
        for (i in 0 until 6) {
            val textureId = textureIds[i]
            
            // Bind texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            
            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            
            // Generate texture from canvas
            val bitmap = createDiceFaceTexture(i)
            
            // Upload texture
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                bitmap.width,
                bitmap.height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                ByteBuffer.wrap(bitmap.pixelBuffer.array())
            )
        }
        
        // Unbind texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }
    
    /**
     * Create a texture for a single dice face
     */
    private fun createDiceFaceTexture(faceIndex: Int): DiceFaceTexture {
        return DiceFaceTexture(faceIndex)
    }
    
    // ==================== SHADER COMPILATION ====================
    
    /**
     * Compile a shader
     */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        
        // Check compilation status
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        
        if (compiled[0] == 0) {
            val infoLog = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation error: $infoLog")
        }
        
        return shader
    }
    
    /**
     * Compile and link a shader program
     */
    private fun compileAndLinkProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        // Check linking status
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        
        if (linked[0] == 0) {
            val infoLog = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program linking error: $infoLog")
        }
        
        // Clean up shaders
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        
        return program
    }
    
    // ==================== PUBLIC METHODS ====================
    
    /**
     * Get the result of the first die
     */
    fun getDie1Result(): Int {
        return die1.resultFaceIdx
    }
    
    /**
     * Get the result of the second die
     */
    fun getDie2Result(): Int {
        return die2.resultFaceIdx
    }
    
    /**
     * Check if dice are currently rolling
     */
    fun isRolling(): Boolean {
        return die1.state == Die.State.ROLLING || die2.state == Die.State.ROLLING
    }
    
    /**
     * Check if dice are currently stopping
     */
    fun isStopping(): Boolean {
        return die1.state == Die.State.STOPPING || die2.state == Die.State.STOPPING
    }
    
    /**
     * Check if dice are currently snapping
     */
    fun isSnapping(): Boolean {
        return die1.state == Die.State.SNAPPING || die2.state == Die.State.SNAPPING
    }
    
    /**
     * Check if dice are finished
     */
    fun isFinished(): Boolean {
        return die1.state == Die.State.FINISHED && die2.state == Die.State.FINISHED
    }
    
    /**
     * Reset the dice
     */
    fun reset() {
        die1.reset()
        die2.reset()
    }
    
    /**
     * Roll the dice
     */
    fun roll() {
        die1.roll()
        die2.roll()
    }
    
    /**
     * Stop the dice with specified inertia
     */
    fun stop(inertia: Float) {
        die1.updateStopping(inertia)
        die2.updateStopping(inertia)
    }
}

/**
 * Helper class to create dice face textures
 */
class DiceFaceTexture(private val faceIndex: Int) {
    val width = 256
    val height = 256
    val pixelBuffer: ByteBuffer
    
    init {
        // Create bitmap
        val bitmap = createBitmap()
        
        // Get pixel buffer
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)
        pixelBuffer.asIntBuffer().put(pixels)
    }
    
    private fun createBitmap(): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw background (red)
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.parseColor("#FF4444")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        
        // Draw pips (white dots)
        paint.color = android.graphics.Color.parseColor("#FFFFFF")
        
        val pipSize = 40f
        val pipSpacing = 80f
        
        // Draw pips based on face index
        when (faceIndex) {
            0 -> { // 1 pip (center)
                drawPip(canvas, width / 2f, height / 2f, pipSize)
            }
            1 -> { // 2 pips (diagonal)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
            }
            2 -> { // 3 pips (diagonal)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f, height / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
            }
            3 -> { // 4 pips (corners)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
            }
            4 -> { // 5 pips (corners + center)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f, height / 2f, pipSize)
            }
            5 -> { // 6 pips (two columns)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f - pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f, pipSize)
                drawPip(canvas, width / 2f - pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
                drawPip(canvas, width / 2f + pipSpacing / 2f, height / 2f + pipSpacing / 2f, pipSize)
            }
        }
        
        return bitmap
    }
    
    private fun drawPip(canvas: android.graphics.Canvas, x: Float, y: Float, size: Float) {
        val paint = android.graphics.Paint()
        paint.color = android.graphics.Color.parseColor("#FFFFFF")
        paint.style = android.graphics.Paint.Style.FILL
        
        val oval = android.graphics.RectF(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
        canvas.drawOval(oval, paint)
    }
}