package app.clearyourtv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class TvCenter(private val activity: Activity) {

    private val white = 0xFFFFFFFF.toInt()
    private val muted = 0xFFB8C2D8.toInt()
    private val accent = 0xFF62B5FF.toInt()

    private var firstAction: View? = null
    private lateinit var content: LinearLayout

    val view: View = buildView()
    val focus: View? get() = firstAction

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun text(
        value: String,
        size: Float,
        color: Int = white,
        bold: Boolean = false
    ) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun buildView(): View {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(0xFF080D18.toInt())
            isFocusable = true
        }

        val scroll = ScrollView(activity)

        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(34), dp(48), dp(36))
        }

        content.addView(text("TV CENTER", 13f, accent, true))

        content.addView(
            text("Телевидение", 34f, white, true).apply {
                setPadding(0, dp(5), 0, dp(8))
            }
        )

        content.addView(
            text(
                "Эфирное, кабельное, спутниковое и интернет-телевидение",
                16f,
                muted
            )
        )

        addSection(
            "Эфирное ТВ",
            "DVB-T / DVB-T2",
            "Для работы необходим совместимый эфирный тюнер"
        ) {
            showInfo("Эфирное ТВ", "Подключение DVB-T/T2 зависит от поддержки тюнера системой телевизора")
        }

        addSection(
            "Кабельное ТВ",
            "DVB-C",
            "Для работы необходим совместимый кабельный тюнер"
        ) {
            showInfo("Кабельное ТВ", "Подключение DVB-C зависит от поддержки тюнера системой телевизора")
        }

        addSection(
            "Спутниковое ТВ",
            "DVB-S / DVB-S2",
            "Для работы необходим совместимый спутниковый тюнер"
        ) {
            showInfo("Спутниковое ТВ", "Подключение DVB-S/S2 зависит от поддержки тюнера системой телевизора")
        }

        addSection(
            "Системные входы",
            "HDMI и другие внешние источники",
            "Открыть входы, обнаруженные Android TV"
        ) {
            showInputs()
        }

        addSection(
            "IPTV",
            "M3U / XMLTV",
            "Интернет-телевидение — следующий этап разработки"
        ) {
            showInfo("IPTV", "IPTV-плеер с поддержкой M3U и XMLTV добавим следующим этапом")
        }

        scroll.addView(content)

        root.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        return root
    }

    private fun addSection(
        title: String,
        subtitle: String,
        description: String,
        action: () -> Unit
    ) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF172235.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF30415B.toInt())
            }
            isFocusable = true
            isClickable = true

            setOnFocusChangeListener { v, focused ->
                v.alpha = if (focused) 1f else 0.82f
                v.scaleX = if (focused) 1.025f else 1f
                v.scaleY = if (focused) 1.025f else 1f
            }

            setOnClickListener { action() }
        }

        card.addView(text(title, 21f, white, true))

        card.addView(
            text(subtitle, 13f, accent, true).apply {
                setPadding(0, dp(3), 0, dp(5))
            }
        )

        card.addView(text(description, 14f, muted))

        content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
            }
        )

        if (firstAction == null) firstAction = card
    }

    private fun showInfo(title: String, message: String) {
        android.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showInputs() {
        val inputs = runCatching {
            val manager = activity.getSystemService(Context.TV_INPUT_SERVICE)
                as TvInputManager

            manager.tvInputList
                .filter { it.isPassthroughInput }
                .map { it.id to it.loadLabel(activity).toString() }
        }.getOrDefault(emptyList())

        if (inputs.isEmpty()) {
            showInfo(
                "Системные входы",
                "Android TV не обнаружил доступных внешних ТВ-входов."
            )
            return
        }

        android.app.AlertDialog.Builder(activity)
            .setTitle("Системные входы")
            .setItems(inputs.map { it.second }.toTypedArray()) { _, index ->
                val inputId = inputs[index].first

                runCatching {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            TvContract.buildChannelUriForPassthroughInput(inputId)
                        )
                    )
                }.onFailure {
                    Toast.makeText(
                        activity,
                        "Не удалось открыть ТВ-вход",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }
}
