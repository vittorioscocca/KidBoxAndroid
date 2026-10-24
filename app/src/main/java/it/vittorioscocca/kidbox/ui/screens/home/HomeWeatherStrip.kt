package it.vittorioscocca.kidbox.ui.screens.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Barra del meteo in Home: ora, prossime ore, minima e massima di oggi.
 * Stessa fonte di iOS (Open-Meteo, senza chiave) così i due client non danno
 * previsioni diverse. La posizione è approssimata e resta sul telefono; senza
 * permesso o senza rete la barra non compare. Open-Meteo è CC BY 4.0: la
 * citazione sotto i dati è un obbligo di licenza, non un vezzo.
 */
data class WeatherHour(val timeMillis: Long, val temp: Int, val code: Int, val isDay: Boolean)

data class WeatherForecast(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val min: Int,
    val max: Int,
    val hours: List<WeatherHour>,
    val city: String?,
    val fetchedAt: Long,
)

/** Codici WMO di Open-Meteo → emoji (niente icone extra da portarsi nell'APK). */
fun weatherEmoji(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67, in 80..82 -> "🌧️"
    in 71..77, 85, 86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "☁️"
}

private object WeatherCache {
    var value: WeatherForecast? = null
    const val TTL_MS = 30 * 60 * 1000L
}

@SuppressLint("MissingPermission")
private suspend fun loadWeather(context: android.content.Context): WeatherForecast? {
    WeatherCache.value?.let { if (System.currentTimeMillis() - it.fetchedAt < WeatherCache.TTL_MS) return it }
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!granted) return null
    return runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = client.lastLocation.await()
            ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            ?: return null
        val f = withContext(Dispatchers.IO) {
            fetch(loc.latitude, loc.longitude).copy(city = cityName(context, loc.latitude, loc.longitude))
        }
        WeatherCache.value = f
        f
    }.getOrNull()
}

/** Nome della località; se il geocoder non risponde resta l'ultimo noto. */
private fun cityName(context: android.content.Context, lat: Double, lon: Double): String? {
    val prefs = context.getSharedPreferences("kidbox_prefs", android.content.Context.MODE_PRIVATE)
    val name = runCatching {
        @Suppress("DEPRECATION")
        android.location.Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea }
    }.getOrNull()
    if (!name.isNullOrBlank()) {
        prefs.edit().putString(KEY_WEATHER_CITY, name).apply()
        return name
    }
    return prefs.getString(KEY_WEATHER_CITY, null)
}

private const val KEY_WEATHER_CITY = "home_weather_city"

/** La fonte: Open-Meteo non ha un'app, il tocco apre il suo sito. */
private const val WEATHER_SOURCE_URL = "https://open-meteo.com/"

private fun fetch(lat: Double, lon: Double): WeatherForecast {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(Locale.US, lat, lon) +
        "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
        "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2"
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 8000
    conn.readTimeout = 8000
    val body = try {
        if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
        conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
    val r = JSONObject(body)
    val cur = r.getJSONObject("current")
    val h = r.getJSONObject("hourly")
    val d = r.getJSONObject("daily")
    // Gli orari sono locali al luogo, senza fuso: si leggono nel suo fuso.
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone(r.optString("timezone", "UTC"))
    val now = System.currentTimeMillis()
    val times = h.getJSONArray("time")
    val temps = h.getJSONArray("temperature_2m")
    val codes = h.getJSONArray("weather_code")
    val days = h.getJSONArray("is_day")
    val hours = ArrayList<WeatherHour>()
    for (i in 0 until times.length()) {
        val t = fmt.parse(times.getString(i))?.time ?: continue
        if (t <= now - 3_600_000L) continue
        hours += WeatherHour(t, Math.round(temps.getDouble(i)).toInt(), codes.getInt(i), days.getInt(i) == 1)
        if (hours.size == 8) break
    }
    return WeatherForecast(
        temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
        code = cur.getInt("weather_code"),
        isDay = cur.getInt("is_day") == 1,
        min = Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
        max = Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
        hours = hours.drop(1),
        city = null,
        fetchedAt = now,
    )
}

@Composable
fun HomeWeatherStrip(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val forecast by produceState<WeatherForecast?>(initialValue = WeatherCache.value) {
        value = loadWeather(context) ?: value
    }
    val f = forecast ?: return
    val uriHandler = LocalUriHandler.current
    val hourFmt = SimpleDateFormat("HH", Locale.getDefault())
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Column(
            modifier = Modifier
                .clickable { uriHandler.openUri(WEATHER_SOURCE_URL) }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            f.city?.let { city ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.kidBoxColors.subtitle,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(city, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.subtitle)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(f.code, f.isDay), fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${f.temp}°",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.kidBoxColors.title,
                    modifier = Modifier.weight(1f),
                )
                Text("${f.max}° / ${f.min}°", fontSize = 14.sp, color = MaterialTheme.kidBoxColors.subtitle)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                f.hours.forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(hourFmt.format(Date(h.timeMillis)), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
                        Text(weatherEmoji(h.code, h.isDay), fontSize = 20.sp)
                        Text("${h.temp}°", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.kidBoxColors.title)
                    }
                }
            }
            Text(
                stringResource(R.string.home_weather_source),
                fontSize = 11.sp,
                color = MaterialTheme.kidBoxColors.subtitle,
            )
        }
    }
}
