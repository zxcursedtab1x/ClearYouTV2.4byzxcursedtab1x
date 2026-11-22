package app.clearyourtv

import android.graphics.Color
import android.graphics.Typeface
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import kotlin.concurrent.thread

/** Экран настроек ClearYourTV: слева разделы, справа содержимое. */
class SettingsView(private val act: MainActivity, start: Int) {
    private val p = act.prefs
    val view = FrameLayout(act)
    val focus: View

    private val list = LinearLayout(act)
    private val headTv = act.label(22f, bold = true)
    private val subTv = act.label(13f, Color.argb(170, 255, 255, 255))
    private val items = mutableListOf<TextView>()
    private var current = -1

    private val names = listOf(
        "Вид и сцены", "Часы", "Погода", "Приложения", "Панель и Dock",
        "Защита", "Лаунчер и система", "Устройство и Smart Cleaner", "О приложении", "Разрешения"
    )
    private val subs = listOf(
        "Пейзажи, живой фон и эффекты", "Время, дата и приветствие", "Прогноз на главном экране",
        "Что показывать в Dock (OK — добавить или убрать)", "Размер, прозрачность, недавние",
        "Реклама, трекеры и DNS", "Главный экран, затемнение и сброс",
        "Профиль ТВ, диагностика и безопасная оптимизация", "О ClearYourTV",
        "Доступы и системные подтверждения"
    )
    private var resetArmed = false

    init {
        view.setBackgroundColor(0x88000000.toInt())
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(act.dp(28).toFloat(), Color.argb(235, 22, 34, 54), act.dp(1), Color.argb(70, 255, 255, 255))
            setPadding(act.dp(20), act.dp(20), act.dp(20), act.dp(20))
        }

