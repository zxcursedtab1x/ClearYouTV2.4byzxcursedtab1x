package app.clearyourtv

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("clearyourtv", Context.MODE_PRIVATE)

    var scene: Int
        get() = sp.getInt("scene", 0)
        set(v) { sp.edit().putInt("scene", v).apply() }
    var h24: Boolean
        get() = sp.getBoolean("h24", true)
        set(v) { sp.edit().putBoolean("h24", v).apply() }
    var showGreeting: Boolean
        get() = sp.getBoolean("greeting", true)
        set(v) { sp.edit().putBoolean("greeting", v).apply() }
    var showDate: Boolean
        get() = sp.getBoolean("date", true)
        set(v) { sp.edit().putBoolean("date", v).apply() }
    var showWeather: Boolean
        get() = sp.getBoolean("weather", true)
        set(v) { sp.edit().putBoolean("weather", v).apply() }
    var city: Int
        get() = sp.getInt("city", 0)
        set(v) { sp.edit().putInt("city", v).apply() }
    var iconSize: Int
        get() = sp.getInt("iconSize", 1)
        set(v) { sp.edit().putInt("iconSize", v).apply() }
    var glass: Int
        get() = sp.getInt("glass", 1)
        set(v) { sp.edit().putInt("glass", v).apply() }
    var liveBg: Boolean
        get() = sp.getBoolean("live_bg", true)
        set(v) { sp.edit().putBoolean("live_bg", v).apply() }
    var showRecent: Boolean
        get() = sp.getBoolean("recent_on", true)
        set(v) { sp.edit().putBoolean("recent_on", v).apply() }
    var recent: List<String>
        get() = sp.getString("recent", null)?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        set(v) { sp.edit().putString("recent", v.joinToString(",")).apply() }
    var adblock: Boolean
        get() = sp.getBoolean("adblock", false)
        set(v) { sp.edit().putBoolean("adblock", v).apply() }
    var dnsUpstream: Int
        get() = sp.getInt("dns_up", 0)
        set(v) { sp.edit().putInt("dns_up", v).apply() }
    var blockedTotal: Long
        get() = sp.getLong("blocked_total", 0L)
        set(v) { sp.edit().putLong("blocked_total", v).apply() }
    var lastListUpdate: Long
        get() = sp.getLong("list_updated", 0L)
        set(v) { sp.edit().putLong("list_updated", v).apply() }
    var listCount: Int
        get() = sp.getInt("list_count", 0)
        set(v) { sp.edit().putInt("list_count", v).apply() }
    var shine: Boolean
        get() = sp.getBoolean("shine", true)
        set(v) { sp.edit().putBoolean("shine", v).apply() }
    var ambientOn: Boolean
        get() = sp.getBoolean("ambient_on", true)
        set(v) { sp.edit().putBoolean("ambient_on", v).apply() }
    var introOn: Boolean
        get() = sp.getBoolean("intro_on", true)
        set(v) { sp.edit().putBoolean("intro_on", v).apply() }
    var auroraSpeed: Int
        get() = sp.getInt("aurora_speed", 1)
        set(v) { sp.edit().putInt("aurora_speed", v).apply() }
    var idleMin: Int
        get() = sp.getInt("idle_min", 0)
        set(v) { sp.edit().putInt("idle_min", v).apply() }
    var clockSize: Int
        get() = sp.getInt("clock_size", 1)
        set(v) { sp.edit().putInt("clock_size", v).apply() }
    var dockClean: Boolean
        get() = sp.getBoolean("dock_clean", true)
        set(v) { sp.edit().putBoolean("dock_clean", v).apply() }

    var deviceProfile: String
        get() = sp.getString("device_profile", "") ?: ""
        set(v) { sp.edit().putString("device_profile", v).apply() }
    var setupCompleted: Boolean
        get() = sp.getBoolean("setup_completed", false)
        set(v) { sp.edit().putBoolean("setup_completed", v).apply() }
    fun resetAll() { sp.edit().clear().apply() }
    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(",")?.filter { it.isNotEmpty() }
        set(v) { sp.edit().putString("dock", v?.joinToString(",")).apply() }
}
