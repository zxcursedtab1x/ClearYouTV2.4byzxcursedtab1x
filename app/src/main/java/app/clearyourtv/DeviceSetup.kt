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
import java.util.Locale

/** Device-aware onboarding and read-only diagnostics. Never removes system packages. */
object DeviceSetup {
    val profiles = listOf("Автоопределение", "Android TV", "Google TV", "Сбер ТВ / Салют ТВ", "YaOS (Яндекс ТВ)", "Другая Android TV-прошивка", "Samsung Tizen", "LG webOS", "Hisense VIDAA")

    fun detected(ctx: Context): String {
        val m = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL + " " + Build.DISPLAY).lowercase(Locale.ROOT)
        return when {
            m.contains("sber") || m.contains("salut") -> "Сбер ТВ / Салют ТВ"
            m.contains("yandex") || m.contains("yaos") -> "YaOS (Яндекс ТВ)"
            m.contains("google tv") -> "Google TV"
            m.contains("android tv") || ctx.packageManager.hasSystemFeature("android.software.leanback") -> "Android TV"
            else -> "Другая Android TV-прошивка"
        }
    }

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
            appendLine("Профиль: ${detected(ctx)}")
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
        val detected = detected(activity)
        val current = prefs.deviceProfile.ifBlank { "Автоопределение" }
        val selected = profiles.indexOf(current).let { if (it >= 0) it else 0 }
        AlertDialog.Builder(activity)
            .setTitle("Добро пожаловать в ClearYourTV")
            .setMessage("Определённое устройство: $detected\n\nВыберите платформу. ClearYourTV проверит возможности прошивки и подберёт совместимые функции. Выбор можно изменить позже.")
            .setSingleChoiceItems(profiles.toTypedArray(), selected) { dialog, which ->
                val value = profiles[which]
                prefs.deviceProfile = if (value == profiles[0]) detected else value
                prefs.setupCompleted = true
                dialog.dismiss()
                showResult(activity, prefs.deviceProfile)
                onDone()
            }
            .setOnCancelListener {
                if (!prefs.setupCompleted) {
                    prefs.deviceProfile = detected
                    prefs.setupCompleted = true
                    onDone()
                }
            }
            .show()
    }

    fun showResult(activity: Activity, profile: String) {
        val androidBased = profile !in listOf("Samsung Tizen", "LG webOS", "Hisense VIDAA")
        val message = if (androidBased) {
            "Профиль сохранён: $profile\n\nБудут включены только функции, доступные этой Android-прошивке. Системные приложения, драйверы и службы производителя автоматически удаляться не будут. Перед оптимизацией ClearYourTV покажет отчёт и запросит подтверждение."
        } else {
            "Профиль сохранён: $profile\n\nОбычный Android APK не может стать системным Launcher на этой платформе. Для неё нужна отдельная версия приложения из магазина производителя."
        }
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
