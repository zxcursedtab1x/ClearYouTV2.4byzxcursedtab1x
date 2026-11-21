package app.clearyourtv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun View.dp(v: Int): Int = context.dp(v)

fun roundRect(radiusPx: Float, fill: Int, strokePx: Int = 0, stroke: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusPx
    if (strokePx > 0) setStroke(strokePx, stroke)
}

fun Context.label(
    size: Float, color: Int = Color.WHITE, bold: Boolean = false,
    shadow: Boolean = false, light: Boolean = false
) = TextView(this).apply {
    textSize = size
    setTextColor(color)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    if (light) typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    if (shadow) setShadowLayer(10f, 0f, 2f, Color.argb(110, 0, 0, 0))
    includeFontPadding = false
}

/** Делает вью фокусируемой с пульта: рамка + лёгкое увеличение. */
fun View.makeFocusable(
    radiusDp: Int = 14, rest: Int = Color.TRANSPARENT, focusFill: Int = 0x40FFFFFF,
    scale: Float = 1.08f, onFocus: ((Boolean) -> Unit)? = null
) {
    isFocusable = true
    isClickable = true
    val r = dp(radiusDp).toFloat()
    background = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), roundRect(r, focusFill, dp(2), Color.WHITE))
        addState(intArrayOf(), roundRect(r, rest))
    }
    setOnFocusChangeListener { v, f ->
        val s = if (f) scale else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        onFocus?.invoke(f)
    }
}

/** Зелёный → жёлтый → красный по заполненности (0..1). */
fun healthColor(used: Float): Int = when {
    used < 0.70f -> 0xFF4CD08A.toInt()
    used < 0.85f -> 0xFFF2B84B.toInt()
    else -> 0xFFF0605A.toInt()
}

/** Кольцевой индикатор: прогресс 0..1, внутри — подпись (по умолчанию проценты). */
class Ring(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var caption: String? = null
        set(v) { field = v; invalidate() }
    var textScale = 0.27f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(55, 255, 255, 255)
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        val aw = width - paddingLeft - paddingRight
        val ah = height - paddingTop - paddingBottom
        val size = minOf(aw, ah).toFloat()
        if (size <= 0f) return
        val sw = size * 0.11f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        val m = sw / 2f + 1f
        val l = paddingLeft + (aw - size) / 2f
        val t = paddingTop + (ah - size) / 2f
        box.set(l + m, t + m, l + size - m, t + size - m)
        c.drawArc(box, 0f, 360f, false, track)
        arc.color = healthColor(progress)
        if (progress > 0f) c.drawArc(box, -90f, 360f * progress, false, arc)
        txt.textSize = size * textScale
        val text = caption ?: "${(progress * 100).toInt()}%"
        c.drawText(text, l + size / 2f, t + size / 2f - (txt.descent() + txt.ascent()) / 2f, txt)
    }
}

/** Плавно «доезжает» до нового значения. */
fun Ring.animateTo(v: Float) {
    ValueAnimator.ofFloat(progress, v).apply {
        duration = 600
        addUpdateListener { progress = it.animatedValue as Float }
        start()
    }
}

/** Тонкая полоса прогресса (громкость). */
class Bar(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        c.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, track)
        if (progress > 0f) c.drawRoundRect(0f, 0f, maxOf(w * progress, h), h, h / 2f, h / 2f, fill)
    }
}

/** Блик на карточке: мягкий верхний свет (gloss) и диагональный «проезд» света (sweep) при фокусе. */
class Shine(ctx: Context) : View(ctx) {
    var gloss = 0f
        set(v) { field = v; invalidate() }
    var sweep = 0f
        set(v) { field = v; invalidate() }
    var radiusDp = 12

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sweeping = sweep > 0f && sweep < 1f
        if (w <= 0f || (gloss <= 0f && !sweeping)) return
        val r = dp(radiusDp).toFloat()
        path.reset()
        box.set(0f, 0f, w, h)
        path.addRoundRect(box, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        if (gloss > 0f) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, h * 0.65f,
                Color.argb((80 * gloss).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w, h, paint)
        }
        if (sweeping) {
            val bw = w * 0.5f
            val x = -bw + (w + bw) * sweep
            c.save()
            c.rotate(-18f, w / 2f, h / 2f)
            paint.shader = LinearGradient(
                x, 0f, x + bw, 0f,
                intArrayOf(Color.argb(0, 255, 255, 255), Color.argb(110, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(-w, -h, w * 2f, h * 2f, paint)
            c.restore()
        }
        paint.shader = null
        c.restore()
    }
}
