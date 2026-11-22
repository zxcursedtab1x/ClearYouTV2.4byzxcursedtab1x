package app.clearyourtv

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.content.*
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {

    lateinit var prefs: Prefs
    var apps: List<App> = emptyList()

    private lateinit var root: FrameLayout
    private lateinit var home: FrameLayout
    private lateinit var bg: ImageView
    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private lateinit var dock: LinearLayout
    private lateinit var shelfScroll: HorizontalScrollView
    private lateinit var shelfBox: LinearLayout
    private lateinit var shelfRow: LinearLayout
    private lateinit var tip: TextView
    private var tipOwner: View? = null
    private lateinit var shield: TextView
    private lateinit var homePill: TextView
    private var settingsView: SettingsView? = null
    private lateinit var aurora: Aurora
    private lateinit var ambient: View
    private lateinit var hero: LinearLayout
    private lateinit var ambientDrawable: GradientDrawable
    private var ambientColor = 0
    private var ambientX = 0.5f
    private var ambientAnim: ValueAnimator? = null
    private var intro = false
    private var dockRing: Ring? = null
    private var shelfKey = ""
    private var launchedFromShelf = false
    private var cleaner: CleanerView? = null
    var backHandler: (() -> Boolean)? = null

    private var weatherText: String? = null
    private var weatherCity = -1
    private var pendingApkUri: Uri? = null
    private var overlay: View? = null
    private var opener: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() { updateTexts(); handler.postDelayed(this, 15_000) }
    }
    private val weatherTick = object : Runnable {
        override fun run() {
            if (prefs.showWeather) refreshWeather()
            handler.postDelayed(this, 1_800_000)
        }
    }
    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { reloadApps() }
    }

    // ───────────────────────── жизненный цикл ─────────────────────────

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        buildHome()
        setContentView(root)
        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        applyPrefs()
        reloadApps()
        if (prefs.adblock && (try { VpnService.prepare(this) } catch (e: Exception) { null }) == null) {
            AdBlockVpnService.start(this)
        }
        if (!prefs.setupCompleted) {
            root.post { DeviceSetup.showWizard(this, prefs) { updateTexts() } }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        handler.removeCallbacks(weatherTick)
        handler.postDelayed(weatherTick, 1_800_000)
        if (prefs.showWeather) refreshWeather()
        cleaner?.refresh()
        if (prefs.liveBg) aurora.start()
        buildShelf()
        if (overlay == null && currentFocus == null) {
            val r = if (launchedFromShelf) shelfRow.getChildAt(0) else null
            if (r != null) r.requestFocus() else focusDock()
        }
        launchedFromShelf = false
        resetIdle()
        updateTexts()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(weatherTick)
        handler.removeCallbacks(idleRun)
        aurora.stop()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        closeOverlay()
        focusDock()
    }

    override fun onDestroy() {
        unregisterReceiver(pkgReceiver)
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (backHandler?.invoke() == true) return
        closeOverlay()
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        resetIdle()
        return super.dispatchKeyEvent(ev)
    }

    override fun onKeyDown(code: Int, e: KeyEvent): Boolean {
        when (code) {
            KeyEvent.KEYCODE_MENU -> { if (overlay == null) openControl(); return true }
            KeyEvent.KEYCODE_SETTINGS -> { if (overlay == null) openSettings(0); return true }
        }
        return super.onKeyDown(code, e)
    }

    // ───────────────────────── главный экран ─────────────────────────

    private fun buildHome() {
        root = FrameLayout(this).apply { clipChildren = false; setBackgroundColor(Color.BLACK) }
        home = FrameLayout(this).apply { clipChildren = false }
        root.addView(home, MATCH, MATCH)

        bg = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        home.addView(bg, MATCH, MATCH)

        aurora = Aurora(this)
        home.addView(aurora, MATCH, MATCH)

        // Ambient: цветное сияние снизу следует за выбранной карточкой
        ambientDrawable = GradientDrawable().apply {
            setGradientType(GradientDrawable.RADIAL_GRADIENT)
            setColors(intArrayOf(0, 0))
            setGradientRadius(dp(640).toFloat())
            setGradientCenter(0.5f, 1.05f)
        }
        ambient = View(this).apply { background = ambientDrawable }
        home.addView(ambient, MATCH, MATCH)

        home.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x44000000, 0x00000000, 0x00000000, 0xCC000000.toInt())
            )
        }, MATCH, MATCH)

        // «Верхняя полка» в стиле tvOS: крупное приветствие слева, мелкие часы в углу
        greeting = label(40f, shadow = true, light = true)
        clock = label(26f, shadow = true, light = true)
        date = label(20f, Color.argb(230, 255, 255, 255), shadow = true).apply { setPadding(0, dp(8), 0, 0) }
        weather = label(17f, Color.argb(200, 255, 255, 255), shadow = true).apply { setPadding(0, dp(4), 0, 0) }
        shield = label(15f, Color.argb(235, 130, 230, 165), shadow = true).apply {
            setPadding(0, dp(6), 0, 0)
            visibility = View.GONE
        }
        hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(greeting); addView(date); addView(weather); addView(shield)
        }
        home.addView(hero, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START)
            .apply { topMargin = dp(96); leftMargin = dp(80) })

        homePill = label(15f, bold = true).apply {
            text = "Сделать главным"
            setPadding(dp(20), dp(10), dp(20), dp(10))
            makeFocusable(20, rest = Color.argb(235, 10, 132, 255), scale = 1.06f)
            setOnClickListener { requestDefaultHome() }
            visibility = View.GONE
        }
        val pill = label(15f).apply {
            text = "Управление"
            setPadding(dp(20), dp(10), dp(20), dp(10))
            makeFocusable(20, rest = Color.argb(110, 28, 40, 58), scale = 1.06f)
            setOnClickListener { openControl() }
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(homePill, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(12) })
            addView(pill)
            addView(clock, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(24) })
        }
        home.addView(topBar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END)
            .apply { topMargin = dp(32); rightMargin = dp(80) })

        // Полка баннеров: все приложения, недавние впереди
        shelfRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        shelfScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            setPadding(dp(72), dp(16), dp(72), dp(8))
            addView(shelfRow, FrameLayout.LayoutParams(WRAP, WRAP))
        }
        shelfBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(label(14f, Color.argb(200, 255, 255, 255), shadow = true).apply {
                text = "Приложения"
                setPadding(dp(80), 0, 0, 0)
            })
            addView(shelfScroll, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        home.addView(shelfBox, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM)
            .apply { bottomMargin = dp(140) })

        // Dock в стиле iPhone: закруглённая стеклянная капсула снизу по центру
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        home.addView(dock, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(24) })

        // Подпись над выбранной иконкой Dock
        tip = label(14f, bold = true).apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = roundRect(dp(10).toFloat(), Color.argb(225, 36, 40, 54), dp(1), Color.argb(60, 255, 255, 255))
            alpha = 0f
        }
        home.addView(tip, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START))
    }

    fun applyPrefs() {
        applyScene()
        buildDock()
        buildShelf()
        updateTexts()
        if (prefs.showWeather && (weatherText == null || weatherCity != prefs.city)) refreshWeather()
    }

    private fun applyScene() {
        val idx = prefs.scene
        aurora.setTint(if (idx in SCENES.indices) SCENES[idx].colors else null)
        aurora.visibility = if (prefs.liveBg) View.VISIBLE else View.GONE
        aurora.setSpeed(prefs.auroraSpeed)
        if (prefs.liveBg) aurora.start() else aurora.stop()
        if (idx >= SCENES.size) {
            val f = File(getExternalFilesDir(null), "wallpaper.jpg")
            thread {
                val bmp = decode(f)
                runOnUiThread {
                    if (bmp != null) bg.setImageBitmap(bmp) else bg.setImageDrawable(SCENES[0].drawable())
                }
            }
        } else {
            bg.setImageDrawable(SCENES[idx.coerceAtLeast(0)].drawable())
        }
    }

    private fun decode(f: File): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 1920) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    private fun updateTexts() {
        val c = Calendar.getInstance()
        val ru = Locale.forLanguageTag("ru")
        greeting.text = when (c.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        greeting.visibility = if (prefs.showGreeting) View.VISIBLE else View.GONE
        clock.text = SimpleDateFormat(if (prefs.h24) "HH:mm" else "h:mm a", ru).format(c.time)
        date.text = SimpleDateFormat("EEEE, d MMMM", ru).format(c.time).replaceFirstChar { it.uppercase() }
        date.visibility = if (prefs.showDate) View.VISIBLE else View.GONE
        weather.text = weatherText ?: "Погода пока недоступна"
        weather.visibility = if (prefs.showWeather) View.VISIBLE else View.GONE
        dockRing?.progress = Sys.ram(this).usedFrac
        clock.textSize = floatArrayOf(22f, 26f, 32f)[prefs.clockSize.coerceIn(0, 2)]
        homePill.visibility = if (isDefaultHome()) View.GONE else View.VISIBLE
        if (prefs.adblock && AdBlockVpnService.running) {
            val n = prefs.blockedTotal + AdBlockVpnService.pending()
            shield.text = "Защита включена · заблокировано " +
                String.format(java.util.Locale.forLanguageTag("ru"), "%,d", n)
            shield.visibility = View.VISIBLE
        } else {
            shield.visibility = View.GONE
        }
    }

    private fun refreshWeather() {
        val idx = prefs.city.coerceIn(0, Weather.cities.size - 1)
        weatherCity = idx
        thread {
            val r = Weather.fetch(Weather.cities[idx])
            runOnUiThread {
                if (weatherCity == idx) { weatherText = r; updateTexts() }
            }
        }
    }

    private fun reloadApps() {
        thread {
            val list = loadApps(this)
            runOnUiThread {
                apps = list
                buildDock()
                buildShelf()
                cleaner?.refresh()
                if (!intro && apps.isNotEmpty()) { intro = true; playIntro() }
                if (currentFocus == null && overlay == null) focusDock()
            }
        }
    }

    // ───────────────────────── Dock ─────────────────────────

    private fun effectiveDock(): List<App> {
        val saved = prefs.dock
        if (saved != null) return saved.mapNotNull { p -> apps.firstOrNull { it.pkg == p && it.intent != null } }
        val preferred = listOf(
            "com.google.android.youtube", "com.netflix", "ru.kinopoisk", "ru.more", "ru.ivi",
            "com.wink", "ru.rutube", "tv.okko", "com.amazon"
        )
        val first = preferred.mapNotNull { p -> apps.firstOrNull { it.pkg.startsWith(p) && it.intent != null } }
        return (first + apps.filter { it.intent != null && it !in first }).take(6)
    }

    fun dockPackages(): List<String> = effectiveDock().map { it.pkg }

    fun toggleDock(pkg: String): Boolean {
        val cur = dockPackages().toMutableList()
        val added = !cur.remove(pkg)
        if (added) cur.add(pkg)
        prefs.dock = cur
        applyPrefs()
        return added
    }

    private fun glassBg(radiusDp: Int): Drawable {
        val a = intArrayOf(102, 153, 204)[prefs.glass.coerceIn(0, 2)]
        return roundRect(dp(radiusDp).toFloat(), Color.argb(a, 28, 40, 58), dp(1), Color.argb(70, 255, 255, 255))
    }

    private fun roundedImage(radiusDp: Int = 12) = ImageView(this).apply {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(radiusDp).toFloat())
            }
        }
        clipToOutline = true
    }

    /** Плавное появление: приветствие, Dock-капсула и карточки каскадом. Один раз при старте. */
    private fun playIntro() {
        if (!prefs.introOn) return
        val ease = DecelerateInterpolator(1.6f)
        hero.alpha = 0f
        hero.translationY = dp(-14).toFloat()
        hero.animate().alpha(1f).translationY(0f).setDuration(600).setInterpolator(ease).start()
        dock.alpha = 0f
        dock.translationY = dp(60).toFloat()
        dock.animate().alpha(1f).translationY(0f).setStartDelay(100).setDuration(600).setInterpolator(ease).start()
        val n = minOf(shelfRow.childCount, 10)
        for (i in 0 until n) {
            val v = shelfRow.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(36).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(200L + i * 50L).setDuration(520).setInterpolator(ease).start()
        }
    }

    /** Сияние под цвет выбранного приложения; color == 0 — плавно погасить. */
    private fun setAmbient(color0: Int, item: View) {
        val color = if (prefs.ambientOn) color0 else 0
        val target = if (color == 0) ambientColor and 0x00FFFFFF
        else (0x99 shl 24) or (color and 0x00FFFFFF)
        val loc = IntArray(2)
        item.getLocationInWindow(loc)
        val toX = ((loc[0] + item.width * item.scaleX / 2f) / home.width.coerceAtLeast(1)).coerceIn(0.1f, 0.9f)
        val fromC = ambientColor
        val fromX = ambientX
        val ev = ArgbEvaluator()
        ambientAnim?.cancel()
        ambientAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380
            addUpdateListener {
                val f = it.animatedFraction
                ambientColor = ev.evaluate(f, fromC, target) as Int
                ambientX = fromX + (toX - fromX) * f
                ambientDrawable.setColors(intArrayOf(ambientColor, ambientColor and 0x00FFFFFF))
                ambientDrawable.setGradientCenter(ambientX, 1.05f)
            }
            start()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dockIconSize() = intArrayOf(56, 64, 72)[prefs.iconSize.coerceIn(0, 2)]

    private fun cardW() = intArrayOf(128, 152, 176)[prefs.iconSize.coerceIn(0, 2)]

    private fun dockBg(): Drawable {
        val a = intArrayOf(50, 80, 120)[prefs.glass.coerceIn(0, 2)]
        return roundRect(dp(34).toFloat(), Color.argb(a, 255, 255, 255), dp(1), Color.argb(90, 255, 255, 255))
    }

    private fun tileBg(radiusDp: Int): Drawable =
        roundRect(dp(radiusDp).toFloat(), Color.argb(150, 24, 34, 52))

    private fun appIcon(a: App, size: Int): ImageView = roundedImage((size * 0.23f).toInt()).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(size / 14)
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(Color.argb(170, 24, 34, 52))
        setImageDrawable(a.icon)
    }

    private fun buildDock() {
        dock.removeAllViews()
        dock.background = dockBg()
        val s = dockIconSize()
        val r = (s * 0.23f).toInt()

        for (a in effectiveDock()) {
            dock.addView(dockIcon(s, a.label, appIcon(a, s), a.accent, { launch(a) }, { dockToggle(a, true) }))
        }

        val divider = View(this).apply { setBackgroundColor(Color.argb(90, 255, 255, 255)) }
        dock.addView(divider, LinearLayout.LayoutParams(dp(2), dp(s * 6 / 10)).apply { setMargins(dp(6), 0, dp(6), 0) })

        if (prefs.dockClean) {
            val ring = Ring(this).apply {
                val pad = dp(s / 6)
                setPadding(pad, pad, pad, pad)
                background = tileBg(r)
                progress = Sys.ram(this@MainActivity).usedFrac
            }
            dockRing = ring
            dock.addView(dockIcon(s, "Очистка и ускорение", ring, 0, { openCleaner() }, null))
        } else {
            dockRing = null
        }

        val gear = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setColorFilter(Color.argb(230, 255, 255, 255))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(s / 4)
            setPadding(pad, pad, pad, pad)
            background = tileBg(r)
        }
        dock.addView(dockIcon(s, "Настройки", gear, 0, { openSettings(0) }, null))

        val more = label(20f, bold = true).apply {
            text = "•••"
            gravity = Gravity.CENTER
            background = tileBg(r)
        }
        dock.addView(dockIcon(s, "Все приложения", more, 0, { openAllApps() }, null))

        val apkIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_add)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = tileBg(r)
        }
        dock.addView(dockIcon(s, "Установить APK", apkIcon, 0, { pickApk() }, null))

        // Полка стоит ровно над капсулой Dock
        val lp = shelfBox.layoutParams as FrameLayout.LayoutParams
        lp.bottomMargin = dp(24 + s + 24 + 28)
        shelfBox.layoutParams = lp
    }

    private fun dockToggle(a: App, refocus: Boolean) {
        if (a.intent == null) {
            toast("Это приложение нельзя добавить в Dock")
            return
        }
        val inDock = a.pkg in dockPackages()
        if (!inDock && dockPackages().size >= 7) {
            toast("Dock заполнен: максимум 7 приложений")
            return
        }
        val added = toggleDock(a.pkg)
        toast(if (added) "${a.label}: добавлено в Dock" else "${a.label}: убрано из Dock")
        if (refocus) focusDock()
    }

    /** Иконка Dock в стиле iOS: при фокусе вырастает, приподнимается, над ней появляется подпись. */
    private fun dockIcon(
        size: Int, title: String, content: View, accent: Int, click: () -> Unit, long: (() -> Unit)?
    ): View {
        val radius = (size * 0.23f).toInt()
        if (accent != 0 && Build.VERSION.SDK_INT >= 28) {
            content.outlineSpotShadowColor = accent
            content.outlineAmbientShadowColor = accent
        }
        val shine = Shine(this).apply { radiusDp = radius }
        val rim = View(this).apply {
            background = roundRect(dp(radius).toFloat(), Color.TRANSPARENT, dp(2), Color.WHITE)
            alpha = 0f
        }
        val item = FrameLayout(this).apply {
            clipChildren = false
            isFocusable = true
            isClickable = true
            if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        }
        item.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        item.addView(shine, FrameLayout.LayoutParams(MATCH, MATCH))
        item.addView(rim, FrameLayout.LayoutParams(MATCH, MATCH))
        item.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply { setMargins(dp(7), 0, dp(7), 0) }

        item.setOnFocusChangeListener { _, f ->
            val sc = if (f) 1.32f else 1f
            item.animate().scaleX(sc).scaleY(sc).translationY(if (f) dp(-10).toFloat() else 0f)
                .setDuration(150).start()
            content.animate().translationZ(if (f) dp(10).toFloat() else 0f).setDuration(150).start()
            rim.animate().alpha(if (f) 1f else 0f).setDuration(150).start()
            animShine(shine, f)
            if (f) {
                setAmbient(accent, item)
                showTip(item, title)
            } else {
                hideTip(item)
            }
        }
        item.setOnClickListener { click() }
        if (long != null) item.setOnLongClickListener { long(); true }
        return item
    }

    private fun showTip(item: View, text: String) {
        tipOwner = item
        tip.text = text
        tip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val l = IntArray(2)
        item.getLocationInWindow(l)
        val h = IntArray(2)
        home.getLocationInWindow(h)
        val cx = l[0] - h[0] + item.width * item.scaleX / 2f
        val topUnscaled = l[1] - h[1] + item.height * (item.scaleY - 1f) / 2f
        val maxX = (home.width - tip.measuredWidth - dp(8)).toFloat().coerceAtLeast(dp(8).toFloat())
        tip.x = (cx - tip.measuredWidth / 2f).coerceIn(dp(8).toFloat(), maxX)
        tip.y = topUnscaled - item.height * 0.16f - dp(10) - tip.measuredHeight
        tip.animate().alpha(1f).setDuration(120).start()
    }

    private fun hideTip(item: View) {
        if (tipOwner === item) {
            tipOwner = null
            tip.animate().alpha(0f).setDuration(100).start()
        }
    }

    /** Блик: верхний свет + диагональный «проезд» света. */
    private fun animShine(shine: Shine, f: Boolean) {
        if (!prefs.shine) return
        ValueAnimator.ofFloat(shine.gloss, if (f) 1f else 0f).apply {
            duration = 160
            addUpdateListener { shine.gloss = it.animatedValue as Float }
            start()
        }
        if (f) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 750
                startDelay = 60
                interpolator = DecelerateInterpolator()
                addUpdateListener { shine.sweep = it.animatedValue as Float }
                start()
            }
        } else {
            shine.sweep = 0f
        }
    }

    /** Карточка полки в стиле tvOS: растёт, «приподнимается», рамка, подпись проявляется снизу. */
    private fun shelfItem(
        w: Int, h: Int, title: String, content: View, click: () -> Unit, long: (() -> Unit)?,
        accent: Int = 0
    ): View {
        if (accent != 0 && Build.VERSION.SDK_INT >= 28) {
            content.outlineSpotShadowColor = accent
            content.outlineAmbientShadowColor = accent
        }
        val shine = Shine(this)
        val holder = FrameLayout(this).apply { clipChildren = false }
        holder.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        holder.addView(shine, FrameLayout.LayoutParams(MATCH, MATCH))
        val rim = View(this).apply {
            background = roundRect(dp(12).toFloat(), Color.TRANSPARENT, dp(3), Color.WHITE)
            alpha = 0f
        }
        holder.addView(rim, FrameLayout.LayoutParams(MATCH, MATCH))

        val name = label(14f, shadow = true).apply {
            this.text = title
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            alpha = 0f
        }

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            setPadding(dp(8), 0, dp(8), 0)
            isFocusable = true
            isClickable = true
            if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        }
        item.addView(holder, LinearLayout.LayoutParams(dp(w), dp(h)))
        item.addView(name, LinearLayout.LayoutParams(dp(w), WRAP).apply { topMargin = dp(10) })

        item.setOnFocusChangeListener { _, f ->
            val s = if (f) 1.12f else 1f
            holder.animate().scaleX(s).scaleY(s).setDuration(140).start()
            content.animate().translationZ(if (f) dp(12).toFloat() else 0f).setDuration(140).start()
            rim.animate().alpha(if (f) 1f else 0f).setDuration(140).start()
            name.animate().alpha(if (f) 1f else 0f).setDuration(140).start()
            animShine(shine, f)
            if (f) setAmbient(accent, item)
        }
        item.setOnClickListener { click() }
        if (long != null) item.setOnLongClickListener { long(); true }
        return item
    }

    // ───────────────────────── запуск и полка ─────────────────────────

    fun launch(a: App, fromShelf: Boolean = false) {
        val intent = a.intent
        if (intent == null) {
            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
            runCatching { startActivity(details) }
                .onFailure { toast("Не удалось открыть сведения о приложении") }
            return
        }

        if (runCatching { startActivity(intent) }.isSuccess) {
            prefs.recent = (listOf(a.pkg) + prefs.recent.filter { it != a.pkg }).take(8)
            launchedFromShelf = fromShelf
        }
    }

    private fun buildShelf() {
        val launchableApps = apps.filter { it.intent != null }
        val order = if (prefs.showRecent) {
            val rec = prefs.recent.mapNotNull { p -> launchableApps.firstOrNull { it.pkg == p } }
            rec + launchableApps.filter { it !in rec }
        } else launchableApps
        val w = cardW()
        val key = w.toString() + ":tv-center:" + order.joinToString(",") { it.pkg }
        if (key == shelfKey) return
        shelfKey = key
        shelfRow.removeAllViews()
        val h = w * 3 / 5
        for (a in order) {
            shelfRow.addView(shelfItem(w, h, a.label, thumb(a), { launch(a, true) }, { dockToggle(a, false) }, a.accent))
        }

        val tvTile = TextView(this).apply {
            text = "TV"
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = glassBg(16)
        }
        shelfRow.addView(
            shelfItem(w, h, "Телевидение", tvTile, { openTvCenter() }, null, 0xFF62B5FF.toInt())
        )
    }

    private fun thumb(a: App): ImageView = roundedImage().also { fillThumb(it, a) }

    /** Баннер приложения; если баннера нет — иконка на плитке. */
    private fun fillThumb(img: ImageView, a: App) {
        val b = a.banner
        if (b != null) {
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setPadding(0, 0, 0, 0)
            img.setBackgroundColor(Color.TRANSPARENT)
            img.setImageDrawable(b)
        } else {
            img.scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dp(16)
            img.setPadding(pad, pad, pad, pad)
            img.setBackgroundColor(Color.argb(150, 28, 40, 58))
            img.setImageDrawable(a.icon)
        }
    }

    private fun focusDock() {
        for (i in 0 until dock.childCount) {
            val c = dock.getChildAt(i)
            if (c.isFocusable) { c.requestFocus(); return }
        }
    }

    // ───────────────────────── оверлеи ─────────────────────────

    private fun showOverlay(v: View, focus: View?) {
        closeOverlay()
        opener = currentFocus
        root.addView(v, MATCH, MATCH)
        home.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        overlay = v
        v.post { focus?.requestFocus() }
    }

    private fun closeOverlay(): Boolean {
        val o = overlay ?: return false
        root.removeView(o)
        overlay = null
        cleaner = null
        settingsView = null
        backHandler = null
        home.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        val op = opener
        opener = null
        if (op == null || !op.isAttachedToWindow || !op.requestFocus()) focusDock()
        return true
    }

    fun openCleaner() {
        val c = CleanerView(this)
        showOverlay(c.view, c.focus)
        cleaner = c
        backHandler = { c.onBack() }
    }

    fun openTvCenter() {
        val tv = TvCenter(this)
        showOverlay(tv.view, tv.focus)
    }

    fun openSettings(section: Int) {
        val s = SettingsView(this, section)
        showOverlay(s.view, s.focus)
        settingsView = s
    }

    // ───────────────────────── защита, лаунчер, бездействие ─────────────────────────

    /** Включает/выключает блокировку рекламы и трекеров (локальный DNS-фильтр через VPN). */
    fun setAdblock(on: Boolean) {
        prefs.adblock = on
        if (!on) {
            AdBlockVpnService.stop(this)
            return
        }
        val consent = try { VpnService.prepare(this) } catch (e: Exception) { null }
        if (consent == null) {
            AdBlockVpnService.start(this)
            return
        }
        try {
            startActivityForResult(consent, 71)
        } catch (e: Exception) {
            prefs.adblock = false
            toast("На этом ТВ нет окна разрешения VPN. Попробуйте системный DNS-фильтр (нужен ADB)")
        }
    }

    fun restartShield() {
        if (!prefs.adblock) return
        AdBlockVpnService.stop(this)
        handler.postDelayed({ AdBlockVpnService.start(this) }, 500)
    }

    private fun pickApk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        try {
            startActivityForResult(intent, 73)
        } catch (e: Exception) {
            toast("Не удалось открыть выбор файлов")
        }
    }

    private fun installSelectedApk(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            pendingApkUri = uri
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            try {
                startActivityForResult(intent, 74)
            } catch (e: Exception) {
                toast("Не удалось открыть настройки установки")
            }
            return
        }
        launchApkInstaller(uri)
    }

    private fun launchApkInstaller(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            toast("Не удалось запустить установщик APK")
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 71) {
            if (res == RESULT_OK) {
                AdBlockVpnService.start(this)
            } else {
                prefs.adblock = false
                toast("Разрешение VPN не выдано — защита выключена")
            }
        } else if (req == 73 && res == RESULT_OK) {
            data?.data?.let { installSelectedApk(it) }
        } else if (req == 74) {
            val uri = pendingApkUri
            pendingApkUri = null
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
                uri?.let { launchApkInstaller(it) }
            } else {
                toast("Разрешение на установку APK не выдано")
            }
        }
        updateTexts()
        settingsView?.refresh()
    }

    fun isDefaultHome(): Boolean {
        val ri = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )
        return ri?.activityInfo?.packageName == packageName
    }

    /** Просит систему сделать ClearYourTV главным экраном. */
    @Suppress("DEPRECATION")
    fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), 72)
                    return
                }
            } catch (e: Exception) {
                // идём дальше по запасным путям
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            return
        } catch (e: Exception) {
            // на этом ТВ нет такого экрана
        }
        Toast.makeText(
            this,
            "Настройки ТВ → Приложения → По умолчанию → Главный экран. Или по ADB: Настройки → Лаунчер и система",
            Toast.LENGTH_LONG
        ).show()
    }

    private val idleRun = Runnable { if (overlay == null && !isFinishing) openBlank() }

    private fun resetIdle() {
        handler.removeCallbacks(idleRun)
        val m = intArrayOf(0, 5, 15, 30)[prefs.idleMin.coerceIn(0, 3)]
        if (m > 0) handler.postDelayed(idleRun, m * 60_000L)
    }

    fun sys(action: String) {
        runCatching { startActivity(Intent(action)) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    // ───────────────────────── управление ТВ ─────────────────────────

    private fun volumeRow(): View {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        val bar = Bar(this)
        val value = label(13f, Color.argb(180, 255, 255, 255))

        fun muted() = Build.VERSION.SDK_INT >= 23 && am.isStreamMute(stream)
        fun update() {
            val v = am.getStreamVolume(stream)
            bar.progress = if (muted()) 0f else v.toFloat() / max
            value.text = when {
                am.isVolumeFixed -> "пультом ТВ"
                muted() -> "Выкл"
                else -> "${v * 100 / max}%"
            }
        }
        fun step(dir: Int) {
            am.adjustStreamVolume(
                stream, if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0
            )
            update()
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(label(15f, bold = true).apply { text = "Громкость" })
            addView(bar, LinearLayout.LayoutParams(0, dp(8), 1f).apply { setMargins(dp(14), 0, dp(14), 0) })
            addView(value)
        }
        row.makeFocusable(14, rest = Color.argb(40, 255, 255, 255), scale = 1.04f)
        row.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23) am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            update()
        }
        row.setOnKeyListener { _, code, ev ->
            if (ev.action != KeyEvent.ACTION_DOWN) {
                false
            } else when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { step(-1); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { step(1); true }
                else -> false
            }
        }
        update()
        return row
    }

    /** Переключение HDMI и других входов ТВ одним нажатием (если ТВ-фреймворк есть на устройстве). */
    private fun inputsRow(): View? {
        val inputs = runCatching {
            val tm = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
            tm.tvInputList.filter { it.isPassthroughInput }.map { it.id to it.loadLabel(this).toString() }
        }.getOrDefault(emptyList())
        if (inputs.isEmpty()) return null
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        inputs.take(4).forEach { (inputId, inputName) ->
            val chip = label(13f, bold = true).apply {
                text = inputName
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(8), dp(12), dp(8), dp(12))
                makeFocusable(14, rest = Color.argb(40, 255, 255, 255), scale = 1.04f)
                setOnClickListener {
                    closeOverlay()
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)))
                    }
                }
            }
            line.addView(chip, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        return line
    }

    private fun openBlank() {
        val v = View(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            setOnClickListener { closeOverlay() }
            setOnKeyListener { _, _, ev -> if (ev.action == KeyEvent.ACTION_DOWN) closeOverlay(); true }
        }
        showOverlay(v, v)
    }

    private class Tile(val title: String, val sub: String, val go: () -> Unit)

    private fun openControl() {
        val tiles = listOf(
            Tile("Wi-Fi", "Сеть и интернет") { sys(Settings.ACTION_WIFI_SETTINGS) },
            Tile("Bluetooth", "Устройства") { sys(Settings.ACTION_BLUETOOTH_SETTINGS) },
            Tile("Изображение", "Экран и разрешение") { sys(Settings.ACTION_DISPLAY_SETTINGS) },
            Tile("Звук", "Аудиовыход и громкость") { sys(Settings.ACTION_SOUND_SETTINGS) },
            Tile("Приложения", "Установленные приложения") { sys(Settings.ACTION_APPLICATION_SETTINGS) },
            Tile("Дата и время", "Часы и часовой пояс") { sys(Settings.ACTION_DATE_SETTINGS) },
            Tile("Спец. возможности", "Доступность и управление") { sys(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            Tile("Очистка", "ОЗУ, кэш, приложения") { openCleaner() },
            Tile("Защита", if (prefs.adblock) "Включена" else "Выключена") { setAdblock(!prefs.adblock) },
            Tile("Чёрный экран", "Погасить картинку") { openBlank() },
            Tile("Настройки ТВ", "Системные настройки") { sys(Settings.ACTION_SETTINGS) }
        )
        val panel = FrameLayout(this).apply { setBackgroundColor(0x55000000) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(24)
            setPadding(dp(14), dp(14), dp(14), dp(12))
        }
        card.addView(label(15f, bold = true).apply { text = "Пункт управления"; setPadding(dp(6), 0, 0, dp(10)) })

        val vol = volumeRow()
        card.addView(vol, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        inputsRow()?.let { card.addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
        var first: View? = vol
        tiles.chunked(2).forEach { pair ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { t ->
                val v = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    addView(label(15f, bold = true).apply { text = t.title })
                    addView(label(11f, Color.argb(180, 255, 255, 255)).apply { text = t.sub; setPadding(0, dp(4), 0, 0) })
                    makeFocusable(14, rest = Color.argb(40, 255, 255, 255), scale = 1.04f)
                    setOnClickListener { closeOverlay(); t.go() }
                }
                if (first == null) first = v
                line.addView(v, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
            }
            card.addView(line)
        }
        card.addView(label(11f, Color.argb(170, 255, 255, 255)).apply {
            text = "← → громкость  ·  OK — открыть  ·  Назад — закрыть"
            setPadding(dp(6), dp(10), 0, 0)
        })
        panel.addView(card, FrameLayout.LayoutParams(dp(380), WRAP, Gravity.TOP or Gravity.END)
            .apply { topMargin = dp(80); rightMargin = dp(40) })
        showOverlay(panel, first)
    }

    private fun openAllApps() {
        val panel = FrameLayout(this).apply { setBackgroundColor(0x99000000.toInt()) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(28)
            setPadding(dp(28), dp(20), dp(28), dp(16))
        }
        card.addView(label(22f, bold = true).apply { text = "Все приложения"; setPadding(0, 0, 0, dp(10)) })
        var visibleApps = apps
        val adapter = AppsAdapter { visibleApps }
        val search = EditText(this).apply {
            hint = "Найти приложение…"
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(150, 255, 255, 255))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundRect(dp(12).toFloat(), Color.argb(55, 255, 255, 255))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val q = s?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
                    visibleApps = if (q.isEmpty()) apps else apps.filter { it.label.lowercase(Locale.ROOT).contains(q) || it.pkg.lowercase(Locale.ROOT).contains(q) }
                    adapter.notifyDataSetChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        card.addView(search, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })
        val grid = GridView(this).apply {
            numColumns = 4
            setDrawSelectorOnTop(true)
            horizontalSpacing = dp(8)
            verticalSpacing = dp(8)
            selector = roundRect(dp(14).toFloat(), 0x22FFFFFF, dp(3), Color.WHITE)
            this.adapter = adapter
            setOnItemClickListener { _, _, p, _ -> visibleApps.getOrNull(p)?.let { launch(it) } }
            setOnItemLongClickListener { _, _, p, _ ->
                val a = visibleApps.getOrNull(p) ?: return@setOnItemLongClickListener true
                if (a.intent == null) {
                    Toast.makeText(this@MainActivity, "Это приложение нельзя добавить в Dock", Toast.LENGTH_SHORT).show()
                    return@setOnItemLongClickListener true
                }
                val had = a.pkg in dockPackages()
                toggleDock(a.pkg)
                Toast.makeText(this@MainActivity, if (had) "Убрано из Dock" else "Добавлено в Dock", Toast.LENGTH_SHORT).show()
                true
            }
        }
        card.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
        card.addView(label(11f, Color.argb(170, 255, 255, 255)).apply {
            text = "OK — запустить  ·  удерживайте OK — добавить в Dock или убрать  ·  Назад — закрыть"
            setPadding(0, dp(10), 0, 0)
        })
        panel.addView(card, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(dp(80), dp(48), dp(80), dp(48)) })
        showOverlay(panel, grid)
    }

    private inner class AppsAdapter(private val source: () -> List<App> = { apps }) : BaseAdapter() {
        override fun getCount() = source().size
        override fun getItem(p: Int) = source()[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, cv: View?, parent: ViewGroup): View {
            val v = (cv as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(roundedImage(), LinearLayout.LayoutParams(MATCH, dp(96)))
                addView(label(13f).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, dp(6), 0, 0)
                })
                addView(label(10f, Color.LTGRAY).apply {
                    maxLines = 1
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                })
            }
            val a = source()[p]
            fillThumb(v.getChildAt(0) as ImageView, a)
            (v.getChildAt(1) as TextView).text = a.label
            (v.getChildAt(2) as TextView).text = when {
                a.intent == null && a.isSystem -> "Системное · Нет запуска"
                a.intent == null -> "Нет кнопки запуска"
                a.isSystem -> "Системное приложение"
                else -> ""
            }
            return v
        }
    }
}
