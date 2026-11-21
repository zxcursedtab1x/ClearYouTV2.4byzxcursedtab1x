package app.clearyourtv

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DIM = Color.argb(170, 255, 255, 255)
private const val MB = 1024L * 1024L

/**
 * Центр очистки: два кольца (ОЗУ и диск), «Ускорить» в одно нажатие
 * и список сторонних приложений — самые давние сверху — для чистки кэша или удаления.
 */
class CleanerView(private val act: MainActivity) {
    val view = FrameLayout(act)
    val focus: View

    private val ramRing = Ring(act)
    private val diskRing = Ring(act)
    private val ramTv = act.label(12f, DIM)
    private val diskTv = act.label(12f, DIM)
    private val headTv = act.label(22f, bold = true)
    private val subTv = act.label(13f, DIM)
    private val list = LinearLayout(act)
    private val status = act.label(13f, DIM)

    private var page = 0
    private var busy = false
    private var lastRow = 0

    private class Inst(val app: App, val installed: Long, val apkSize: Long)

    init {
        view.setBackgroundColor(0x88000000.toInt())
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(act.dp(28).toFloat(), Color.argb(235, 22, 34, 54), act.dp(1), Color.argb(70, 255, 255, 255))
            setPadding(act.dp(20), act.dp(20), act.dp(20), act.dp(20))
        }

