package com.example.myapplication.opengl

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.opengl.GLSurfaceView
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Рендерер 3D-куба с использованием OpenGL ES 2.0 и матричных операций.
 * 
 * Использует только android.opengl.Matrix для всех матричных преобразований:
 * - Проекционная матрица (perspective projection)
 * - View матрица (камера)
 * - Model матрица (трансформации объекта: вращение, перемещение)
 * 
 * Ограничивает FPS до 60 кадров/сек для оптимизации производительности.
 */
class CubeRenderer(private val context: Context) : GLSurfaceView.Renderer {
    
    companion object {
        private const val TAG = "CubeRenderer"
        
        // Целевое время кадра в наносекундах (1 секунда / 60 FPS)
        private const val TARGET_FPS = 60
        private const val TARGET_FRAME_TIME_NANOS: Long = 1_000_000_000L / TARGET_FPS
    }
    
    // Матрицы трансформаций
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    
    // Буферы вершин и цветов
    private var mTriangleVertices: FloatBuffer? = null
    private var mColors: FloatBuffer? = null
    
    // Переменные шейдеров
    private var mMVPMatrixHandle = -1
    private var mPositionHandle = -1
    private var mColorHandle = -1
    
    // ID программы шейдеров
    private var mProgramId = 0
    
    // Угол вращения куба
    private var rotationAngle = 0f
    
    // Переменные для ограничения FPS
    private var lastFrameTimeNanos: Long = 0
    
