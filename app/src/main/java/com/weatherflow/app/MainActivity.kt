package com.weatherflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val Orange = Color(0xFFFF6500)
private val LightBg = Color(0xFFF5F6FA)
private val DarkBg = Color(0xFF111318)

data class WeatherData(
    val city: String, val temperature: Int, val feels: Int, val condition: String,
    val humidity: Int, val wind: Double, val uv: Double,
    val hourly: List<Pair<String, Int>>, val daily: List<Triple<String, Int, Int>>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherApp() }
    }
}

@Composable
fun WeatherApp() {
    var dark by remember { mutableStateOf(false) }
    var city by remember { mutableStateOf("Alexandria") }
    var query by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<WeatherData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load(place: String) {
        loading = true; error = null
        scope.launch {
            try {
                data = fetchWeather(place)
                city = data?.city ?: place
            } catch (e: Exception) {
                error = "تعذّر تحميل الطقس. تحقق من اتصال الإنترنت وحاول مجددًا."
            } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load(city) }

    val bg = if (dark) DarkBg else LightBg
    val surface = if (dark) Color(0xFF20232B) else Color.White
    val primaryText = if (dark) Color(0xFFF4F4F6) else Color(0xFF17191F)
    val secondary = if (dark) Color(0xFFB7BBC5) else Color(0xFF737987)

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(primary = Orange, background = bg, surface = surface)
                      else lightColorScheme(primary = Orange, background = bg, surface = surface)
    ) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("WEATHERFLOW", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("الطقس حولك", color = primaryText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { dark = !dark }) {
                        Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, null, tint = primaryText)
                    }
                    IconButton(onClick = { load(city) }) {
                        Icon(Icons.Default.Refresh, "تحديث", tint = primaryText)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("ابحث عن مدينة") },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        trailingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (query.isNotBlank()) { load(query.trim()); query = "" } },
                        colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) { Text("بحث") }
                }
                Spacer(Modifier.height(22.dp))

                if (loading) {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Orange)
                    }
                } else if (error != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, null, tint = Orange, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(error!!, color = primaryText, textAlign = TextAlign.Center)
                            TextButton(onClick = { load(city) }) { Text("إعادة المحاولة", color = Orange) }
                        }
                    }
                } else data?.let { w ->
                    Text("الآن في ${w.city}", color = secondary, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(28.dp)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${w.temperature}°", color = primaryText, fontSize = 76.sp, fontWeight = FontWeight.Light)
                                    Text(w.condition, color = primaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                    Text("المحسوسة ${w.feels}°", color = secondary, fontSize = 14.sp)
                                }
                                Icon(conditionIcon(w.condition), null, tint = Orange, modifier = Modifier.size(92.dp))
                            }
                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider(color = if (dark) Color(0xFF383C46) else Color(0xFFEDEEF2))
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Metric(Icons.Default.WaterDrop, "الرطوبة", "${w.humidity}%")
                                Metric(Icons.Default.Air, "الرياح", "${w.wind} كم/س")
                                Metric(Icons.Default.WbSunny, "UV", String.format(Locale.US, "%.1f", w.uv))
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("توقعات الساعة القادمة", primaryText)
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(22.dp)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp)) {
                            w.hourly.forEach { (time, temp) ->
                                Column(Modifier.width(66.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(time, color = secondary, fontSize = 12.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Icon(Icons.Default.WbCloudy, null, tint = Orange, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("$temp°", color = primaryText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionTitle("توقعات 7 أيام", primaryText)
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                            w.daily.forEachIndexed { index, item ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.first, color = primaryText, modifier = Modifier.weight(1f))
                                    Icon(if (index % 3 == 0) Icons.Default.WbSunny else Icons.Default.Cloud, null, tint = Orange)
                                    Text("${item.second}°", color = primaryText, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                                    Text("${item.third}°", color = secondary, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                                }
                                if (index != w.daily.lastIndex) HorizontalDivider(color = if (dark) Color(0xFF383C46) else Color(0xFFEDEEF2))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("البيانات مقدمة مجانًا من Open-Meteo", color = secondary, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Orange, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(title: String, color: Color) {
    Text(title, color = color, fontSize = 19.sp, fontWeight = FontWeight.Bold)
}

fun conditionIcon(condition: String) = when {
    condition.contains("مطر") -> Icons.Default.Umbrella
    condition.contains("غائم") -> Icons.Default.Cloud
    condition.contains("ثلج") -> Icons.Default.AcUnit
    else -> Icons.Default.WbSunny
}

suspend fun fetchWeather(place: String): WeatherData = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(place, "UTF-8")
    val geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=ar&format=json")
    val result = geo.optJSONArray("results")?.optJSONObject(0) ?: error("City not found")
    val lat = result.getDouble("latitude")
    val lon = result.getDouble("longitude")
    val cityName = result.optString("name", place)
    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m&hourly=temperature_2m&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max&forecast_days=7&timezone=auto"
    val j = getJson(url)
    val current = j.getJSONObject("current")
    val hourly = j.getJSONObject("hourly")
    val times = hourly.getJSONArray("time")
    val temps = hourly.getJSONArray("temperature_2m")
    val hourlyList = mutableListOf<Pair<String, Int>>()
    for (i in 0 until minOf(12, times.length())) {
        val t = times.getString(i).substringAfter("T")
        hourlyList.add(t to temps.getDouble(i).toInt())
    }
    val daily = j.getJSONObject("daily")
    val dates = daily.getJSONArray("time")
    val max = daily.getJSONArray("temperature_2m_max")
    val min = daily.getJSONArray("temperature_2m_min")
    val days = mutableListOf<Triple<String, Int, Int>>()
    for (i in 0 until dates.length()) {
        val day = if (i == 0) "اليوم" else dates.getString(i).takeLast(5)
        days.add(Triple(day, max.getDouble(i).toInt(), min.getDouble(i).toInt()))
    }
    WeatherData(
        cityName, current.getDouble("temperature_2m").toInt(),
        current.getDouble("apparent_temperature").toInt(),
        weatherDescription(current.getInt("weather_code")),
        current.getInt("relative_humidity_2m"),
        current.getDouble("wind_speed_10m"),
        daily.getJSONArray("uv_index_max").optDouble(0, 0.0),
        hourlyList, days
    )
}

fun getJson(urlString: String): JSONObject {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.connectTimeout = 12000
    connection.readTimeout = 12000
    connection.requestMethod = "GET"
    return try {
        connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
    } finally { connection.disconnect() }
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "سماء صافية"
    1, 2 -> "غائم جزئيًا"
    3 -> "غائم"
    45, 48 -> "شبورة"
    in 51..67 -> "أمطار خفيفة"
    in 71..77 -> "ثلوج"
    in 80..82 -> "زخات مطر"
    in 95..99 -> "عواصف رعدية"
    else -> "حالة جوية متغيرة"
}
