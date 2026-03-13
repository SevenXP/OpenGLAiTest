package com.example.myapplication.opengl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CubeRenderer(private val context: Context) : GLSurfaceView.Renderer {
    override fun onDrawFrame(gl: GL10?) {

    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val glVersion = "OpenGL ES ${gl!!.glGetString(GL10.GL_VERSION)}"
        Log.d("Roma", "GL Version: $glVersion")
    }
}