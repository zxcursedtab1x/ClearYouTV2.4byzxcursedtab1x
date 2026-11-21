package app.clearyourtv

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.util.Locale

/** Системная статистика и «ускорение». Без особых прав, без root. */
object Sys {
    class Mem(val total: Long, val avail: Long) {
        val usedFrac: Float get() = if (total > 0) (total - avail).toFloat() / total else 0f
    }

    fun ram(ctx: Context): Mem {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Mem(mi.totalMem, mi.availMem)
    }

    fun disk(): Mem {
        val s = StatFs(Environment.getDataDirectory().path)
        return Mem(s.totalBytes, s.availableBytes)
    }

    /** Закрывает фоновые процессы приложений (на foreground не влияет). Возвращает число попыток. */
    fun boost(ctx: Context, pkgs: List<String>): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        for (p in pkgs) {
            if (p == ctx.packageName) continue
            runCatching { am.killBackgroundProcesses(p); n++ }
        }
        return n
    }

    fun fmt(bytes: Long): String {
        val ru = Locale.forLanguageTag("ru")
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return String.format(ru, "%.1f ГБ", gb)
        return String.format(ru, "%d МБ", bytes / (1024L * 1024L))
    }
}
