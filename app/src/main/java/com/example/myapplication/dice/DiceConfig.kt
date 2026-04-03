package com.example.myapplication.dice

/**
 * Central configuration for the dice game.
 * All magic numbers are centralized here for easy tuning.
 */
object DiceConfig {

    // ==================== TIMING ====================
    /** Auto-stop timer duration in milliseconds */
    const val AUTO_STOP_MS = 3000L

    /** Shake detection quiet period in samples */
    const val SHAKE_QUIET_NEEDED = 14

    /** Shake detection quiet period in milliseconds */
    const val SHAKE_QUIET_MS = 280L

    // ==================== SHAKE DETECTION ====================
    /** Low-pass filter alpha for gravity estimation */
    const val SHAKE_LP_ALPHA = 0.82f

    /** Shake threshold in m/s² */
    const val SHAKE_THRESH = 3.2f

    /** Quiet threshold in m/s² */
    const val SHAKE_QUIET_THRESH = 1.0f

    /** Inertia divisor for shake strength calculation */
    const val SHAKE_INERTIA_DIVISOR = 10f

    /** Minimum inertia for shake */
    const val SHAKE_INERTIA_MIN = 0.3f

    /** Maximum inertia for shake */
    const val SHAKE_INERTIA_MAX = 3.0f

    // ==================== PHYSICS ====================
    /** Friction during rolling phase (per frame) */
    const val ROLLING_FRICTION = 0.998f

    /** Base friction during stopping phase */
    const val STOP_FRICTION_BASE = 0.92f

    /** Inertia factor for friction during stopping */
    const val STOP_FRICTION_INERTIA_FACTOR = 0.055f

    /** Maximum friction during stopping */
    const val STOP_FRICTION_MAX = 0.985f

    /** Speed threshold to enter stopping phase (rad/s) */
    const val STOPPING_SPEED_THRESHOLD = 0.05f

    // ==================== SNAPPING ====================
    /** Speed factor for proportional snap control */
    const val SNAP_SPEED_FACTOR = 6.0f

    /** Maximum snap rotation speed (rad/s) */
    const val SNAP_MAX_SPEED = 8.0f

    /** Angle threshold for snap completion (rad) */
    const val SNAP_ANGLE_THRESHOLD = 0.008f

    // ==================== SCENE ====================
    /** Camera Z position */
    const val CAMERA_Z = 5.0f

    /** Die 1 offset Y position */
    const val DIE1_OFFSET_Y = 0.78f

    /** Die 2 offset Y position */
    const val DIE2_OFFSET_Y = -0.78f

    /** Camera field of view in degrees */
    const val CAMERA_FOV = 55f

    /** Camera near plane */
    const val CAMERA_NEAR = 0.1f

    /** Camera far plane */
    const val CAMERA_FAR = 20.0f

    // ==================== RENDERING ====================
    /** Vertex buffer stride in bytes */
    const val VERTEX_STRIDE = 32

    /** Maximum delta time for physics (seconds) */
    const val MAX_DELTA_TIME = 0.1f

    /** Initial delta time for physics (seconds) */
    const val INITIAL_DELTA_TIME = 0.016f

    // ==================== UI ====================
    /** Dice face emojis */
    const val DICE_EMOJI_1 = "⚀"
    const val DICE_EMOJI_2 = "⚁"
    const val DICE_EMOJI_3 = "⚂"
    const val DICE_EMOJI_4 = "⚃"
    const val DICE_EMOJI_5 = "⚄"
    const val DICE_EMOJI_6 = "⚅"

    /** Haptic feedback amplitude for roll button */
    const val HAPTIC_ROLL_AMPLITUDE = 30

    /** Haptic feedback amplitude for result */
    const val HAPTIC_RESULT_AMPLITUDE = 50

    // ==================== SHADERS ====================
    /** Vertex shader source */
    val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec2 aTexCoord;
        
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        
        varying vec3 vNormal;
        varying vec2 vTexCoord;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vNormal = (uModelMatrix * vec4(aNormal, 0.0)).xyz;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    /** Fragment shader source */
    val FRAGMENT_SHADER = """
        precision mediump float;
        
        varying vec3 vNormal;
        varying vec2 vTexCoord;
        
        uniform vec3 uLightDir;
        uniform vec3 uAmbientColor;
        uniform vec3 uDiffuseColor;
        
        void main() {
            // Normalize normal
            vec3 normal = normalize(vNormal);
            
            // Diffuse lighting
            float diff = max(dot(normal, uLightDir), 0.0);
            
            // Combine ambient and diffuse
            vec3 color = uAmbientColor + uDiffuseColor * diff;
            
            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()
}