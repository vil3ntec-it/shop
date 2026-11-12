package ir.vil3ntec.tohid.core.text

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  فهرستِ مرکزیِ مترادف (POS-04، ۴-۱ «ب») آن‌گونه که برنامه نگه می‌دارد: نسخهٔ سرور و گروه‌های واژه.
 *  گروه‌ها همان شکلِ `TextNormalizer.matches(…, synonyms)` و `text-core.matches` ِ سرورند؛ تهی = جست‌وجوی پیش از POS-04.
 */
data class SynonymTable(val version: Long, val groups: List<List<String>>) {
  companion object {
    val EMPTY = SynonymTable(0L, emptyList())
  }
}

/**
 *  خواندنِ `GET /api/search/synonyms` (بی‌توکن؛ نوشتن فقط پنلِ مرکزی) و قاعدهٔ «کی دوباره بپرسیم» — همان الگوی
 *  `core/calendar/Holidays` (LOC-04).
 *
 *  سرور همان سنجش را پیش از نوشتن دارد (`server/src/lib/search-synonyms.js` ‹`validate`›: تا ۵۰۰ گروه، ۲ تا ۲۰ واژه، هر
 *  واژه ۱ تا ۶۰ نویسه)؛ این‌جا فقط سپر است: سندِ بدشکل (نسخهٔ نادرست، `groups` ِ نافهرست) ⇒ «فهرستی نیست» (`null` از [of]،
 *  نسخهٔ ذخیره‌شده می‌ماند)، واژهٔ نادرست کنار و گروهِ کمتر از دو واژه کنار؛ هرگز پرتاب.
 */
object SynonymList {

  /** همان سقف‌های سرور (`GROUPS_MAX`، `TERMS_MAX`، `TERM_MAX`) */
  const val GROUPS_MAX: Int = 500
  const val TERMS_MAX: Int = 20
  const val TERM_MAX: Int = 60

  /** روزی یک بار (همان تعطیلات)؛ پاسخِ گم‌شده یا بی‌شبکه ⇒ نسخهٔ ذخیره‌شده می‌ماند و ساعتِ بعد دوباره */
  const val REFRESH_MS: Long = 24L * 60 * 60 * 1000

  /** فاصلهٔ سنجیدنِ [due] در برنامهٔ باز */
  const val CHECK_MS: Long = 60L * 60 * 1000

  /** متنِ ذخیره‌شده یا رسیده ⇒ فهرست؛ هر خرابی ⇒ [SynonymTable.EMPTY] */
  fun parse(text: String?): SynonymTable {
    val raw = text?.takeIf { it.isNotBlank() } ?: return SynonymTable.EMPTY
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: return SynonymTable.EMPTY
    return of(root) ?: SynonymTable.EMPTY
  }

  /** پاسخِ سرور ⇒ فهرست؛ سندِ بدشکل ⇒ `null` */
  fun of(root: JsonObject): SynonymTable? {
    val version = versionOf(root["version"] as? JsonPrimitive) ?: return null
    val list = root["groups"] as? JsonArray ?: return null
    val groups = ArrayList<List<String>>()
    for (row in list.take(GROUPS_MAX)) {
      val terms = (row as? JsonArray ?: continue).take(TERMS_MAX).mapNotNull { t ->
        (t as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() && it.length <= TERM_MAX }
      }.distinct()
      if (terms.size >= 2) groups += terms
    }
    return SynonymTable(version, groups)
  }

  /** عددِ صحیحِ نامنفی (`2.0` هم)؛ متن، بولی، کسری و منفی ⇒ `null` */
  private fun versionOf(p: JsonPrimitive?): Long? {
    if (p == null || p.isString) return null
    val whole = p.longOrNull ?: p.doubleOrNull?.takeIf { it % 1.0 == 0.0 && it <= 9.007199254740991E15 }?.toLong()
    return whole?.takeIf { it >= 0L }
  }

  /** نسخهٔ ذخیره‌شدنی: فقط نسخه و گروه‌های درست. `parse(encode(t)) == t` برای هر فهرستِ خوانده‌شده. */
  fun encode(table: SynonymTable): String = buildJsonObject {
    put("version", table.version)
    put("groups", buildJsonArray { for (g in table.groups) add(buildJsonArray { for (t in g) add(JsonPrimitive(t)) }) })
  }.toString()

  /** وقتِ پرسیدنِ دوباره: هرگز گرفته نشده، یک روز گذشته، یا ساعتِ دستگاه عقب رفته */
  fun due(lastFetchMs: Long, nowMs: Long): Boolean =
    lastFetchMs <= 0L || nowMs < lastFetchMs || nowMs - lastFetchMs >= REFRESH_MS
}
