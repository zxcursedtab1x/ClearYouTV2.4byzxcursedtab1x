package app.clearyourtv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

/** «Живой фон»: три мягких световых пятна медленно плывут по сцене. Цикл 90 секунд, ~30 кадров/с. */
class Aurora(ctx: Context) : View(ctx) {
    private var colors = IntArray(3) { Color.argb(0x55, 255, 255, 255) }
    private val shaders = arrayOfNulls<RadialGradient>(3)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var last = 0L
    private var anim: ValueAnimator? = null
    private var durMs = 90_000L

    fun setTint(base: IntArray?) {
        val src = base ?: intArrayOf(0xFFFFFFFF.toInt(), 0xFFB0C4FF.toInt(), 0xFFFFD6A0.toInt())
        colors = IntArray(3) { i ->
            val c = src[(i + 1) % src.size]
            // немного светлее сцены, чтобы пятна читались
            fun up(v: Int) = v + (255 - v) * 40 / 100
            Color.argb(0x66, up(Color.red(c)), up(Color.green(c)), up(Color.blue(c)))
        }
        shaders.fill(null)
        invalidate()
    }

    fun start() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val now = System.nanoTime()
                if (now - last >= 33_000_000L) {
                    last = now
                    t = it.animatedFraction
                    invalidate()
                }
            }
            start()
        }
    }

    fun setSpeed(i: Int) {
        val d = longArrayOf(150_000L, 90_000L, 45_000L)[i.coerceIn(0, 2)]
        if (d == durMs) return
        durMs = d
        if (anim != null) { stop(); start() }
    }

    fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        shaders.fill(null)
    }

    override fun onDraw(c: Canvas) {
        if (width == 0 || height == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val r = maxOf(w, h) * 0.55f
        val twoPi = (2.0 * Math.PI).toFloat()
        for (i in 0 until 3) {
            val sh = shaders[i] ?: RadialGradient(
                0f, 0f, r, colors[i], Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            ).also { shaders[i] = it }
            paint.shader = sh
            val px = twoPi * (i + 1) * t + i * 2.1f
            val py = twoPi * (3 - i) * t + i * 1.3f
            val x = w * (0.5f + 0.38f * sin(px))
            val y = h * (0.45f + 0.30f * cos(py))
            c.save()
            c.translate(x, y)
            c.drawCircle(0f, 0f, r, paint)
            c.restore()
        }
        paint.shader = null
    }
}
