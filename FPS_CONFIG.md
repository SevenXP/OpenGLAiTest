# Настройка лимита отрисовки в 60 FPS

## Как использовать CubeRenderer с лимитом 60 FPS

Лимит частоты кадров (FPS) настраивается через `GLSurfaceView`, а не внутри класса рендерера.

### Настройка GLSurfaceView для 60 FPS

```kotlin
// В вашей Activity или Fragment
val glSurfaceView = GLSurfaceView(this).apply {
    // Установить версию OpenGL ES 2.0
    setEGLContextClientVersion(2)
    
    // Настроить рендерер
    setRenderer(CubeRenderer(context))
    
    // ВАЖНО: Настроить лимит FPS на 60 кадров в секунду
    // По умолчанию Android использует максимальную частоту обновления экрана
    // Для ограничения до 60 FPS используйте:
    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
}

// Дополнительно: можно настроить частоту обновления через EGLConfig
val eglConfigChooser = object : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, configAttrs: Array<Int>): EGLConfig? {
        // Выберите конфигурацию с поддержкой 60 FPS
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        egl.eglChooseConfig(
            egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY),
            configAttrs,
            configs,
            1,
            numConfigs
        )
        return if (numConfigs[0] > 0) configs[0] else null
    }
    
    override fun getConfigAttributes(): IntArray {
        return intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_NONE
        )
    }
}

glSurfaceView.setEGLConfigChooser(eglConfigChooser)
setContentView(glSurfaceView)
```

### Альтернативный подход: Использование RENDERMODE_WHEN_DIRTY

Если вам нужно более точное управление FPS:

```kotlin
val glSurfaceView = GLSurfaceView(this).apply {
    setEGLContextClientVersion(2)
    setRenderer(CubeRenderer(context))
    
    // Режим: рендерить только по запросу
    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
}

// Затем в вашем коде control the frame rate:
val handler = Handler(Looper.getMainLooper())
val runnable = object : Runnable {
    override fun run() {
        glSurfaceView.requestRender()
        handler.postDelayed(this, 16) // ~60 FPS (1000ms / 60 ≈ 16.67ms)
    }
}
handler.post(runnable)
```

## Как это работает

- **RENDERMODE_CONTINUOUSLY**: Рендерит так часто, как возможно (по умолчанию)
- **RENDERMODE_WHEN_DIRTY**: Рендерит только по запросу `requestRender()`
- Для точного контроля FPS используйте таймер с интервалом 16мс (~60 FPS)

## Оптимизация для 60 FPS

CubeRenderer уже оптимизирован:
- Использует прямые буферы (`ByteBuffer.allocateDirect()`) для минимальной задержки
- Переиспользует матрицы вместо создания новых каждый кадр
- Минимизирует количество вызовов OpenGL
- Эффективно управляет ресурсами (буферами, шейдерами)

## Проверка FPS

Для отладки можно добавить счетчик FPS:

```kotlin
class CubeRenderer(private val context: Context) : Renderer {
    private var frameCount = 0
    private var lastTime = System.currentTimeMillis()
    
    override fun onDrawFrame(unused: GL10?) {
        // ... существующий код ...
        
        // Счетчик FPS
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTime >= 1000) {
            Log.d("CubeRenderer", "FPS: $frameCount")
            frameCount = 0
            lastTime = currentTime
        }
    }
}
```