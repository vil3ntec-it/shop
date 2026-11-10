package ir.vil3ntec.tohid.core.calendar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 *  جدولِ تعطیلاتِ رسمی (LOC-04) آن‌گونه که برنامه نگه می‌دارد: نسخهٔ سرور و روزها (`YYYY-MM-DD` ِ میلادی).
 *  روزها همان `WeekRules.holidays` اند؛ تهی = هیچ تعطیلِ رسمی = رفتارِ پیش از LOC-04.
 */
data class HolidayTable(val version: Long, val dates: Set<String>) {
  companion object {
    val EMPTY = HolidayTable(0L, emptySet())
  }
}

/**
 *  خواندنِ جدولِ تعطیلاتِ سرور (`GET /api/calendar/holidays`، بی‌توکن) و قاعدهٔ «کی دوباره بپرسیم».
 *
 *  تعطیلاتِ قمری با رؤیتِ ماه اعلام می‌شوند و هیچ برنامه‌ای جدول را ثابت ندارد (`server/src/lib/holidays.js`).
 *  [parse] همان قاعدهٔ `parse` ِ سرور است، با بردارِ مشترکِ `core/test-vectors/calendar-holidays-v1.json`
 *  (`HolidayVectorsTest` و `server/test/holidays.test.js`): نسخهٔ نادرست یا `holidays` ِ نافهرست ⇒ «جدولی نیست»
 *  (`null` از [of])، ردیفِ نادرست کنار و بقیه می‌مانند. پاسخِ سرور هرگز به خودیِ خود جای تنظیمِ دکان را نمی‌گیرد:
 *  فقط روزی را «تعطیلِ رسمی» می‌کند (`BusinessDay.dayKind` ⇒ `HOLIDAY`).
 */
object Holidays {

  /** همان سقفِ سرور (`MAX_ENTRIES`): ردیف‌های پس از آن خوانده نمی‌شوند */
  const val MAX_ENTRIES: Int = 500

  /** روزی یک بار (§۶.۲۳)؛ پاسخِ گم‌شده یا بی‌شبکه ⇒ نسخهٔ ذخیره‌شده می‌ماند و ساعتِ بعد دوباره */
  const val REFRESH_MS: Long = 24L * 60 * 60 * 1000

  /** فاصلهٔ سنجیدنِ [due] در برنامهٔ باز */
  const val CHECK_MS: Long = 60L * 60 * 1000

  private const val MIN_DATE = "2000-01-01"
  private const val MAX_DATE = "2199-12-31"

  /** تاریخِ میلادیِ واقعی در بازهٔ سرور (۲۰۰۰ تا ۲۱۹۹)؛ رقمِ فارسی، فاصله و خورشیدیِ اشتباهی نه */
  fun isDate(iso: String): Boolean = CivilDays.jdnOfIso(iso) != null && iso >= MIN_DATE && iso <= MAX_DATE

  /** متنِ ذخیره‌شده یا رسیده ⇒ جدول؛ هر خرابی ⇒ [HolidayTable.EMPTY]، هرگز پرتاب */
  fun parse(text: String?): HolidayTable {
    val raw = text?.takeIf { it.isNotBlank() } ?: return HolidayTable.EMPTY
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return HolidayTable.EMPTY
    return of(root) ?: HolidayTable.EMPTY
  }

  /** پاسخِ سرور ⇒ جدول؛ سندِ بدشکل ⇒ `null` (صدازننده نسخهٔ ذخیره‌شده را نگه می‌دارد) */
  fun of(root: JsonObject): HolidayTable? {
    val version = versionOf(root["version"] as? JsonPrimitive) ?: return null
    val list = root["holidays"] as? JsonArray ?: return null
    val dates = LinkedHashSet<String>()
    for (row in list.take(MAX_ENTRIES)) {
      val date = ((row as? JsonObject)?.get("date") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
      if (isDate(date)) dates += date
    }
    return HolidayTable(version, dates)
  }

  /** عددِ صحیحِ نامنفی (`2.0` هم، مثلِ `Number.isInteger` ِ سرور)؛ متن، بولی، کسری و منفی ⇒ `null` */
  private fun versionOf(p: JsonPrimitive?): Long? {
    if (p == null || p.isString) return null
    val whole = p.longOrNull ?: p.doubleOrNull?.takeIf { it % 1.0 == 0.0 && it <= 9.007199254740991E15 }?.toLong()
    return whole?.takeIf { it >= 0L }
  }

  /**
   *  نسخهٔ ذخیره‌شدنیِ کوچک: فقط نسخه و روزهای درست (نام برای روزِ کاری بی‌اثر است)، نه متنِ خامِ پاسخ — پاسخِ بزرگ یا
   *  پرفیلد روی دستگاه نمی‌ماند. `parse(encode(t)) == t` برای هر جدولِ خوانده‌شده.
   */
  fun encode(table: HolidayTable): String =
    table.dates.filter { isDate(it) }.joinToString(",", prefix = "{\"version\":${table.version},\"holidays\":[", postfix = "]}") {
      "{\"date\":\"$it\"}"
    }

  /** وقتِ پرسیدنِ دوباره: هرگز گرفته نشده، یک روز گذشته، یا ساعتِ دستگاه عقب رفته */
  fun due(lastFetchMs: Long, nowMs: Long): Boolean =
    lastFetchMs <= 0L || nowMs < lastFetchMs || nowMs - lastFetchMs >= REFRESH_MS
}