        val left = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, act.dp(16), 0)
        }
        left.addView(act.label(19f, bold = true).apply { text = "Очистка" })
        left.addView(act.label(12f, DIM).apply {
            text = "ОЗУ и приложения"
            setPadding(0, act.dp(2), 0, act.dp(22))
        })
        val rings = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        rings.addView(meter(ramRing, "ОЗУ", ramTv), LinearLayout.LayoutParams(0, WRAP, 1f))
        rings.addView(meter(diskRing, "Диск", diskTv), LinearLayout.LayoutParams(0, WRAP, 1f))
        left.addView(rings, LinearLayout.LayoutParams(MATCH, WRAP))
        left.addView(View(act), LinearLayout.LayoutParams(MATCH, 0, 1f))
        left.addView(act.label(12f, DIM).apply { text = "← Назад" })

        subTv.setPadding(0, act.dp(2), 0, act.dp(12))
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(act).apply {
            isVerticalScrollBarEnabled = false
            isFocusable = false
            addView(list)
        }
        status.maxLines = 2
        status.setPadding(0, act.dp(10), 0, 0)
        val right = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(act.dp(20).toFloat(), Color.argb(70, 60, 80, 120), act.dp(1), Color.argb(50, 255, 255, 255))
            setPadding(act.dp(22), act.dp(18), act.dp(22), act.dp(14))
            addView(headTv)
            addView(subTv)
            addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(status)
        }

        card.addView(left, LinearLayout.LayoutParams(act.dp(280), MATCH))
        card.addView(right, LinearLayout.LayoutParams(0, MATCH, 1f))
        view.addView(card, FrameLayout.LayoutParams(MATCH, MATCH).apply {
            setMargins(act.dp(90), act.dp(50), act.dp(90), act.dp(50))
        })
        updateMeters(false)
        focus = showMain(0)
    }

    // ───────────────────────── индикаторы ─────────────────────────

    private fun meter(ring: Ring, name: String, value: TextView): View = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(ring, LinearLayout.LayoutParams(act.dp(104), act.dp(104)))
        addView(act.label(15f, bold = true).apply { text = name; setPadding(0, act.dp(10), 0, act.dp(2)) })
        addView(value)
    }

    private fun updateMeters(animate: Boolean) {
        val r = Sys.ram(act)
        val d = Sys.disk()
        if (animate) ramRing.animateTo(r.usedFrac) else ramRing.progress = r.usedFrac
        diskRing.progress = d.usedFrac
        ramTv.text = "${Sys.fmt(r.avail)} свободно"
        diskTv.text = "${Sys.fmt(d.avail)} свободно"
    }

    /** Вызывается из MainActivity: вернулись из настроек или список приложений изменился. */
    fun refresh() {
        updateMeters(false)
        if (page == 1) showApps(lastRow)
    }

    /** Назад: с подстраницы — на главную страницу очистки. */
    fun onBack(): Boolean {
        if (page == 0) return false
        val v = showMain(1)
        v.post { v.requestFocus() }
        return true
    }

    // ───────────────────────── страницы ─────────────────────────

    private fun row(title: String, sub: String, value: String, onClick: () -> Unit): LinearLayout {
        val r = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(act.dp(16), act.dp(12), act.dp(16), act.dp(12))
        }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(act.label(16f).apply { text = title })
        if (sub.isNotEmpty()) {
            col.addView(act.label(12f, DIM).apply { text = sub; setPadding(0, act.dp(3), 0, 0) })
        }
        r.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (value.isNotEmpty()) r.addView(act.label(13f, DIM).apply { text = value })
        r.makeFocusable(12, rest = Color.argb(30, 255, 255, 255), scale = 1f)
        r.setOnClickListener { onClick() }
        return r
    }

    private fun addRow(v: View) {
        list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = act.dp(8) })
    }

    private fun showMain(focusIdx: Int): View {
        page = 0
        headTv.text = "Быстрая уборка"
        subTv.text = "Пара нажатий — и телевизор снова летает"
        status.text = ""
        list.removeAllViews()
        val rows = listOf(
            row("Ускорить телевизор", "Закрыть фоновые приложения и освободить ОЗУ", "") { boost() },
            row("Освободить место", "Давно установленные приложения: кэш и удаление", "") { showApps(0) },
            row("Хранилище системы", "Подробности в настройках Android", "") {
                act.sys(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            }
        )
        rows.forEach { addRow(it) }
        return rows[focusIdx.coerceIn(0, rows.size - 1)]
    }

    private fun boost() {
        if (busy) return
        busy = true
        val before = Sys.ram(act).avail
        Sys.boost(act, act.apps.map { it.pkg })
        status.text = "Закрываю фоновые приложения…"
        view.postDelayed({
            val after = Sys.ram(act)
            updateMeters(true)
            val freed = after.avail - before
            status.text = if (freed > 8 * MB) "Освобождено ${Sys.fmt(freed)} ОЗУ" else "ОЗУ уже в порядке — лишнего в фоне нет"
            busy = false
        }, 900)
    }

    private fun userApps(): List<Inst> {
        val pm = act.packageManager
        return act.apps.mapNotNull { a ->
            runCatching {
                val ai = pm.getApplicationInfo(a.pkg, 0)
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                    null
                } else {
                    Inst(a, pm.getPackageInfo(a.pkg, 0).firstInstallTime, File(ai.sourceDir).length())
                }
            }.getOrNull()
        }.sortedBy { it.installed }
    }

    private fun showApps(focusIdx: Int): View? {
        val items = userApps()
        if (items.isEmpty()) {
            Toast.makeText(act, "Сторонних приложений нет — всё чисто", Toast.LENGTH_SHORT).show()
            val v = showMain(1)
            v.post { v.requestFocus() }
            return v
        }
        page = 1
        headTv.text = "Освободить место"
        subTv.text = "Самые давние — сверху. OK — кэш и данные, удерживайте OK — удалить"
        status.text = "Сторонних приложений: ${items.size}"
        list.removeAllViews()
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
        val views = items.mapIndexed { i, x ->
            row(x.app.label, "Установлено ${fmt.format(Date(x.installed))} · APK ${Sys.fmt(x.apkSize)}", "") {
                lastRow = i
                runCatching {
                    act.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${x.app.pkg}"))
                    )
                }
            }.apply {
                setOnLongClickListener {
                    lastRow = i
                    runCatching { act.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${x.app.pkg}"))) }
                    true
                }
            }
        }
        views.forEach { addRow(it) }
        val target = views[focusIdx.coerceIn(0, views.size - 1)]
        target.post { target.requestFocus() }
        return target
    }
}
