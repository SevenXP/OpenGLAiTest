package com.example.myapplication.dice

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * 3D Die (cube) with physics-based rotation animation.
 * 
 * State machine:
 * IDLE → ROLLING → STOPPING → SNAPPING → IDLE
 */
class Die {
    // ==================== GEOMETRY ====================

    // 24 vertices (4 per face * 6 faces)
    // Each vertex: position(3) + normal(3) + uv(2) = 8 floats
    private val vertices = floatArrayOf(
        // Front face (z+)
        -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f,
        0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
        0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f,
        -0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,

        // Back face (z-)
        -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0.0f, 1.0f,
        0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 1.0f, 1.0f,
        0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 1.0f, 0.0f,
        -0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f,

        // Top face (y+)
        -0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f,
        0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f,
        -0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,

        // Bottom face (y-)
        -0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f,
        0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f, 1.0f, 1.0f,
        0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f, 1.0f, 0.0f,
        -0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f,

        // Right face (x+)
        0.5f, -0.5f, 0.5f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f,
        0.5f, 0.5f, -0.5f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,

        // Left face (x-)
        -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        -0.5f, -0.5f, 0.5f, -1.0f, 0.0f, 0.0f, 1.0f, 1.0f,
        -0.5f, 0.5f, 0.5f, -1.0f, 0.0f, 0.0f, 1.0f, 0.0f,
        -0.5f, 0.5f, -0.5f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f
    )

    // 36 indices (6 faces * 2 triangles * 3 indices)
    private val indices = shortArrayOf(
        0, 1, 2, 0, 2, 3,  // Front
        4, 5, 6, 4, 6, 7,  // Back
        8, 9, 10, 8, 10, 11,  // Top
        12, 13, 14, 12, 14, 15,  // Bottom
        16, 17, 18, 16, 18, 19,  // Right
        20, 21, 22, 20, 22, 23   // Left
    )

