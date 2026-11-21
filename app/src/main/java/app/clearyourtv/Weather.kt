package app.clearyourtv

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Погода из Open-Meteo: без ключа, без регистрации, без аналитики. */
object Weather {
    class City(val name: String, val lat: Double, val lon: Double)

    val cities = listOf(
        City("Москва", 55.75, 37.62), City("Санкт-Петербург", 59.94, 30.31),
        City("Казань", 55.79, 49.12), City("Екатеринбург", 56.84, 60.61),
        City("Новосибирск", 55.03, 82.92), City("Краснодар", 45.04, 38.98),
        City("Минск", 53.90, 27.57), City("Алматы", 43.24, 76.89)
    )

    private fun describe(code: Int) = when (code) {
        0 -> "ясно"
        1 -> "малооблачно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        in 51..57 -> "морось"
        in 61..67 -> "дождь"
        in 71..77 -> "снег"
        in 80..82 -> "ливень"
        85, 86 -> "снегопад"
        in 95..99 -> "гроза"
        else -> ""
    }

    fun fetch(c: City): String? = try {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
                "&current=temperature_2m,weather_code&timezone=auto")
        val con = url.openConnection() as HttpURLConnection
        con.connectTimeout = 6000
        con.readTimeout = 6000
        val body = con.inputStream.bufferedReader().use { it.readText() }
        con.disconnect()
        val cur = JSONObject(body).getJSONObject("current")
        val t = Math.round(cur.getDouble("temperature_2m")).toInt()
        val ts = if (t > 0) "+$t" else "$t"
        "${c.name} · $ts° · ${describe(cur.getInt("weather_code"))}"
    } catch (e: Exception) {
        null
    }
}