    // Vertex Shader - обрабатывает вершины
    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aColor;
        uniform mat4 uMVPMatrix;
        varying vec4 vColor;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vColor = aColor;
        }
    """.trimIndent()
    
    // Fragment Shader - обрабатывает пиксели
    private val fragmentShaderCode = """
        precision mediump float;
        varying vec4 vColor;
        
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()
    
    // Вершины куба (36 вершин для 12 треугольников)
    // Каждая грань состоит из 2 треугольников по 3 вершины
    private val triangleVerticesData = floatArrayOf(
        // Front face (Z = +1) - Red
         -1.0f, -1.0f,  1.0f,
          1.0f, -1.0f,  1.0f,
         -1.0f,  1.0f,  1.0f,
         -1.0f,  1.0f,  1.0f,
          1.0f, -1.0f,  1.0f,
          1.0f,  1.0f,  1.0f,
        
        // Back face (Z = -1) - Green
         -1.0f, -1.0f, -1.0f,
         -1.0f,  1.0f, -1.0f,
          1.0f, -1.0f, -1.0f,
          1.0f, -1.0f, -1.0f,
         -1.0f,  1.0f, -1.0f,
          1.0f,  1.0f, -1.0f,
        
        // Top face (Y = +1) - Blue
         -1.0f,  1.0f, -1.0f,
          1.0f,  1.0f, -1.0f,
         -1.0f,  1.0f,  1.0f,
         -1.0f,  1.0f,  1.0f,
          1.0f,  1.0f, -1.0f,
          1.0f,  1.0f,  1.0f,
        
        // Bottom face (Y = -1) - Yellow
         -1.0f, -1.0f, -1.0f,
         -1.0f, -1.0f,  1.0f,
          1.0f, -1.0f, -1.0f,
          1.0f, -1.0f, -1.0f,
         -1.0f, -1.0f,  1.0f,
          1.0f, -1.0f,  1.0f,
        
        // Right face (X = +1) - Cyan
          1.0f, -1.0f, -1.0f,
          1.0f,  1.0f, -1.0f,
          1.0f, -1.0f,  1.0f,
          1.0f, -1.0f,  1.0f,
          1.0f,  1.0f, -1.0f,
          1.0f,  1.0f,  1.0f,
        
        // Left face (X = -1) - Magenta
         -1.0f, -1.0f, -1.0f,
         -1.0f, -1.0f,  1.0f,
         -1.0f,  1.0f, -1.0f,
         -1.0f,  1.0f, -1.0f,
         -1.0f, -1.0f,  1.0f,
         -1.0f,  1.0f,  1.0f
    )
    
    // Цвета для каждой вершины (RGBA)
    private val colorData = floatArrayOf(
        // Front face - Red
         1.0f, 0.0f, 0.0f, 1.0f,
         1.0f, 0.0f, 0.0f, 1.0f,
         1.0f, 0.0f, 0.0f, 1.0f,
         1.0f, 0.0f, 0.0f, 1.0f,
         1.0f, 0.0f, 0.0f, 1.0f,
         1.0f, 0.0f, 0.0f, 1.0f,
        
        // Back face - Green
         0.0f, 1.0f, 0.0f, 1.0f,
         0.0f, 1.0f, 0.0f, 1.0f,
         0.0f, 1.0f, 0.0f, 1.0f,
         0.0f, 1.0f, 0.0f, 1.0f,
         0.0f, 1.0f, 0.0f, 1.0f,
         0.0f, 1.0f, 0.0f, 1.0f,
        
        // Top face - Blue
         0.0f, 0.0f, 1.0f, 1.0f,
         0.0f, 0.0f, 1.0f, 1.0f,
         0.0f, 0.0f, 1.0f, 1.0f,
         0.0f, 0.0f, 1.0f, 1.0f,
         0.0f, 0.0f, 1.0f, 1.0f,
         0.0f, 0.0f, 1.0f, 1.0f,
        
        // Bottom face - Yellow
         1.0f, 1.0f, 0.0f, 1.0f,
         1.0f, 1.0f, 0.0f, 1.0f,
         1.0f, 1.0f, 0.0f, 1.0f,
         1.0f, 1.0f, 0.0f, 1.0f,
         1.0f, 1.0f, 0.0f, 1.0f,
         1.0f, 1.0f, 0.0f, 1.0f,
        
        // Right face - Cyan
         0.0f, 1.0f, 1.0f, 1.0f,
         0.0f, 1.0f, 1.0f, 1.0f,
         0.0f, 1.0f, 1.0f, 1.0f,
         0.0f, 1.0f, 1.0f, 1.0f,
         0.0f, 1.0f, 1.0f, 1.0f,
         0.0f, 1.0f, 1.0f, 1.0f,
        
        // Left face - Magenta
         1.0f, 0.0f, 1.0f, 1.0f,
         1.0f, 0.0f, 1.0f, 1.0f,
         1.0f, 0.0f, 1.0f, 1.0f,
         1.0f, 0.0f, 1.0f, 1.0f,
         1.0f, 0.0f, 1.0f, 1.0f,
         1.0f, 0.0f, 1.0f, 1.0f
    )
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated - Инициализация OpenGL ES")
        
        // Включаем глубинный буфер для правильного отображения 3D
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // Устанавливаем цвет фона (темно-синий)
        GLES20.glClearColor(0.1f, 0.1f, 0.2f, 1.0f)
        
        // Компилируем шейдеры и создаем программу
        mProgramId = buildShaderProgram()
        
        if (mProgramId == 0) {
            Log.e(TAG, "Ошибка создания программы шейдеров")
            return
        }
        
        // Получаем дескрипторы переменных шейдера
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uMVPMatrix")
        mPositionHandle = GLES20.glGetAttribLocation(mProgramId, "aPosition")
        mColorHandle = GLES20.glGetAttribLocation(mProgramId, "aColor")
        
        // Создаем буферы для вершин и цветов
        createBuffers()
        
        Log.d(TAG, "onSurfaceCreated - Инициализация завершена")
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged - Размер поверхности: ${width}x${height}")
        
        // Устанавливаем viewport на весь экран
        GLES20.glViewport(0, 0, width, height)
        
        // === ПРОЕКЦИОННАЯ МАТРИЦА ===
        // Matrix.perspectiveM создает перспективную проекцию
        // Параметры: матрица, offset, FOV (угол обзора), aspect ratio, near plane, far plane
        val aspectRatio = if (height != 0) width.toFloat() / height else 1f
        
        Matrix.perspectiveM(
            mProjectionMatrix,
            0,
            45f,           // FOV = 45 градусов
            aspectRatio,   // Соотношение сторон
            1.0f,          // Ближняя плоскость отсечения
            20.0f          // Дальняя плоскость отсечения
        )
        
        // === VIEW МАТРИЦА (КАМЕРА) ===
        // Matrix.setLookAtM устанавливает позицию камеры и точку наблюдения
        // Камера находится в точке (0, 0, 8), смотрит на центр сцены (0, 0, 0)
        // Вектор "вверх" = (0, 1, 0) - стандартная ориентация
        Matrix.setLookAtM(
            mViewMatrix,
            0,
            0.0f, 0.0f, 8.0f,   // Позиция камеры (eyeX, eyeY, eyeZ)
            0.0f, 0.0f, 0.0f,   // Точка наблюдения (centerX, centerY, centerZ)
            0.0f, 1.0f, 0.0f    // Вектор "вверх" (upX, upY, upZ)
        )
        
        Log.d(TAG, "onSurfaceChanged - Матрицы настроены")
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // === ОГРАНИЧЕНИЕ FPS ДО 60 КАДРОВ/СЕК ===
        val currentTimeNanos = System.nanoTime()
        
        if (lastFrameTimeNanos > 0) {
            val elapsedNanos = currentTimeNanos - lastFrameTimeNanos
            val sleepTimeNanos = TARGET_FRAME_TIME_NANOS - elapsedNanos
            
            if (sleepTimeNanos > 0) {
                try {
                    // Переводим наносекунды в миллисекунды для Thread.sleep()
                    Thread.sleep(sleepTimeNanos / 1_000_000)
                } catch (e: InterruptedException) {
                    Log.w(TAG, "Прерывание сна при ограничении FPS", e)
                    Thread.currentThread().interrupt()
                }
            }
        }
        lastFrameTimeNanos = System.nanoTime()
        
        // Очистка экрана и глубинного буфера
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Активируем программу шейдеров
        GLES20.glUseProgram(mProgramId)
        
        if (mPositionHandle == -1 || mColorHandle == -1 || mMVPMatrixHandle == -1) {
            Log.e(TAG, "Невалидные дескрипторы шейдера")
            return
        }
        
        // === MODEL МАТРИЦА (ТРАНСФОРМАЦИИ ОБЪЕКТА) ===
        // Сбрасываем model матрицу к единичной
        Matrix.setIdentityM(mModelMatrix, 0)
        
        // Применяем вращение вокруг оси X
        Matrix.rotateM(
            mModelMatrix,
            0,
            rotationAngle,    // Угол вращения
            1.0f, 0.0f, 0.0f  // Ось вращения (X)
        )
        
        // Применяем вращение вокруг оси Y
        Matrix.rotateM(
            mModelMatrix,
            0,
            rotationAngle * 0.7f,  // Разная скорость для интересного эффекта
            0.0f, 1.0f, 0.0f       // Ось вращения (Y)
        )
        
        // === КОМБИНАЦИЯ МАТРИЦ: MVP = Projection × View × Model ===
        // Сначала умножаем Projection × View
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)
        // Затем результат × Model
        Matrix.multiplyMM(mMVPMatrix, 0, mMVPMatrix, 0, mModelMatrix, 0)
        
        // Передаем MVP матрицу в шейдер
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        
        // Рендерим треугольники
        drawTriangles()
        
        // Обновляем угол вращения (1 градус за кадр)
        rotationAngle = (rotationAngle + 1.0f) % 360.0f
    }
    
    /**
     * Компилирует vertex и fragment шейдеры, связывает их в программу.
     * @return ID программы или 0 при ошибке
     */
    private fun buildShaderProgram(): Int {
        // Компилируем vertex shader
        val shaderTypeVertex = GLES20.GL_VERTEX_SHADER
        val compiledShaderVertex = compileShader(shaderTypeVertex, vertexShaderCode)
        
        if (compiledShaderVertex == 0) {
            Log.e(TAG, "Ошибка компиляции vertex shader")
            return 0
        }
        
        // Компилируем fragment shader
        val shaderTypeFragment = GLES20.GL_FRAGMENT_SHADER
        val compiledShaderFragment = compileShader(shaderTypeFragment, fragmentShaderCode)
        
        if (compiledShaderFragment == 0) {
            Log.e(TAG, "Ошибка компиляции fragment shader")
            GLES20.glDeleteShader(compiledShaderVertex)
            return 0
        }
        
        // Создаем программу шейдеров
        val programId = GLES20.glCreateProgram()
        
        if (programId == 0) {
            Log.e(TAG, "Ошибка создания программы")
            GLES20.glDeleteShader(compiledShaderVertex)
            GLES20.glDeleteShader(compiledShaderFragment)
            return 0
        }
        
        // Привязываем шейдеры к программе
        GLES20.glAttachShader(programId, compiledShaderVertex)
        GLES20.glAttachShader(programId, compiledShaderFragment)
        
        // Линкуем программу
        GLES20.glLinkProgram(programId)
        
        // Проверяем результат линковки
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Ошибка линковки программы: ${GLES20.glGetProgramInfoLog(programId)}")
            GLES20.glDeleteProgram(programId)
            GLES20.glDeleteShader(compiledShaderVertex)
            GLES20.glDeleteShader(compiledShaderFragment)
            return 0
        }
        
        // Удаляем компилированные шейдеры (они уже в программе)
        GLES20.glDeleteShader(compiledShaderVertex)
        GLES20.glDeleteShader(compiledShaderFragment)
        
        return programId
    }
    
    /**
     * Компилирует отдельный шейдер.
     * @param type Тип шейдера (GL_VERTEX_SHADER или GL_FRAGMENT_SHADER)
     * @param shaderCode Исходный код шейдера
     * @return ID скомпилированного шейдера или 0 при ошибке
     */
    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        
        if (shader == 0) {
            Log.e(TAG, "Ошибка создания шейдера")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Проверяем результат компиляции
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Ошибка компиляции шейдера: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
    
    /**
     * Создает буферы для вершин и цветов куба.
     */
    private fun createBuffers() {
        // Буфер вершин
        val bytesPerFloat = 4
        mTriangleVertices = ByteBuffer.allocateDirect(
            triangleVerticesData.size * bytesPerFloat
        ).apply {
            order(ByteOrder.nativeOrder())
        }.asFloatBuffer()
        
        mTriangleVertices?.put(triangleVerticesData)?.position(0)
        
        // Буфер цветов
        mColors = ByteBuffer.allocateDirect(
            colorData.size * bytesPerFloat
        ).apply {
            order(ByteOrder.nativeOrder())
        }.asFloatBuffer()
        
        mColors?.put(colorData)?.position(0)
        
        Log.d(TAG, "createBuffers - Буферы созданы: ${triangleVerticesData.size} вершин")
    }
    
    /**
     * Рендерит треугольники куба.
     */
    private fun drawTriangles() {
        // Устанавливаем буфер позиций вершин
        mTriangleVertices?.apply {
            position(0)
            GLES20.glEnableVertexAttribArray(mPositionHandle)
            GLES20.glVertexAttribPointer(
                mPositionHandle,
                3,              // По 3 компонента на вершину (x, y, z)
                GLES20.GL_FLOAT,// Тип данных
                false,          // Без нормализации
                3 * 4,          // Шаг между вершинами (3 float × 4 байта)
                this            // Буфер данных
            )
        }
        
        // Устанавливаем буфер цветов
        mColors?.apply {
            position(0)
            GLES20.glEnableVertexAttribArray(mColorHandle)
            GLES20.glVertexAttribPointer(
                mColorHandle,
                4,              // По 4 компонента на цвет (r, g, b, a)
                GLES20.GL_FLOAT,// Тип данных
                false,          // Без нормализации
                4 * 4,          // Шаг между цветами (4 float × 4 байта)
                this            // Буфер данных
            )
        }
        
        // Рендерим треугольники
        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLES,
            0,                  // Начальный индекс
            triangleVerticesData.size / 3  // Количество вершин
        )
        
        // Отключаем атрибуты после рендеринга
        GLES20.glDisableVertexAttribArray(mPositionHandle)
        GLES20.glDisableVertexAttribArray(mColorHandle)
    }
}
