// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.steve_linux.photograin.render

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

/** Small helpers over raw GLES 3.0 calls. All functions need a current GL context. */
internal object GlUtil {
    fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw IllegalStateException("Program link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("Shader compile failed: $log")
        }
        return shader
    }

    fun uploadBitmap(bitmap: Bitmap, existing: Int = 0): Int {
        val tex = if (existing != 0) existing else genTexture()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        setSampling(mipmap = false)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        return tex
    }

    fun genTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        return ids[0]
    }

    fun setSampling(mipmap: Boolean) {
        val min = if (mipmap) GLES30.GL_LINEAR_MIPMAP_LINEAR else GLES30.GL_LINEAR
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, min)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    fun deleteTexture(tex: Int) {
        if (tex != 0) GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
    }

    fun maxTextureSize(): Int {
        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0)
        return v[0]
    }

    fun hasExtension(name: String): Boolean {
        val count = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_NUM_EXTENSIONS, count, 0)
        for (i in 0 until count[0]) {
            if (GLES30.glGetStringi(GLES30.GL_EXTENSIONS, i) == name) return true
        }
        return false
    }
}

/** A texture with a framebuffer attached, used as an intermediate render target. */
internal class RenderTarget(
    val width: Int,
    val height: Int,
    val mipmap: Boolean,
    halfFloat: Boolean,
) {
    val texture: Int = GlUtil.genTexture()
    val framebuffer: Int

    init {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GlUtil.setSampling(mipmap)
        val levels = if (mipmap) mipLevels(width, height) else 1
        val format = if (halfFloat) GLES30.GL_RGBA16F else GLES30.GL_RGBA8
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels, format, width, height)
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0,
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer incomplete: $status" }
    }

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer)
        GLES30.glViewport(0, 0, width, height)
    }

    fun matches(w: Int, h: Int) = w == width && h == height

    fun release() {
        GLES30.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        GlUtil.deleteTexture(texture)
    }

    private companion object {
        fun mipLevels(w: Int, h: Int): Int {
            var levels = 1
            var size = maxOf(w, h)
            while (size > 1) {
                size = size shr 1
                levels++
            }
            return levels
        }
    }
}
