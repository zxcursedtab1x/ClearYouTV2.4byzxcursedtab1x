package app.clearyourtv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.app.ActivityManager
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Device-aware onboarding and read-only diagnostics. Never removes system packages. */
object DeviceSetup {
    val profiles = listOf("Android TV", "Google TV", "Сбер ТВ / Салют ТВ", "YaOS (Яндекс ТВ)")

    fun report(ctx: Context): String {
        val pm = ctx.packageManager
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes / (1024L * 1024L)
        val free = stat.availableBytes / (1024L * 1024L)
        val display = ctx.resources.displayMetrics
        val battery = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val batteryLevel = battery?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batteryScale = battery?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (batteryLevel >= 0 && batteryScale > 0) (batteryLevel * 100 / batteryScale) else -1
        val totalRam = mem.totalMem / (1024L * 1024L)
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi подключён"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet подключён"
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> "Сеть подключена"
                else -> "Нет активного подключения"
            }
        } else {
            @Suppress("DEPRECATION")
            if (cm.activeNetworkInfo?.isConnected == true) "Сеть подключена" else "Нет активного подключения"
        }
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME)
        val home = pm.resolveActivity(homeIntent, 0)?.activityInfo?.packageName ?: "не определён"
        val leanback = pm.hasSystemFeature("android.software.leanback")
        val tvInput = pm.hasSystemFeature("android.software.live_tv")
        return buildString {
            appendLine("Профиль ClearYourTV: выбирается вручную в настройках")
            appendLine("Производитель: ${Build.MANUFACTURER}")
            appendLine("Модель: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Сборка: ${Build.DISPLAY}")
            appendLine("Android TV / Leanback: ${if (leanback) "да" else "не обнаружено"}")
            appendLine("Live TV API: ${if (tvInput) "доступен" else "не обнаружен"}")
            appendLine("Память устройства: ${free} МБ свободно из ${total} МБ (${(free * 100 / total.coerceAtLeast(1))}% свободно)")
            appendLine("ОЗУ: ${mem.availMem / (1024L * 1024L)} МБ свободно из ${totalRam} МБ")
            appendLine("Экран: ${display.widthPixels} × ${display.heightPixels}, плотность ${display.densityDpi} dpi")
            appendLine("Процессор ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Ядра CPU: ${Runtime.getRuntime().availableProcessors()}")
            appendLine("Батарея: ${if (batteryPct >= 0) "$batteryPct%" else "нет данных / ТВ без батареи"}")
            appendLine("Часовой пояс: ${java.util.TimeZone.getDefault().id}")
            appendLine("Сеть: $network")
            appendLine("Текущий HOME: $home")
        }
    }

    fun showWizard(activity: Activity, prefs: Prefs, onDone: () -> Unit) {
        val current = profiles.indexOf(prefs.deviceProfile).let { if (it >= 0) it else -1 }
        AlertDialog.Builder(activity)
            .setTitle("Выбор платформы ClearYourTV")
            .setMessage("Выберите платформу вашего телевизора. Автоматического определения нет.")
            .setSingleChoiceItems(profiles.toTypedArray(), current) { dialog, which ->
                prefs.deviceProfile = profiles[which]
                prefs.setupCompleted = true
                dialog.dismiss()
                showResult(activity, prefs.deviceProfile)
                onDone()
            }
            .show()
    }

    fun showResult(activity: Activity, profile: String) {
        val message = "Профиль сохранён: $profile\n\nClearYourTV будет использовать этот профиль для подбора совместимых функций. Фактические возможности зависят от модели телевизора и версии прошивки. Системные приложения, драйверы и службы автоматически удаляться не будут."
        AlertDialog.Builder(activity).setTitle("Настройка устройства").setMessage(message).setPositiveButton("Продолжить", null).show()
    }

    fun showDiagnostics(activity: Activity) {
        val report = report(activity)
        val recommendations = buildString {
            appendLine("\nРекомендации")
            appendLine("• Проверьте, выбран ли ClearYourTV главным экраном.")
            appendLine("• Удаляйте только приложения, которые вы узнаёте и больше не используете.")
            appendLine("• Не отключайте системные службы, обновления, компоненты ТВ и драйверы.")
            appendLine("• Очистка кэша доступна только через штатные системные экраны, если прошивка её разрешает.")
            appendLine("• Для Сбер ТВ и YaOS совместимость зависит от конкретной модели и версии прошивки.")
        }
        AlertDialog.Builder(activity).setTitle("Диагностика и Smart Cleaner")
            .setMessage(report + recommendations)
            .setPositiveButton("Понятно", null)
            .setNeutralButton("Открыть настройки ТВ") { _, _ ->
                try { activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
            }.show()
    }
}