    // Buffers
    val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertices)
        .position(0) as FloatBuffer

    val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
        .put(indices)
        .position(0) as ShortBuffer

    // ==================== STATE ====================

    /** Current rotation matrix (4x4) */
    val rotationMatrix = FloatArray(16)

    /** Current rotation speed (rad/s) */
    var rotationSpeed = 0.0f

    /** Current state */
    var state = State.IDLE
        private set

    /** Result face index (0-5) */
    var resultFaceIdx = -1
        private set

    /** Current accumulated rotation (for snap calculation) */
    private var accumulatedRotation = 0.0f

    /** Target rotation for snap animation */
    private var targetRotation = 0.0f

    /** Snap progress (0.0 to 1.0) */
    private var snapProgress = 0.0f

    /** Random seed for this die */
    private val randomSeed = System.currentTimeMillis()

    // Scratch matrices for performance
    private val scratchModel = FloatArray(16)
    private val scratchMvp = FloatArray(16)

    // ==================== STATE ENUM ====================

    enum class State {
        IDLE,        // Waiting for roll
        ROLLING,     // Random rotation
        STOPPING,    // Decelerating
        SNAPPING,    // Aligning to camera
        FINISHED     // Animation complete
    }

    // ==================== INITIALIZATION ====================

    init {
        // Initialize rotation matrix to identity
        Matrix.setIdentityM(rotationMatrix, 0)
        accumulatedRotation = 0.0f
        snapProgress = 0.0f
    }

    // ==================== PUBLIC METHODS ====================

    /**
     * Start rolling the die
     */
    fun roll() {
        state = State.ROLLING
        rotationSpeed = (Math.random() * 4 + 4).toFloat() // 4-8 rad/s
        accumulatedRotation = 0.0f
        resultFaceIdx = -1
    }

    /**
     * Update physics state
     * @param deltaTime Time since last frame (seconds)
     */
    fun update(deltaTime: Float) {
        when (state) {
            State.ROLLING -> updateRolling(deltaTime)
            State.STOPPING -> updateStopping(deltaTime)
            State.SNAPPING -> updateSnapping(deltaTime)
            else -> {
                // No update needed
            }
        }
    }

    /**
     * Get the accumulated rotation angle (for result calculation)
     */
    fun getAccumulatedRotation(): Float {
        return accumulatedRotation
    }

    /**
     * Reset the die to IDLE state
     */
    fun reset() {
        state = State.IDLE
        rotationSpeed = 0.0f
        accumulatedRotation = 0.0f
        resultFaceIdx = -1
        snapProgress = 0.0f
        Matrix.setIdentityM(rotationMatrix, 0)
    }

    // ==================== PRIVATE UPDATE METHODS ====================

    private fun updateRolling(deltaTime: Float) {
        // Apply random perturbations
        rotationSpeed += (Math.random() * 0.6 - 0.3).toFloat()

        // Apply rolling friction
        rotationSpeed *= DiceConfig.ROLLING_FRICTION

        // Update rotation
        val deltaRotation = rotationSpeed * deltaTime
        accumulatedRotation += deltaRotation

        // Apply rotation to matrix
        Matrix.rotateM(rotationMatrix, 0, (deltaRotation * (180.0f / Math.PI)).toFloat(), 0f, 1f, 0f)

        // Check if we should stop
        if (rotationSpeed < DiceConfig.STOPPING_SPEED_THRESHOLD) {
            state = State.STOPPING
        }
    }

    fun updateStopping(deltaTime: Float) {
        // Calculate inertia-based friction
        val inertia = Math.min(rotationSpeed / 10f, 3.0f)
        val friction = DiceConfig.STOP_FRICTION_BASE + DiceConfig.STOP_FRICTION_INERTIA_FACTOR * inertia
        val clampedFriction = Math.min(friction, DiceConfig.STOP_FRICTION_MAX)

        // Apply friction
        rotationSpeed *= clampedFriction

        // Update rotation
        val deltaRotation = rotationSpeed * deltaTime
        accumulatedRotation += deltaRotation

        // Apply rotation to matrix
        Matrix.rotateM(rotationMatrix, 0, (deltaRotation * (180.0f / Math.PI)).toFloat(), 0f, 1f, 0f)

        // Check if we should snap
        if (rotationSpeed < DiceConfig.STOPPING_SPEED_THRESHOLD) {
            state = State.SNAPPING
            snapProgress = 0.0f
            calculateTargetRotation()
        }
    }

    private fun updateSnapping(deltaTime: Float) {
        // Proportional control: speed = gain * error
        val error = targetRotation - accumulatedRotation
        val speed = DiceConfig.SNAP_SPEED_FACTOR * error

        // Clamp speed
        val clampedSpeed = Math.min(Math.abs(speed), DiceConfig.SNAP_MAX_SPEED) * Math.signum(speed)

        // Update progress
        snapProgress += deltaTime / 0.5f // Snap takes 0.5 seconds

        if (snapProgress >= 1.0f || Math.abs(error) < DiceConfig.SNAP_ANGLE_THRESHOLD) {
            // Snap complete
            accumulatedRotation = targetRotation
            rotationSpeed = 0.0f
            state = State.FINISHED
            resultFaceIdx = calculateResultFace()
        } else {
            // Continue snapping
            val deltaRotation = clampedSpeed * deltaTime
            accumulatedRotation += deltaRotation
            Matrix.rotateM(rotationMatrix, 0, (deltaRotation * (180.0f / Math.PI)).toFloat(), 0f, 1f, 0f)
        }
    }

    private fun calculateTargetRotation() {
        // Calculate which face should be facing the camera (+Z)
        // The result is determined by the accumulated rotation
        // We need to find the face with maximum projection on +Y (that's "lookinging up")

        // Normalize accumulated rotation to 0-2π
        val normalizedRotation = accumulatedRotation % (2 * Math.PI)

        // Determine which face is now facing up (+Y)
        // This is a simplified calculation based on rotation
        val faceIndex = (normalizedRotation / (Math.PI / 3)).toInt() % 6

        // Map to result index (0-5)
        resultFaceIdx = faceIndex

        // Calculate target rotation to align this face to camera
        // The face normal should be (0, 1, 0) for +Y
        // We need to rotate to align it to +Z (camera)
        targetRotation = (faceIndex * Math.PI / 3).toFloat()
    }

    private fun calculateResultFace(): Int {
        // This is a simplified result calculation
        // In a full implementation, we would track which face is currently facing up
        // and map it to the result index

        // For now, return a random result based on accumulated rotation
        val normalizedRotation = accumulatedRotation % (2 * Math.PI)
        return (normalizedRotation / (Math.PI / 3)).toInt() % 6
    }

    // ==================== DRAWING ====================

    /**
     * Draw the die
     * @param program OpenGL program
     * @param mvpMatrix MVP matrix
     * @param modelMatrix Model matrix
     * @param textureIds Array of 6 texture IDs (one per face)
     */
    fun draw(program: Int, mvpMatrix: FloatArray, modelMatrix: FloatArray, textureIds: IntArray) {
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
        Matrix.multiplyMM(scratchMvp, 0, mvpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, scratchMvp, 0)

        // Set model matrix
        GLES20.glUniformMatrix4fv(modelLoc, 1, false, rotationMatrix, 0)

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
        GLES20.glVertexAttribPointer(positionLoc, 3, GLES20.GL_FLOAT, false, DiceConfig.VERTEX_STRIDE, vertexBuffer)
        GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, DiceConfig.VERTEX_STRIDE, vertexBuffer)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, DiceConfig.VERTEX_STRIDE, vertexBuffer)

        // Draw each face with its texture
        intArrayOf(0, 1, 2, 0, 2, 3) // Front
        floatArrayOf(0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f)

        // Bind textures for each face
        for (i in 0 until 6) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[i])

            // Set texture uniform
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), i)

            // Draw face
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, indexBuffer)
        }

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(normalLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
    }
}