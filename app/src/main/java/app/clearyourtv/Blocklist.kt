package app.clearyourtv

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Список доменов рекламы и трекеров. Совпадение по суффиксу: если в списке ads.example.com,
 * блокируются и x.ads.example.com. Встроенный список — основные рекламные сети, трекеры и
 * телеметрия производителей ТВ; большие открытые списки можно подтянуть из настроек.
 */
object Blocklist {
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"
    )

    @Volatile private var blockSet: HashSet<String> = HashSet()
    @Volatile private var allowSet: HashSet<String> = HashSet()

    val size: Int get() = blockSet.size
    val builtinCount: Int get() = BUILTIN.size

    private fun file(ctx: Context) = File(ctx.filesDir, "blocklist.txt")

    fun load(ctx: Context) {
        val block = HashSet<String>(BUILTIN)
        val allow = HashSet<String>()
        val f = file(ctx)
        if (f.exists()) {
            runCatching {
                f.forEachLine { raw ->
                    val l = raw.trim()
                    if (l.isEmpty() || l[0] == '#') return@forEachLine
                    if (l.startsWith("@@")) allow.add(l.substring(2)) else block.add(l)
                }
            }
        }
        blockSet = block
        allowSet = allow
    }

    fun isBlocked(domain: String): Boolean {
        var d = domain
        while (true) {
            if (d in allowSet) return false
            if (d in blockSet) return true
            val i = d.indexOf('.')
            if (i < 0 || i == d.length - 1) return false
            d = d.substring(i + 1)
        }
    }

    /** Скачивает большие списки, склеивает, сохраняет. Возвращает число доменов или -1 при неудаче. */
    fun update(ctx: Context): Int {
        val domains = HashSet<String>()
        val allows = HashSet<String>()
        var ok = 0
        for (u in SOURCES) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 25_000
                c.inputStream.bufferedReader().useLines { lines -> lines.forEach { parse(it, domains, allows) } }
                c.disconnect()
                ok++
            } catch (e: Exception) {
                // источник недоступен — пробуем остальные
            }
        }
        if (ok == 0 || domains.isEmpty()) return -1
        file(ctx).bufferedWriter().use { w ->
            domains.forEach { w.write(it); w.newLine() }
            allows.forEach { w.write("@@" + it); w.newLine() }
        }
        load(ctx)
        val p = Prefs(ctx)
        p.lastListUpdate = System.currentTimeMillis()
        p.listCount = blockSet.size
        return blockSet.size
    }

    fun reset(ctx: Context) {
        file(ctx).delete()
        val p = Prefs(ctx)
        p.lastListUpdate = 0L
        p.listCount = 0
        load(ctx)
    }

    fun summary(ctx: Context): String {
        val p = Prefs(ctx)
        if (p.lastListUpdate == 0L) return "встроенный: ${BUILTIN.size}"
        val d = SimpleDateFormat("d MMM", Locale.forLanguageTag("ru")).format(Date(p.lastListUpdate))
        return "${p.listCount} доменов · $d"
    }

    private fun parse(raw: String, d: MutableSet<String>, a: MutableSet<String>) {
        val l = raw.trim()
        if (l.isEmpty() || l[0] == '#' || l[0] == '!') return
        if (l.startsWith("@@||")) {
            simple(l.substring(4))?.let { a.add(it) }
            return
        }
        if (l.startsWith("||")) {
            simple(l.substring(2))?.let { d.add(it) }
            return
        }
        val t = l.split(' ', '\t').filter { it.isNotEmpty() }
        if (t.size >= 2 && (t[0] == "0.0.0.0" || t[0] == "127.0.0.1")) {
            val h = t[1].lowercase()
            if (valid(h) && h != "localhost" && h != "local" && h != "broadcasthost" && h != "0.0.0.0") d.add(h)
        }
    }

    /** "example.org^" или "example.org^\$important" -> example.org; всё остальное (маски, опции) пропускаем. */
    private fun simple(r: String): String? {
        val caret = r.indexOf('^')
        if (caret < 0) return null
        val rest = r.substring(caret + 1)
        if (rest.isNotEmpty() && rest != "\$important") return null
        val host = r.substring(0, caret).lowercase()
        return if (valid(host)) host else null
    }

    private fun valid(h: String): Boolean =
        h.length in 3..253 && h.contains('.') &&
            h.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }

    private val BUILTIN = listOf(
        // Google / рекламные и измерительные сервисы
        "doubleclick.net", "googleadservices.com", "googlesyndication.com", "googletagservices.com",
        "google-analytics.com", "googletagmanager.com", "adservice.google.com", "app-measurement.com",
        "crashlytics.com",
        // Основные рекламные сети и биржи
        "adcolony.com", "adnxs.com", "applovin.com", "unityads.unity3d.com", "ads.unity3d.com",
        "ironsrc.com", "ironsource.mobi", "supersonicads.com", "vungle.com", "inmobi.com", "mopub.com",
        "chartboost.com", "tapjoy.com", "smaato.net", "pubmatic.com", "rubiconproject.com", "openx.net",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "adsrvr.org", "bidswitch.net",
        "casalemedia.com", "contextweb.com", "smartadserver.com", "teads.tv", "yieldmo.com", "3lift.com",
        "amazon-adsystem.com", "serving-sys.com", "adform.net", "adition.com", "advertising.com",
        // Измерение, атрибуция, аналитика
        "appsflyer.com", "adjust.com", "adjust.io", "kochava.com", "singular.net", "flurry.com",
        "mixpanel.com", "segment.io", "api.amplitude.com", "hotjar.com", "moatads.com",
        "scorecardresearch.com", "quantserve.com", "adsafeprotected.com", "doubleverify.com",
        // Данные о пользователях и ретаргетинг
        "demdex.net", "krxd.net", "bluekai.com", "exelator.com", "eyeota.net", "tapad.com", "rlcdn.com",
        // Соцсети: пиксели и рекламные точки
        "an.facebook.com", "pixel.facebook.com", "ads.twitter.com", "analytics.twitter.com",
        "ads.tiktok.com", "analytics.tiktok.com",
        // Яндекс и Mail.ru: реклама и счётчики
        "an.yandex.ru", "mc.yandex.ru", "mc.yandex.com", "yabs.yandex.ru", "awaps.yandex.net",
        "adsdk.yandex.ru", "yandexadexchange.net", "adfox.ru", "api.appmetrica.yandex.net",
        "report.appmetrica.yandex.net", "appmetrica.yandex.com", "ad.mail.ru", "top-fwz1.mail.ru",
        "top.mail.ru", "counter.yadro.ru", "tracker-api.my.com",
        // Телевизоры: реклама и ACR (распознавание того, что вы смотрите)
        "samsungads.com", "samsungacr.com", "lgtvsdp.com", "ad.lgappstv.com", "tvinteractive.tv",
        "samba.tv", "tracking.miui.com", "data.mistat.xiaomi.com", "ad.xiaomi.com", "api.ad.xiaomi.com",
        "sdkconfig.ad.xiaomi.com"
    )
}
