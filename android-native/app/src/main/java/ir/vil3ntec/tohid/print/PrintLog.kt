package ir.vil3ntec.tohid.print

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 *  سابقهٔ چاپِ رسیدها روی همین دستگاه (HW-02).
 *
 *  دو کار دارد: بداند فاکتوری **پیش‌تر چاپ شده** تا چاپِ دوباره دلیل
 *  بخواهد، و چاپ‌های دوباره را با دلیل نشان دهد (صفحهٔ دستگاه‌ها).
 *
 *  ⚠️ فقط محلی است و به دفترِ همگام نمی‌رود — رفتنش به سابقهٔ عملیاتِ
 *  دکان (auditLog) تصمیمِ قالبِ داده است و در گزارش پیشنهاد شده.
 */
object PrintLog {

  data class Entry(val saleId: String, val kind: String, val reason: String, val at: Long, val ok: Boolean)

  private const val FILE = "print-log.json"
  private const val KEEP = 500

  private fun file(context: Context) = File(context.filesDir, FILE)

  @Synchronized
  fun all(context: Context): List<Entry> = runCatching {
    val f = file(context)
    if (!f.isFile) return emptyList()
    val arr = JSONArray(f.readText())
    (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      Entry(o.optString("saleId"), o.optString("kind"), o.optString("reason"), o.optLong("at"), o.optBoolean("ok"))
    }
  }.getOrDefault(emptyList())

  @Synchronized
  fun add(context: Context, e: Entry) {
    val next = (all(context) + e).takeLast(KEEP)
    val arr = JSONArray()
    next.forEach {
      arr.put(JSONObject().put("saleId", it.saleId).put("kind", it.kind).put("reason", it.reason).put("at", it.at).put("ok", it.ok))
    }
    runCatching { file(context).writeText(arr.toString()) }
  }

  /** این فاکتور پیش‌تر روی کاغذ آمده؟ */
  fun wasPrinted(context: Context, saleId: String): Boolean = all(context).any { it.saleId == saleId && it.ok }

  /** چاپ‌های دوباره، تازه‌ترین اول */
  fun reprints(context: Context, limit: Int = 20): List<Entry> =
    all(context).filter { it.kind == "reprint" }.sortedByDescending { it.at }.take(limit)
}
