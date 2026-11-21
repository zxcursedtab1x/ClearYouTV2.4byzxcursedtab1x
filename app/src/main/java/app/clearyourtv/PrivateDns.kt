package app.clearyourtv

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Системный Private DNS (Android 9+) на AdGuard DNS: реклама и трекеры режутся на уровне ТВ,
 * без VPN и без нагрузки на процессор. Права на запись системных настроек выдаются один раз по ADB:
 * adb shell pm grant app.clearyourtv android.permission.WRITE_SECURE_SETTINGS
 */
object PrivateDns {
    const val HOST = "dns.adguard-dns.com"
    private const val MODE = "private_dns_mode"
    private const val SPEC = "private_dns_specifier"

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 28

    fun canWrite(ctx: Context): Boolean =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    fun isOn(ctx: Context): Boolean = try {
        Settings.Global.getString(ctx.contentResolver, MODE) == "hostname" &&
            Settings.Global.getString(ctx.contentResolver, SPEC) == HOST
    } catch (e: Exception) { false }

    fun set(ctx: Context, on: Boolean): Boolean = try {
        if (on) {
            Settings.Global.putString(ctx.contentResolver, SPEC, HOST)
            Settings.Global.putString(ctx.contentResolver, MODE, "hostname")
        } else {
            Settings.Global.putString(ctx.contentResolver, MODE, "opportunistic")
        }
        true
    } catch (e: Exception) { false }
}
