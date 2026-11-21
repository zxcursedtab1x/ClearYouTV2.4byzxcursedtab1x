package app.clearyourtv

import android.graphics.drawable.GradientDrawable

class Scene(val name: String, val colors: IntArray) {
    fun drawable() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
}

private fun c(h: Long) = h.toInt()

val SCENES = listOf(
    Scene("Тропический рассвет", intArrayOf(c(0xFF2E6FB7), c(0xFF7FB2E0), c(0xFFF2B27A), c(0xFF3C6B3A), c(0xFF1B3A22))),
    Scene("Лунное озеро", intArrayOf(c(0xFF050A1F), c(0xFF0B1F4A), c(0xFF2A4C80), c(0xFF0A1428))),
    Scene("Дюны", intArrayOf(c(0xFF3A2A5C), c(0xFFB5527A), c(0xFFF2A65A), c(0xFF8C4A2F))),
    Scene("Северное сияние", intArrayOf(c(0xFF02111F), c(0xFF0B5D63), c(0xFF28B58C), c(0xFF0B1F3A))),
    Scene("Золотой берег", intArrayOf(c(0xFF3A6EA5), c(0xFFF4C27A), c(0xFFE8895A), c(0xFF1F3A5F)))
)
