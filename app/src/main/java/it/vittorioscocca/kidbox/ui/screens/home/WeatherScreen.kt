package it.vittorioscocca.kidbox.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Meteo in dettaglio: si apre dal tocco sulla barra della Home. Stessa fonte
 * (Open-Meteo, senza chiave) e stessa posizione approssimata; qui in più il
 * vento, l'umidità, le 24 ore e i sette giorni. La citazione di Open-Meteo
 * (CC BY 4.0) in fondo è un obbligo di licenza.
 */
private data class DetailHour(val timeMillis: Long, val temp: Int, val code: Int, val isDay: Boolean, val rain: Int)

private data class DetailDay(val dayMillis: Long, val code: Int, val min: Int, val max: Int, val rain: Int)

private data class WeatherDetail(
    val city: String?,
    val temp: Int,
    val feels: Int,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Int,
    val hours: List<DetailHour>,
    val days: List<DetailDay>,
)

private sealed interface WeatherState {
    data object Loading : WeatherState
    data object NoLocation : WeatherState
    data object Error : WeatherState
    data class Ready(val detail: WeatherDetail) : WeatherState
}

@StringRes
private fun conditionRes(code: Int): Int = when (code) {
    0 -> R.string.weather_cond_clear
    1, 2 -> R.string.weather_cond_partly
    3 -> R.string.weather_cond_cloudy
    45, 48 -> R.string.weather_cond_fog
    in 51..57 -> R.string.weather_cond_drizzle
    in 61..67, in 80..82 -> R.string.weather_cond_rain
    in 71..77, 85, 86 -> R.string.weather_cond_snow
    in 95..99 -> R.string.weather_cond_storm
    else -> R.string.weather_cond_cloudy
}

private fun fetchDetail(lat: Double, lon: Double, city: String?): WeatherDetail {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(Locale.US, lat, lon) +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
        "&hourly=temperature_2m,weather_code,is_day,precipitation_probability" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=7"
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
    val tz = TimeZone.getTimeZone(r.optString("timezone", "UTC"))
    val cur = r.getJSONObject("current")
    val h = r.getJSONObject("hourly")
    val d = r.getJSONObject("daily")
    // Gli orari sono locali al luogo, senza fuso: si leggono nel suo fuso.
    val hourFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = tz }
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
    val now = System.currentTimeMillis()
    val times = h.getJSONArray("time")
    val hours = ArrayList<DetailHour>()
    for (i in 0 until times.length()) {
        val t = hourFmt.parse(times.getString(i))?.time ?: continue
        if (t <= now - 3_600_000L) continue
        hours += DetailHour(
            timeMillis = t,
            temp = Math.round(h.getJSONArray("temperature_2m").getDouble(i)).toInt(),
            code = h.getJSONArray("weather_code").getInt(i),
            isDay = h.getJSONArray("is_day").getInt(i) == 1,
            rain = h.getJSONArray("precipitation_probability").optInt(i, 0),
        )
        if (hours.size == 24) break
    }
    val dTimes = d.getJSONArray("time")
    val days = ArrayList<DetailDay>()
    for (i in 0 until dTimes.length()) {
        val t = dayFmt.parse(dTimes.getString(i))?.time ?: continue
        days += DetailDay(
            dayMillis = t,
            code = d.getJSONArray("weather_code").getInt(i),
            min = Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)).toInt(),
            max = Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
            rain = d.getJSONArray("precipitation_probability_max").optInt(i, 0),
        )
    }
    return WeatherDetail(
        city = city,
        temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
        feels = Math.round(cur.getDouble("apparent_temperature")).toInt(),
        code = cur.getInt("weather_code"),
        isDay = cur.getInt("is_day") == 1,
        humidity = cur.getInt("relative_humidity_2m"),
        windKmh = Math.round(cur.getDouble("wind_speed_10m")).toInt(),
        hours = hours,
        days = days,
    )
}

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<WeatherState>(WeatherState.Loading, attempt) {
        value = WeatherState.Loading
        val coords = currentCoordinates(context)
        value = if (coords == null) {
            WeatherState.NoLocation
        } else {
            runCatching {
                withContext(Dispatchers.IO) {
                    fetchDetail(coords.first, coords.second, cityName(context, coords.first, coords.second))
                }
            }.fold({ WeatherState.Ready(it) }, { WeatherState.Error })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_widget_weather),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.kidBoxColors.title,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (val s = state) {
            WeatherState.Loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            WeatherState.NoLocation -> Message(stringResource(R.string.weather_no_location), null)
            WeatherState.Error -> Message(stringResource(R.string.weather_error)) { attempt++ }
            is WeatherState.Ready -> WeatherContent(s.detail)
        }
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, fontSize = 15.sp, color = MaterialTheme.kidBoxColors.subtitle)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.weather_retry)) }
        }
    }
}

@Composable
private fun WeatherContent(w: WeatherDetail) {
    val hourFmt = remember { SimpleDateFormat("HH", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WeatherCard {
            w.city?.let { city ->
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(w.code, w.isDay), fontSize = 48.sp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${w.temp}°", fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
                    Text(stringResource(conditionRes(w.code)), fontSize = 15.sp, color = MaterialTheme.kidBoxColors.subtitle)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(stringResource(R.string.weather_feels_like), "${w.feels}°")
                Stat(stringResource(R.string.weather_wind), stringResource(R.string.weather_wind_value, w.windKmh))
                Stat(stringResource(R.string.weather_humidity), "${w.humidity}%")
            }
        }
        WeatherCard {
            SectionTitle(stringResource(R.string.weather_next_hours))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                w.hours.forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(hourFmt.format(Date(h.timeMillis)), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
                        Text(weatherEmoji(h.code, h.isDay), fontSize = 20.sp)
                        Text("${h.temp}°", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.kidBoxColors.title)
                        Text(
                            if (h.rain > 0) "💧${h.rain}%" else " ",
                            fontSize = 11.sp,
                            color = MaterialTheme.kidBoxColors.subtitle,
                        )
                    }
                }
            }
        }
        WeatherCard {
            SectionTitle(stringResource(R.string.weather_next_days))
            w.days.forEachIndexed { i, d ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (i == 0) stringResource(R.string.weather_today) else dayFmt.format(Date(d.dayMillis)),
                        fontSize = 15.sp,
                        color = MaterialTheme.kidBoxColors.title,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (d.rain > 0) "💧${d.rain}%" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.kidBoxColors.subtitle,
                        modifier = Modifier.width(56.dp),
                    )
                    Text(weatherEmoji(d.code, true), fontSize = 20.sp, modifier = Modifier.width(36.dp))
                    Text("${d.max}° / ${d.min}°", fontSize = 15.sp, color = MaterialTheme.kidBoxColors.title)
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

@Composable
private fun WeatherCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.subtitle)
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.kidBoxColors.title)
    }
}
