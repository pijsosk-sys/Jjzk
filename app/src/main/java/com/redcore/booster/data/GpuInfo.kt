package com.redcore.booster.data

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20

data class GlInfo(val renderer: String?, val vendor: String?, val version: String?, val extensionCount: Int?, val maxTextureSize: Int?)

/** Creates a tiny off-screen OpenGL ES context purely to ask the driver who it is. */
object GpuInfo {
    @Volatile private var cached: GlInfo? = null

    fun query(): GlInfo {
        cached?.let { return it }
        val result = try { queryInternal() } catch (_: Throwable) { GlInfo(null, null, null, null, null) }
        cached = result
        return result
    }

    private fun queryInternal(): GlInfo {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return GlInfo(null, null, null, null, null)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return GlInfo(null, null, null, null, null)
        try {
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) return GlInfo(null, null, null, null, null)
            val ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            val surf = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, surf, surf, ctx)
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)
            val version = GLES20.glGetString(GLES20.GL_VERSION)
            val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS)?.split(' ')?.count { it.isNotBlank() }
            val max = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, it, 0) }[0]
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surf)
            EGL14.eglDestroyContext(display, ctx)
            return GlInfo(renderer, vendor, version, ext, max.takeIf { it > 0 })
        } finally {
            EGL14.eglTerminate(display)
        }
    }
}