        val side = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, act.dp(16), 0)
        }
        side.addView(ImageView(act).apply {
            setImageResource(R.drawable.clearyourtv_logo_512)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Логотип ClearYourTV"
        }, LinearLayout.LayoutParams(act.dp(76), act.dp(76)).apply {
            bottomMargin = act.dp(8)
        })
        side.addView(ImageView(act).apply {
            setImageResource(R.drawable.clearyourtv_logo_512)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Логотип ClearYourTV"
        }, LinearLayout.LayoutParams(act.dp(76), act.dp(76)).apply {
            bottomMargin = act.dp(8)
        })
        side.addView(act.label(19f, bold = true).apply { text = "ClearYourTV" })
        side.addView(act.label(12f, Color.argb(170, 255, 255, 255)).apply {
            text = "Настройки"
            setPadding(0, act.dp(2), 0, act.dp(8))
        })
        names.forEachIndexed { i, n ->
            val t = act.label(15f).apply {
                text = n
                setPadding(act.dp(14), act.dp(8), act.dp(14), act.dp(8))
            }
            t.makeFocusable(12, scale = 1f, onFocus = { f -> if (f) show(i) })
            items.add(t)
            side.addView(t, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = act.dp(2) })
        }
        side.addView(View(act), LinearLayout.LayoutParams(MATCH, 0, 1f))
        side.addView(act.label(12f, Color.argb(170, 255, 255, 255)).apply { text = "← Назад" })

        subTv.setPadding(0, act.dp(2), 0, act.dp(12))
        list.orientation = LinearLayout.VERTICAL
        val scroll = ScrollView(act).apply {
            isVerticalScrollBarEnabled = false
            isFocusable = false
            addView(list)
        }
        val right = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(act.dp(20).toFloat(), Color.argb(70, 60, 80, 120), act.dp(1), Color.argb(50, 255, 255, 255))
            setPadding(act.dp(22), act.dp(18), act.dp(22), act.dp(14))
            addView(headTv)
            addView(subTv)
            addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }

        card.addView(side, LinearLayout.LayoutParams(act.dp(230), MATCH))
        card.addView(right, LinearLayout.LayoutParams(0, MATCH, 1f))
        view.addView(card, FrameLayout.LayoutParams(MATCH, MATCH).apply {
            setMargins(act.dp(90), act.dp(50), act.dp(90), act.dp(50))
        })
        show(start)
        focus = items[start]
    }

    private fun row(title: String, value: String, swatch: Scene? = null, onClick: () -> Unit): View {
        val r = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(act.dp(16), act.dp(12), act.dp(16), act.dp(12))
        }
        if (swatch != null) {
            val sw = View(act)
            sw.background = swatch.drawable().apply { cornerRadius = act.dp(6).toFloat() }
            r.addView(sw, LinearLayout.LayoutParams(act.dp(56), act.dp(32)).apply { rightMargin = act.dp(14) })
        }
        r.addView(act.label(16f).apply { text = title }, LinearLayout.LayoutParams(0, WRAP, 1f))
        r.addView(act.label(13f, Color.argb(190, 255, 255, 255)).apply { text = value })
        r.makeFocusable(12, rest = Color.argb(30, 255, 255, 255), scale = 1f)
        r.setOnClickListener { onClick() }
        return r
    }

    /** Перерисовать текущий раздел (после системных диалогов и фоновых задач). */
    fun refresh() {
        show(if (current < 0) 0 else current, 0)
    }

    private fun updateLists() {
        Toast.makeText(act, "Скачиваю списки… это может занять до минуты", Toast.LENGTH_LONG).show()
        thread {
            val n = Blocklist.update(act)
            act.runOnUiThread {
                val msg = if (n > 0) "Готово: в списке $n доменов" else "Не удалось скачать списки — проверьте интернет"
                Toast.makeText(act, msg, Toast.LENGTH_LONG).show()
                if (current == 5) show(5, 3)
            }
        }
    }

    private fun custom() {
        val f = File(act.getExternalFilesDir(null), "wallpaper.jpg")
        if (f.exists()) {
            p.scene = SCENES.size
            act.applyPrefs()
            show(0, SCENES.size)
        } else {
            Toast.makeText(
                act,
                "Положите wallpaper.jpg в Android/data/app.clearyourtv/files/ (adb push или файловый менеджер)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun show(i: Int, focusRow: Int = -1) {
        if (i == current && focusRow < 0) return
        current = i
        if (i != 6) resetArmed = false
        headTv.text = names[i]
        subTv.text = subs[i]
        items.forEachIndexed { j, t ->
            t.setTextColor(if (j == i) Color.WHITE else Color.argb(160, 255, 255, 255))
            t.typeface = if (j == i) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        list.removeAllViews()
        val rows = mutableListOf<View>()

        fun add(v: View) {
            list.addView(v, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = act.dp(8) })
            rows.add(v)
        }
        fun tog(t: String, cur: Boolean, set: (Boolean) -> Unit) {
            val idx = rows.size
            add(row(t, if (cur) "Вкл" else "Выкл") { set(!cur); act.applyPrefs(); show(i, idx) })
        }
        fun cyc(t: String, labels: List<String>, cur: Int, set: (Int) -> Unit) {
            val idx = rows.size
            add(row(t, labels[cur.coerceIn(0, labels.size - 1)]) {
                set((cur + 1) % labels.size); act.applyPrefs(); show(i, idx)
            })
        }

        fun info(text: String) {
            add(act.label(13f, Color.argb(190, 255, 255, 255)).apply {
                this.text = text
                setPadding(act.dp(16), act.dp(10), act.dp(16), act.dp(10))
                background = roundRect(act.dp(12).toFloat(), Color.argb(28, 255, 255, 255))
            })
        }

        when (i) {
            0 -> {
                SCENES.forEachIndexed { k, s ->
                    add(row(s.name, if (p.scene == k) "Выбрано" else "", s) {
                        p.scene = k; act.applyPrefs(); show(0, k)
                    })
                }
                add(row("Выбрать свою картинку", if (p.scene == SCENES.size) "Выбрано" else "") { custom() })
                tog("Живой фон", p.liveBg) { p.liveBg = it }
                cyc("Скорость живого фона", listOf("Медленно", "Обычно", "Быстро"), p.auroraSpeed) { p.auroraSpeed = it }
                tog("Подсветка под цвет приложения", p.ambientOn) { p.ambientOn = it }
                tog("Блик на карточках", p.shine) { p.shine = it }
                tog("Анимация при запуске", p.introOn) { p.introOn = it }
            }
            1 -> {
                tog("24-часовой формат", p.h24) { p.h24 = it }
                tog("Показывать приветствие", p.showGreeting) { p.showGreeting = it }
                tog("Показывать дату", p.showDate) { p.showDate = it }
                cyc("Размер часов", listOf("Мелкие", "Средние", "Крупные"), p.clockSize) { p.clockSize = it }
            }
            2 -> {
                tog("Показывать погоду", p.showWeather) { p.showWeather = it }
                cyc("Город", Weather.cities.map { it.name }, p.city) { p.city = it }
            }
            3 -> {
                val inDock = act.dockPackages()
                act.apps.forEachIndexed { k, a ->
                    add(row(a.label, if (a.pkg in inDock) "В Dock" else "") {
                        act.toggleDock(a.pkg); show(3, k)
                    })
                }
            }
            4 -> {
                tog("Недавние приложения впереди", p.showRecent) { p.showRecent = it }
                cyc("Размер карточек и иконок", listOf("Маленький", "Средний", "Большой"), p.iconSize) { p.iconSize = it }
                cyc("Прозрачность окон и Dock", listOf("Прозрачная", "Средняя", "Плотная"), p.glass) { p.glass = it }
                tog("Кольцо очистки в Dock", p.dockClean) { p.dockClean = it }
                info("В полке «Приложения» удержание OK добавляет приложение в Dock или убирает его. Dock вмещает до 7 приложений.")
            }
            5 -> {
                add(row("Блокировка рекламы и трекеров", if (p.adblock) "Вкл" else "Выкл") {
                    act.setAdblock(!p.adblock); show(5, 0)
                })
                add(row("Статус защиты", when {
                    p.adblock && AdBlockVpnService.running -> "Работает"
                    p.adblock -> "Запускается…"
                    else -> "Выключено"
                }) { show(5, 1) })
                cyc("DNS-сервер", AdBlockVpnService.UPSTREAM_NAMES, p.dnsUpstream) {
                    p.dnsUpstream = it; act.restartShield()
                }
                add(row("Обновить списки блокировки", Blocklist.summary(act)) { updateLists() })
                add(row("Вернуть встроенный список", "") {
                    Blocklist.reset(act); act.restartShield(); show(5, 4)
                })
                val sysDns = when {
                    !PrivateDns.supported() -> "Android 9+"
                    !PrivateDns.canWrite(act) -> "Нужен ADB"
                    PrivateDns.isOn(act) -> "Вкл"
                    else -> "Выкл"
                }
                add(row("Системный DNS-фильтр (AdGuard)", sysDns) {
                    if (PrivateDns.supported() && PrivateDns.canWrite(act)) PrivateDns.set(act, !PrivateDns.isOn(act))
                    show(5, 5)
                })
                if (PrivateDns.supported() && !PrivateDns.canWrite(act)) {
                    info("Чтобы включить системный DNS-фильтр, один раз выдайте право по ADB:\nadb shell pm grant app.clearyourtv android.permission.WRITE_SECURE_SETTINGS")
                }
                info("Блокируются домены рекламы и трекеров во всех приложениях ТВ. Остальной трафик идёт мимо фильтра, скорость не страдает.")
                info("Честно о пределах: реклама внутри YouTube и похожих сервисов приходит с тех же адресов, что и сам видеопоток, — DNS-фильтр её убрать не может. Приложения со своим зашифрованным DNS тоже обходят фильтр.")
            }
            6 -> {
                add(row("Лаунчер по умолчанию", if (act.isDefaultHome()) "Да" else "Нет") { act.requestDefaultHome() })
                cyc("Затемнение при бездействии", listOf("Выкл", "5 минут", "15 минут", "30 минут"), p.idleMin) { p.idleMin = it }
                add(row("Системные настройки ТВ", "") { act.sys(Settings.ACTION_SETTINGS) })
                add(row("Сбросить все настройки", if (resetArmed) "Нажмите ещё раз" else "") {
                    if (!resetArmed) {
                        resetArmed = true
                        show(6, 3)
                    } else {
                        resetArmed = false
                        AdBlockVpnService.stop(act)
                        p.resetAll()
                        act.applyPrefs()
                        Toast.makeText(act, "Настройки сброшены", Toast.LENGTH_SHORT).show()
                        show(6, 3)
                    }
                })
                info("Если ТВ не предлагает выбрать главный экран, выполните по ADB:\nadb shell cmd package set-home-activity app.clearyourtv/.MainActivity")
                info("Вернуть стандартный лаунчер: Настройки ТВ → Приложения → По умолчанию → Главный экран.")
            }
            7 -> {
                add(row("Профиль устройства", p.deviceProfile.ifBlank { "Не выбран" }) {
                    DeviceSetup.showWizard(act, p) { show(7, 0) }
                })
                add(row("Запустить диагностику и Smart Cleaner", "Анализ") { DeviceSetup.showDiagnostics(act) })
                info("Выбери платформу вручную. ClearYourTV не определяет её автоматически.")
                info("Smart Cleaner работает безопасно: показывает характеристики и рекомендации. Не удаляет системные приложения, службы, драйверы или прошивку.")
                info("Доступность функций зависит от модели телевизора, версии Android и ограничений производителя.")
            }
            8 -> listOf(
                "ClearYourTV · 2.3-dev",
                "Погода: Open-Meteo · CC BY 4.0",
                "В самом лаунчере нет рекламы и аналитики",
                "Списки блокировки: AdGuard, StevenBlack (при обновлении)"
            ).forEach { item ->
                add(act.label(15f).apply {
                    text = item
                    setPadding(act.dp(16), act.dp(14), act.dp(16), act.dp(14))
                    background = roundRect(act.dp(12).toFloat(), Color.argb(30, 255, 255, 255))
                })
            }
            9 -> {
                add(row("Главный экран (HOME)", if (act.isDefaultHome()) "Выбран" else "Не выбран") {
                    act.requestDefaultHome()
                    show(9, 0)
                })

                val canInstall = Build.VERSION.SDK_INT < 26 ||
                    act.packageManager.canRequestPackageInstalls()
                add(row("Установка APK", if (canInstall) "Разрешено" else "Нужно разрешить") {
                    if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
                        try {
                            act.startActivityForResult(Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${act.packageName}")
                            ), 75)
                        } catch (e: Exception) {
                            Toast.makeText(act, "Не удалось открыть настройки установки APK", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(act, "Установка APK уже разрешена", Toast.LENGTH_SHORT).show()
                    }
                })

                val vpnAllowed = try { VpnService.prepare(act) == null } catch (e: Exception) { false }
                add(row("VPN для блокировки рекламы", if (vpnAllowed) "Подтверждено" else "Нужно подтвердить") {
                    act.setAdblock(true)
                    show(9, 2)
                })

                val dnsStatus = when {
                    !PrivateDns.supported() -> "Нужен Android 9+"
                    !PrivateDns.canWrite(act) -> "Нужно ADB-разрешение"
                    PrivateDns.isOn(act) -> "Включён"
                    else -> "Выключен"
                }
                add(row("Системный DNS (AdGuard)", dnsStatus) {
                    if (PrivateDns.supported() && PrivateDns.canWrite(act)) {
                        PrivateDns.set(act, !PrivateDns.isOn(act))
                        show(9, 3)
                    }
                })

                if (PrivateDns.supported() && !PrivateDns.canWrite(act)) {
                    info("Для системного DNS один раз выдайте разрешение по ADB на своём ТВ:\nadb shell pm grant app.clearyourtv android.permission.WRITE_SECURE_SETTINGS")
                }
                info("Интернет: разрешение из манифеста, отдельное окно не требуется.")
                info("Список приложений: доступ объявлен в манифесте; отдельного системного запроса нет.")
                info("Выбор APK и обоев открывается через системный файловый диалог. Широкий доступ ко всем файлам не запрашивается.")
                info("HOME, установка APK и VPN подтверждаются отдельными системными экранами. Поддержка зависит от прошивки ТВ.")
            }
            else -> {}
        }
        if (focusRow >= 0) rows.getOrNull(focusRow)?.requestFocus()
    }
}
