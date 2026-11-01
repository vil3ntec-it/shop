package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 *  PAY-20 / RPT-12 — گزارشِ خودکار برای صاحبِ دکان: شکلِ تنظیم و نمای `GET /api/shop/owner-report`
 *  (`server/src/lib/owner-report.js`). خالص: نه شبکه، نه ساعت، نه دستگاه.
 *
 *  ⛔ داور سرور است: فقط صاحبِ دکان می‌نویسد (۴۰۳ `owner_only`)، پرچمِ `shop.owner_report` خاموش ⇒ `enabled = false` و
 *  هیچ بخشی دیده نمی‌شود. این‌جا فقط همان قاعده‌ها برای رابط: ساعتِ پیش از ظهر ⇒ گزارشِ **دیروز**، از ظهر به بعد ⇒
 *  **امروز** تا همان ساعت؛ دست‌کم یک کانال؛ روزِ هفته ۰ = یکشنبه … ۶ = شنبه (همان `getUTCDay` ِ سرور).
 *  واتس‌اپ در سرور سرویس‌دهنده ندارد ⇒ کانالِ انتخاب‌شدنی نیست.
 */
object OwnerReportPlan {
  const val DAILY = "daily"
  const val WEEKLY = "weekly"
  const val INAPP = "inapp"
  const val PUSH = "push"
  const val EMAIL = "email"
  val CHANNELS: List<String> = listOf(INAPP, PUSH, EMAIL)
  const val MORNING_BEFORE = 12

  /** ترتیبِ نمایشِ روزهای هفته: از شنبه */
  val WEEK_ORDER: List<Int> = listOf(6, 0, 1, 2, 3, 4, 5)
  private val DAY_NAMES = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه")

  data class Settings(
    val active: Boolean = false,
    val frequency: String = DAILY,
    val weekday: Int = 6,
    val hour: Int = 21,
    val channels: List<String> = listOf(INAPP),
    val version: Long = 0,
  )

  /** یک گزارشِ فرستاده‌شده — فقط عددهایی که سرور از ردیف‌های همگام‌شده ساخته؛ نبود ⇒ null، نه صفر */
  data class Report(
    val reportDate: String,
    val frequency: String,
    val status: String,
    val invoices: Long?,
    val netSalesMinor: Long?,
  )

  data class View(
    val enabled: Boolean = false,
    val owner: Boolean = false,
    val settings: Settings = Settings(),
    val emailAvailable: Boolean = false,
    val timezone: String = "",
    val reports: List<Report> = emptyList(),
  )

  fun weekdayName(day: Int): String = DAY_NAMES.getOrElse(day) { "" }

  fun hourLabel(hour: Int): String = hour.coerceIn(0, 23).toString().padStart(2, '0') + ":00"

  /** بازهٔ هر گزارش، همان قاعدهٔ `dueOf` ِ سرور */
  fun coverage(s: Settings): String {
    val morning = s.hour < MORNING_BEFORE
    val day = if (morning) "دیروز (کامل)" else "امروز تا ساعتِ ${hourLabel(s.hour)}"
    return if (s.frequency == WEEKLY) "هر ${weekdayName(s.weekday)} ساعتِ ${hourLabel(s.hour)}: هفت روزِ تا $day"
    else "هر روز ساعتِ ${hourLabel(s.hour)}: $day"
  }

  /** روشن/خاموش کردنِ یک کانال؛ آخرین کانال برداشته نمی‌شود (سرور دست‌کم یکی می‌خواهد). ترتیب همان [CHANNELS]. */
  fun toggleChannel(current: List<String>, channel: String): List<String> {
    if (channel !in CHANNELS) return current
    val next = if (channel in current) current - channel else current + channel
    return if (next.isEmpty()) current else CHANNELS.filter { it in next }
  }

  /** بدنهٔ `PUT /api/shop/owner-report` با نسخهٔ دیده‌شده (کهنه ⇒ ۴۰۹ `version_conflict`) */
  fun body(next: Settings, expectedVersion: Long): JsonObject = buildJsonObject {
    put("active", next.active)
    put("frequency", if (next.frequency == WEEKLY) WEEKLY else DAILY)
    put("weekday", next.weekday.coerceIn(0, 6))
    put("hour", next.hour.coerceIn(0, 23))
    putJsonArray("channels") { CHANNELS.filter { it in next.channels }.forEach { add(JsonPrimitive(it)) } }
    put("expectedVersion", expectedVersion)
  }

  private fun prim(o: JsonObject?, key: String): JsonPrimitive? = o?.get(key) as? JsonPrimitive
  private fun long(o: JsonObject?, key: String): Long? = prim(o, key)?.longOrNull
  private fun bool(o: JsonObject?, key: String): Boolean = prim(o, key)?.booleanOrNull == true
  private fun str(o: JsonObject?, key: String): String = prim(o, key)?.takeIf { it.isString }?.content.orEmpty()

  fun parseSettings(o: JsonObject?): Settings {
    val d = Settings()
    if (o == null) return d
    val channels = (o["channels"] as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    return Settings(
      active = bool(o, "active"),
      frequency = if (str(o, "frequency") == WEEKLY) WEEKLY else DAILY,
      weekday = long(o, "weekday")?.toInt()?.takeIf { it in 0..6 } ?: d.weekday,
      hour = long(o, "hour")?.toInt()?.takeIf { it in 0..23 } ?: d.hour,
      channels = CHANNELS.filter { it in channels }.ifEmpty { d.channels },
      version = long(o, "version") ?: 0,
    )
  }

  /** پاسخِ `GET` ⇒ نما. خاموش یا غیرِ صاحب ⇒ بی تنظیم و بی گزارش. */
  fun parse(o: JsonObject): View {
    if (!bool(o, "enabled")) return View()
    if (!bool(o, "owner")) return View(enabled = true)
    val reports = (o["reports"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { r ->
      val sum = r["summary"] as? JsonObject
      Report(str(r, "reportDate"), str(r, "frequency"), str(r, "status"), long(sum, "invoices"), long(sum, "netSales"))
    }
    return View(
      enabled = true, owner = true,
      settings = parseSettings(o["settings"] as? JsonObject),
      emailAvailable = bool(o["channels"] as? JsonObject, EMAIL),
      timezone = str(o, "timezone"),
      reports = reports,
    )
  }
}
