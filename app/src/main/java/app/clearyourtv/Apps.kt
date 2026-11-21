package app.clearyourtv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

class App(
    val label: String,
    val pkg: String,
    val intent: Intent,
    val icon: Drawable,
    val banner: Drawable?,
    val accent: Int = 0
)

fun dominantColor(d: Drawable?): Int {
    if (d == null) return 0
    return runCatching {
        val n = 12
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val old = d.copyBounds()
        d.setBounds(0, 0, n, n)
        d.draw(Canvas(bmp))
        d.setBounds(old.left, old.top, old.right, old.bottom)

        var r = 0.0
        var g = 0.0
        var b = 0.0
        var wsum = 0.0
        val hsv = FloatArray(3)

        for (y in 0 until n) for (x in 0 until n) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val w = hsv[1] * hsv[2] + 0.04
            r += Color.red(p) * w
            g += Color.green(p) * w
            b += Color.blue(p) * w
            wsum += w
        }

        if (wsum <= 0.0) return@runCatching 0

        Color.colorToHSV(
            Color.rgb(
                (r / wsum).toInt(),
                (g / wsum).toInt(),
                (b / wsum).toInt()
            ),
            hsv
        )
        hsv[1] = hsv[1].coerceIn(0.5f, 0.95f)
        hsv[2] = hsv[2].coerceIn(0.75f, 1f)
        Color.HSVToColor(hsv)
    }.getOrDefault(0)
}

fun loadApps(ctx: Context): List<App> {
    val pm = ctx.packageManager

    return runCatching {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != ctx.packageName }
            .mapNotNull { info ->
                val pkg = info.packageName

                val intent = runCatching {
                    pm.getLeanbackLaunchIntentForPackage(pkg)
                        ?: pm.getLaunchIntentForPackage(pkg)
                }.getOrNull() ?: return@mapNotNull null

                val icon = runCatching {
                    info.loadIcon(pm)
                }.getOrNull() ?: return@mapNotNull null

                val label = runCatching {
                    info.loadLabel(pm).toString()
                }.getOrDefault(pkg)

                val banner = runCatching {
                    info.loadBanner(pm)
                }.getOrNull()

                App(
                    label,
                    pkg,
                    intent,
                    icon,
                    banner,
                    dominantColor(banner ?: icon)
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }.getOrDefault(emptyList())
}
