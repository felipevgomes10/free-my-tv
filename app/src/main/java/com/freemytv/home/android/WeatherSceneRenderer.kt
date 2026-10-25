package com.freemytv.home.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.util.Log
import com.freemytv.home.weather.WeatherAtmosphere
import com.freemytv.home.weather.WeatherSceneTransition
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

/**
 * Native GLES adaptation of Weather Animations 1.1.1's palettes, cloud lobes/layers, celestial
 * bodies, fog and precipitation. Copyright (c) 2026 Marc, MIT; assets/weather/LICENSE.txt.
 * Sprites are generated once per scene, uploaded and recycled; no bitmap/blur work per frame.
 */
internal class WeatherSceneRenderer(initial: WeatherAtmosphere) : GLSurfaceView.Renderer {
    private var wanted = initial
    private var transition = WeatherSceneTransition(initial)
    private var current: Scene? = null
    private var incoming: Scene? = null
    private lateinit var sky: Program
    private lateinit var sprite: Program
    private lateinit var points: Program
    private lateinit var lines: Program
    private var ready = false
    private var previous = 0L
    private var time = 0f
    private val quad = buffer(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f))
    private val particles = buffer(FloatArray(256 * 4))
    private val bolt = buffer(FloatArray(64 * 2))
    private var boltCount = 0
    private val mixedTop = FloatArray(3)
    private val mixedBottom = FloatArray(3)
    private val random = Random(9271)
    private val seeds = Array(256) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()) }

    fun scene(value: WeatherAtmosphere) {
        wanted = value
        transition.target(value)
        if (WeatherAnimationProbe.ENABLED) Log.i("WeatherAnimation", "scene=${value.animationCondition}:${value.animationTime}")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        ready = false
        try {
            sky = Program(QUAD_VERTEX, SKY_FRAGMENT)
            sprite = Program(QUAD_VERTEX, SPRITE_FRAGMENT)
            points = Program(POINT_VERTEX, POINT_FRAGMENT)
            lines = Program(LINE_VERTEX, LINE_FRAGMENT)
            glDisable(GL_DEPTH_TEST)
            glEnable(GL_BLEND)
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA)
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
            transition = WeatherSceneTransition(wanted)
            current = Scene(wanted)
            incoming = null
            previous = 0L
            time = 0f
            ready = true
            if (WeatherAnimationProbe.ENABLED) {
                WeatherAnimationProbe.reset()
                Log.i("WeatherAnimation", "renderer_ready")
                Log.i("WeatherAnimation", "scene=${wanted.animationCondition}:${wanted.animationTime}")
            }
        } catch (error: RuntimeException) {
            Log.w("WeatherScene", "Graphics unavailable", error)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        if (WeatherAnimationProbe.ENABLED) Log.i("WeatherAnimation", "output=${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (previous == 0L) 0f else ((now - previous) / 1e9f).coerceIn(0f, 0.1f)
        previous = now
        glClearColor(0.06f, 0.09f, 0.13f, 1f)
        glClear(GL_COLOR_BUFFER_BIT)
        if (!ready) return
        try {
            time += dt
            transition.advance(dt)
            if (current?.weather != transition.current) {
                current?.release()
                current = incoming?.takeIf { it.weather == transition.current } ?: Scene(transition.current)
                incoming = null
            }
            val target = transition.next
            if (target != null && incoming?.weather != target) {
                incoming?.release()
                incoming = Scene(target)
            }
            val from = current ?: return
            val to = incoming
            from.tick(dt)
            to?.tick(dt)
            val t = if (to == null) 0f else transition.fraction
            for (i in 0..2) {
                mixedTop[i] = from.top[i] * (1 - t) + (to?.top?.get(i) ?: from.top[i]) * t
                mixedBottom[i] = from.bottom[i] * (1 - t) + (to?.bottom?.get(i) ?: from.bottom[i]) * t
            }
            sky.useQuad()
            glUniform3fv(sky.at("uTop"), 1, mixedTop, 0)
            glUniform3fv(sky.at("uBottom"), 1, mixedBottom, 0)
            glUniform1f(sky.at("uFlash"), from.flash * (1 - t) + (to?.flash ?: 0f) * t)
            glUniform4f(sky.at("uRect"), 0f, 0f, W, H)
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
            drawScene(from, 1 - t)
            if (to != null) drawScene(to, t)
            if (WeatherAnimationProbe.ENABLED) WeatherAnimationProbe.frame(now, glGetError())
        } catch (error: RuntimeException) {
            ready = false
            Log.w("WeatherScene", "Graphics unavailable", error)
        }
    }

    private fun drawScene(scene: Scene, alpha: Float) {
        if (alpha <= 0f) return
        sprite.useQuad()
        glUniform1i(sprite.at("uTexture"), 0)
        glUniform1f(sprite.at("uNight"), if (scene.night) 1f else 0f)
        glUniform1f(sprite.at("uDarkBase"), if (scene.weather in DARK_CLOUDS) 0.35f else if (scene.night) 0.28f else 0.16f)
        val bodyAlpha = when (scene.weather) {
            WeatherAtmosphere.ClearDay, WeatherAtmosphere.ClearNight -> 1f
            WeatherAtmosphere.PartlyCloudyDay, WeatherAtmosphere.PartlyCloudyNight -> 0.45f
            else -> 0f
        }
        if (bodyAlpha > 0f) {
            val radius = H * if (scene.night) 0.06f else 0.08f
            val extent = radius * if (scene.night) 2.6f else 4f
            drawSprite(if (scene.night) 2 else 1, W * 0.75f - extent, H * 0.18f - extent, extent * 2, extent * 2, alpha * bodyAlpha, WHITE)
        }
        for (cloud in scene.clouds) {
            var x = cloud.x + cloud.speed * scene.age
            val span = W + cloud.width * 2
            x = (x + cloud.width) % span - cloud.width
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, cloud.texture)
            drawSprite(0, x - cloud.cx, cloud.y - cloud.cy, cloud.width, cloud.height, alpha * cloud.alpha, scene.cloudColor)
        }
        if (scene.weather == WeatherAtmosphere.Fog) {
            for (i in 0..4) {
                val radius = W * 0.48f
                val x = W * ((i * 0.24f + scene.age * 0.009f) % 1.4f - 0.2f)
                val y = H * (0.42f + i * 0.085f) + sin(scene.age * 0.15f + i) * 5f
                drawSprite(3, x - radius, y - radius, radius * 2, radius * 2, alpha * 0.96f, FOG_COLOR)
            }
        }
        drawParticles(scene, alpha)
        if (scene.flash > 0f && scene.weather == WeatherAtmosphere.Thunderstorm) drawBolt(scene, alpha)
    }

    private fun drawSprite(kind: Int, x: Float, y: Float, width: Float, height: Float, alpha: Float, color: FloatArray) {
        glUniform1f(sprite.at("uKind"), kind.toFloat())
        glUniform1f(sprite.at("uOpacity"), alpha)
        glUniform3fv(sprite.at("uTint"), 1, color, 0)
        glUniform4f(sprite.at("uRect"), x, y, width, height)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawParticles(scene: Scene, alpha: Float) {
        val mode = when (scene.weather) {
            WeatherAtmosphere.ClearNight -> 0
            WeatherAtmosphere.Snow -> 1
            WeatherAtmosphere.Rain -> 2
            WeatherAtmosphere.Drizzle -> 3
            WeatherAtmosphere.Thunderstorm -> 4
            else -> return
        }
        val count = when (mode) { 0 -> 78; 1 -> 128; 2 -> 64; 3 -> 40; else -> 96 }
        particles.clear()
        for (i in 0 until count) {
            val seed = seeds[i]
            val depth = 0.5f + seed[2] * 0.5f
            var x = seed[0] * W
            var y = seed[1] * H
            val size: Float
            val opacity: Float
            if (mode == 0) {
                size = 2f + seed[2] * 3f
                opacity = (0.5f + 0.5f * sin(scene.age * 2.5f + seed[3] * 12)) * alpha
            } else {
                val speed = when (mode) { 1 -> 20f + seed[3] * 28; 3 -> 420f + seed[3] * 160; 4 -> 900f + seed[3] * 300; else -> 650f + seed[3] * 200 }
                y = (y + scene.age * speed * depth) % (H + 30) - 15
                val drift = if (mode == 1) sin(scene.age * 0.6f + seed[3] * 12) * 24 else -scene.age * (if (mode == 4) 35 else if (mode == 3) 12 else 20)
                x = ((x + drift) % W + W) % W
                size = when (mode) { 1 -> (3f + seed[3] * 6) * depth; 3 -> (6f + seed[3] * 6) * depth; 4 -> 20f + seed[3] * 14; else -> (14f + seed[3] * 10) * depth }
                opacity = alpha * depth * if (mode == 3) 0.35f else 0.7f
            }
            particles.put(x).put(y).put(size).put(opacity)
        }
        particles.flip()
        glUseProgram(points.id)
        glUniform2f(points.at("uViewport"), W, H)
        glUniform1f(points.at("uMode"), mode.toFloat())
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 16, particles)
        glDrawArrays(GL_POINTS, 0, count)
    }

    private fun drawBolt(scene: Scene, alpha: Float) {
        glUseProgram(lines.id)
        glUniform2f(lines.at("uViewport"), W, H)
        glEnableVertexAttribArray(0)
        bolt.position(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, bolt)
        glLineWidth(1f)
        glUniform4f(lines.at("uColor"), 0.7f, 0.83f, 1f, scene.flash * alpha * 0.8f)
        glDrawArrays(GL_LINES, 0, boltCount)
    }

    private inner class Scene(val weather: WeatherAtmosphere) {
        val night = weather.animationTime == "night"
        val top: FloatArray
        val bottom: FloatArray
        val clouds = mutableListOf<Cloud>()
        val cloudColor: FloatArray
        var age = 0f
        var flash = 0f
        private var nextFlash = 2f

        init {
            val palette = when (weather) {
                WeatherAtmosphere.ClearDay -> 0x1a6b9e to 0x87ceeb
                WeatherAtmosphere.ClearNight -> 0x0a0a2e to 0x1a1a4e
                WeatherAtmosphere.PartlyCloudyDay -> 0x6b7a8d to 0xb0bec5
                WeatherAtmosphere.PartlyCloudyNight -> 0x1c2331 to 0x2d3a4a
                WeatherAtmosphere.Cloudy -> 0x5f6b76 to 0x9ea9b1
                WeatherAtmosphere.Fog -> 0xc8cdd2 to 0xe8eaed
                WeatherAtmosphere.Drizzle -> 0x718394 to 0xb7c6cf
                WeatherAtmosphere.Rain -> 0x3d5166 to 0x607d8b
                WeatherAtmosphere.Snow -> 0xb0bec5 to 0xe8eef4
                WeatherAtmosphere.Thunderstorm -> 0x1a1a2e to 0x16213e
            }
            top = rgb(palette.first); bottom = rgb(palette.second)
            cloudColor = rgb(when {
                weather == WeatherAtmosphere.Thunderstorm -> 0x2a2a3a
                weather == WeatherAtmosphere.Cloudy -> 0xb2bac2
                weather == WeatherAtmosphere.Rain || weather == WeatherAtmosphere.Drizzle -> 0x6a7a8a
                night -> 0x3a4a6a
                else -> 0xd0d8e0
            })
            if (weather != WeatherAtmosphere.ClearDay && weather != WeatherAtmosphere.ClearNight && weather != WeatherAtmosphere.Fog) makeClouds()
        }

        private fun makeClouds() {
            val storm = weather == WeatherAtmosphere.Thunderstorm
            val overcast = weather == WeatherAtmosphere.Cloudy
            val light = weather.animationIntensity == "light"
            val counts = if (storm) intArrayOf(2, 2, 3) else if (overcast) intArrayOf(7, 8, 9) else intArrayOf(4, 5, 6)
            val baseAlpha = when (weather) { WeatherAtmosphere.Thunderstorm -> 0.9f; WeatherAtmosphere.Cloudy -> 0.94f; WeatherAtmosphere.Rain -> 0.75f; WeatherAtmosphere.Drizzle -> 0.58f; WeatherAtmosphere.PartlyCloudyDay, WeatherAtmosphere.PartlyCloudyNight -> 0.7f; else -> 0.5f }
            for (layer in 0..2) {
                val count = (counts[layer] * if (light) 0.55f else 1f).roundToInt().coerceAtLeast(1)
                val layerScale = 1f + layer * if (storm) 0.4f else if (overcast) 0.25f else 0.35f
                for (i in 0 until count) {
                    val w = W * (if (storm) 0.18f + random.nextFloat() * 0.14f else if (overcast) 0.32f + random.nextFloat() * 0.18f else 0.22f + random.nextFloat() * 0.18f) * layerScale
                    val h = H * (if (storm) 0.30f + random.nextFloat() * 0.20f else 0.12f + random.nextFloat() * 0.08f) * layerScale
                    val x = i.toFloat() / count * W * 1.4f - W * 0.2f
                    val y = if (storm) H * (layer * 0.07f + random.nextFloat() * 0.05f) - h * 0.15f else H * (0.04f + layer * if (overcast) 0.13f else 0.18f) + random.nextFloat() * H * 0.08f
                    clouds += cloud(w, h, x, y, floatArrayOf(0.006f, 0.022f, 0.06f)[layer] * W, baseAlpha * (1 - layer * 0.12f) * if (light) 0.75f else 1f, overcast)
                }
            }
        }

        fun tick(dt: Float) {
            age += dt
            if (weather != WeatherAtmosphere.Thunderstorm) return
            nextFlash -= dt
            flash = (flash - dt * 3.5f).coerceAtLeast(0f)
            if (nextFlash <= 0f) {
                flash = 1f
                nextFlash = 6f + random.nextFloat() * 6f
                bolt.clear()
                var x = W * (0.2f + random.nextFloat() * 0.6f)
                var y = H * 0.08f
                for (i in 1..8) {
                    val nx = (x + (random.nextFloat() - 0.5f) * W * 0.13f).coerceIn(W * 0.05f, W * 0.95f)
                    val ny = H * (0.08f + 0.52f * i / 8)
                    bolt.put(x).put(y).put(nx).put(ny)
                    if (i == 3 || i == 5) bolt.put(nx).put(ny).put(nx + W * 0.08f).put(ny + H * 0.12f)
                    x = nx; y = ny
                }
                bolt.flip(); boltCount = bolt.limit() / 2
            }
        }

        fun release() { clouds.forEach { glDeleteTextures(1, intArrayOf(it.texture), 0) }; clouds.clear() }
    }

    private data class Cloud(val texture: Int, val width: Float, val height: Float, val cx: Float, val cy: Float, val x: Float, val y: Float, val speed: Float, val alpha: Float)

    /** Port of cloud lobes and two-pass blurred silhouette from upstream clouds.ts. */
    private fun cloud(width: Float, height: Float, x: Float, y: Float, speed: Float, alpha: Float, flat: Boolean): Cloud {
        val rx = width / 2; val ry = height / 2
        val count = (2 + rx / ry * 1.8f).roundToInt().coerceIn(4, 9)
        val spacing = if (flat) 2.4f else 1.8f
        val lobes = ArrayList<FloatArray>(11)
        for (i in 0 until count) {
            val t = i.toFloat() / (count - 1)
            val r = (0.62f + (1 - abs(t - 0.5f) * 2) * if (flat) 0.18f else 0.42f) + random.nextFloat() * 0.3f - 0.15f
            lobes += floatArrayOf(((t - 0.5f) * spacing + (random.nextFloat() * 2 - 1) * spacing / (count - 1) * 0.15f) * rx, ((1 - r) + random.nextFloat() * 0.08f + random.nextFloat() * 0.2f - 0.1f) * ry, r.coerceAtMost(1.1f) * ry)
        }
        if (!flat && count >= 5) repeat(2) {
            val r = 0.85f + random.nextFloat() * 0.2f
            lobes += floatArrayOf((random.nextFloat() * 0.4f - 0.2f) * spacing * 0.8f * rx, ((1 - r) + 0.05f + random.nextFloat() * 0.1f) * ry, r * ry)
        }
        val blur = max(2f, height * 0.06f)
        val pad = blur * 4 + 2
        val left = lobes.minOf { it[0] - it[2] }; val top = lobes.minOf { it[1] - it[2] }
        val bw = ceil(lobes.maxOf { it[0] + it[2] } - left + pad * 2).toInt()
        val bh = ceil(lobes.maxOf { it[1] + it[2] } - top + pad * 2).toInt()
        // Cloud masks are low-frequency silhouettes: half-size textures retain their softness
        // while bounding both current/incoming overcast texture sets to a few MiB.
        val body = Bitmap.createBitmap((bw + 1) / 2, (bh + 1) / 2, Bitmap.Config.ARGB_8888)
        val bodyCanvas = Canvas(body).apply { scale(0.5f, 0.5f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        lobes.forEach { bodyCanvas.drawCircle(it[0] - left + pad, it[1] - top + pad, it[2], paint) }
        // A bounded three-pass alpha blur runs only at scene creation. Upload its bytes directly
        // using GLES2's single-channel format; no Skia extractAlpha/mask filter or RGBA copy.
        val maskWidth = body.width; val maskHeight = body.height
        val mask = blurAlpha(body, (blur * 0.5f).roundToInt().coerceAtLeast(1))
        body.recycle()
        val texture = IntArray(1)
        glGenTextures(1, texture, 0)
        glBindTexture(GL_TEXTURE_2D, texture[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, maskWidth, maskHeight, 0, GL_ALPHA, GL_UNSIGNED_BYTE, mask)
        return Cloud(texture[0], bw.toFloat(), bh.toFloat(), pad - left, pad - top, x, y, speed, alpha)
    }

    private fun blurAlpha(bitmap: Bitmap, radius: Int): ByteBuffer {
        val width = bitmap.width; val height = bitmap.height
        val alpha = IntArray(width * height)
        bitmap.getPixels(alpha, 0, width, 0, 0, width, height)
        for (i in alpha.indices) alpha[i] = alpha[i] ushr 24
        val horizontal = IntArray(alpha.size)
        val diameter = radius * 2 + 1
        repeat(3) {
            for (y in 0 until height) {
                val row = y * width
                var sum = 0
                for (k in -radius..radius) sum += alpha[row + k.coerceIn(0, width - 1)]
                for (x in 0 until width) {
                    horizontal[row + x] = sum / diameter
                    sum += alpha[row + (x + radius + 1).coerceAtMost(width - 1)] - alpha[row + (x - radius).coerceAtLeast(0)]
                }
            }
            for (x in 0 until width) {
                var sum = 0
                for (k in -radius..radius) sum += horizontal[k.coerceIn(0, height - 1) * width + x]
                for (y in 0 until height) {
                    alpha[y * width + x] = sum / diameter
                    sum += horizontal[(y + radius + 1).coerceAtMost(height - 1) * width + x] - horizontal[(y - radius).coerceAtLeast(0) * width + x]
                }
            }
        }
        return ByteBuffer.wrap(ByteArray(alpha.size) { alpha[it].toByte() })
    }

    private inner class Program(vertex: String, fragment: String) {
        val id: Int
        private val uniforms = HashMap<String, Int>()
        init {
            val vs = shader(GL_VERTEX_SHADER, vertex); val fs = shader(GL_FRAGMENT_SHADER, fragment)
            id = glCreateProgram()
            glAttachShader(id, vs); glAttachShader(id, fs)
            glBindAttribLocation(id, 0, "aData")
            glLinkProgram(id)
            val status = IntArray(1); glGetProgramiv(id, GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { glGetProgramInfoLog(id) }
            glDeleteShader(vs); glDeleteShader(fs)
        }
        fun at(name: String): Int = uniforms.getOrPut(name) { glGetUniformLocation(id, name) }
        fun useQuad() {
            glUseProgram(id)
            glUniform2f(at("uViewport"), W, H)
            glEnableVertexAttribArray(0)
            quad.position(0)
            glVertexAttribPointer(0, 4, GL_FLOAT, false, 16, quad)
        }
    }

    companion object {
        private const val W = 640f
        private const val H = 360f
        private val WHITE = floatArrayOf(1f, 1f, 1f)
        private val FOG_COLOR = rgb(0xcdd2d8)
        private val DARK_CLOUDS = setOf(WeatherAtmosphere.Cloudy, WeatherAtmosphere.Rain, WeatherAtmosphere.Drizzle, WeatherAtmosphere.Thunderstorm)
        private fun rgb(value: Int) = floatArrayOf((value shr 16 and 255) / 255f, (value shr 8 and 255) / 255f, (value and 255) / 255f)
        private fun buffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
        private fun shader(type: Int, source: String): Int {
            val id = glCreateShader(type); glShaderSource(id, source); glCompileShader(id)
            val status = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { glGetShaderInfoLog(id) }; return id
        }
        private const val QUAD_VERTEX = """
            attribute vec4 aData; uniform vec2 uViewport; uniform vec4 uRect; varying vec2 vUv;
            void main(){ vec2 p=uRect.xy+aData.xy*uRect.zw; gl_Position=vec4(p.x/uViewport.x*2.-1.,1.-p.y/uViewport.y*2.,0.,1.);vUv=aData.zw; }
        """
        private const val SKY_FRAGMENT = """
            precision mediump float; varying vec2 vUv; uniform vec3 uTop,uBottom;uniform float uFlash;
            void main(){vec3 c=mix(uTop,uBottom,vUv.y);c=mix(c,vec3(.78,.86,1.),uFlash*.25);gl_FragColor=vec4(c,1.);}
        """
        private const val SPRITE_FRAGMENT = """
            precision mediump float; varying vec2 vUv;uniform sampler2D uTexture;uniform float uKind,uOpacity,uNight,uDarkBase;uniform vec3 uTint;
            void main(){vec2 p=vUv*2.-1.;float d=length(p);float a;vec3 c;
              if(uKind<.5){a=texture2D(uTexture,vUv).a;float light=(1.-vUv.y)*mix(.22,.10,uNight);c=mix(uTint,vec3(1.,.957,.84),light);c*=1.-uDarkBase*smoothstep(.35,1.,vUv.y);}
              else if(uKind<1.5){float disc=1.-smoothstep(.245,.255,d);float halo=.45*exp(-d*d*5.)*(1.-smoothstep(.8,1.,d));a=max(disc,halo);c=mix(vec3(1.,.86,.59),vec3(1.,.99,.91),disc*(1.-d));}
              else if(uKind<2.5){float disc=1.-smoothstep(.379,.390,d);a=max(disc,.22*(1.-smoothstep(.38,1.,d)));c=mix(vec3(.86,.9,.96),vec3(1.,.97,.84),disc);vec2 q=p/.385;float crater=(1.-smoothstep(.15,.19,length(q-vec2(-.3,-.2))))+(1.-smoothstep(.10,.14,length(q-vec2(.25,.1))))+(1.-smoothstep(.07,.11,length(q-vec2(.05,.35))));c*=1.-crater*.20;}
              else{a=pow(max(0.,1.-d),2.)*.5;c=uTint;}
              a*=uOpacity;gl_FragColor=vec4(c*a,a);}
        """
        private const val POINT_VERTEX = """
            attribute vec4 aData;uniform vec2 uViewport;varying float vAlpha;
            void main(){gl_Position=vec4(aData.x/uViewport.x*2.-1.,1.-aData.y/uViewport.y*2.,0.,1.);gl_PointSize=aData.z;vAlpha=aData.w;}
        """
        private const val POINT_FRAGMENT = """
            precision mediump float;varying float vAlpha;uniform float uMode;
            void main(){vec2 p=gl_PointCoord-.5;float a;vec3 c;
              if(uMode<1.5){a=1.-smoothstep(.10,.5,length(p));c=uMode<.5?vec3(1.,.98,.88):vec3(.94,.97,1.);}
              else{float width=uMode<3.5&&uMode>2.5?.045:.035;a=(1.-smoothstep(width,width+.035,abs(p.x+p.y*.035)))*(1.-smoothstep(.35,.5,abs(p.y)));c=vec3(.71,.80,.88);}
              a*=vAlpha;gl_FragColor=vec4(c*a,a);}
        """
        private const val LINE_VERTEX = """
            attribute vec2 aData;uniform vec2 uViewport;void main(){gl_Position=vec4(aData.x/uViewport.x*2.-1.,1.-aData.y/uViewport.y*2.,0.,1.);}
        """
        private const val LINE_FRAGMENT = """
            precision mediump float;uniform vec4 uColor;void main(){gl_FragColor=vec4(uColor.rgb*uColor.a,uColor.a);}
        """
    }
}
