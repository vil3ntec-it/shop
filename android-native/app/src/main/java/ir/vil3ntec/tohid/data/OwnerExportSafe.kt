package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  گاوصندوق در خروجیِ اکسلِ صاحب (`OwnerExport.sheets`) — جدا تا `OwnerExport.kt` بزرگ نشود.
 *
 *  • برگهٔ «گاوصندوق»: هر ردیف ستونِ «ارز» دارد، درست پس از مبلغ؛ ردیفِ بی `currency` همان «AFN» نوشته می‌شود (نبودن =
 *    افغانی، `core/safe/SafeCurrency`). فقط برگهٔ اکسل — JSON ِ خروجی همان دفتر می‌ماند (فایلِ «بازیابی» عوض نشود).
 *  • برگهٔ «گاوصندوق — مانده به تفکیکِ ارز»: یک ردیف برای هر ارز با ماندهٔ **همان ارز** از `SafeCore.views` (همان عددِ
 *    برنامه) — هرگز جمعِ دو ارز، هرگز تبدیل. دفترِ بی ردیفِ گاوصندوق ⇒ این برگه نیست.
 *  نسخهٔ وبِ همین خروجی (`license/owner-export.js`، بی صفحه از وقتی وبِ مدیریت برداشته شد) فقط ستونِ «ارز» را دارد.
 */
internal object OwnerExportSafe {

  const val SHEET = "گاوصندوق — مانده به تفکیکِ ارز"  // داده: برگه و ستونِ خروجیِ کاملِ صاحب

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /** هر ردیف با ستونِ `currency` پس از `amountMinor` (بی آن ⇒ «AFN») */
  fun rows(arr: JsonArray): JsonArray = JsonArray(
    arr.map { e ->
      val o = e as? JsonObject ?: return@map e
      val cur = o["currency"]?.takeIf { it !is JsonNull } ?: JsonPrimitive(SafeCurrency.AFN)
      val out = LinkedHashMap<String, JsonElement>()
      for ((k, v) in o) {
        if (k == "currency") continue
        out[k] = v
        if (k == "amountMinor") out["currency"] = cur
      }
      if ("currency" !in out) out["currency"] = cur
      JsonObject(out)
    },
  )

  /** جمعِ هر ارز جدا: ارز، مانده، مانده به واحدِ کوچک، شمارِ ردیف‌ها و ردیف‌های کنارگذاشته */
  fun totals(arr: JsonArray): List<List<Xlsx.Cell?>>? {
    val entries = arr.mapNotNull { e ->
      runCatching { SafeBook.toCore(json.decodeFromJsonElement(SafeEntry.serializer(), e)) }.getOrNull()
    }
    if (entries.isEmpty()) return null
    fun t(s: String) = Xlsx.Cell.Text(s)
    fun n(v: Number) = Xlsx.Cell.Num(v.toDouble())
    val rows = ArrayList<List<Xlsx.Cell?>>()
    rows += listOf(t("ارز"), t("مانده"), t("مانده (واحدِ کوچکِ همان ارز)"), t("ردیف‌ها"), t("ردیف‌های کنارگذاشته"))  // داده: برگه و ستونِ خروجیِ کاملِ صاحب
    for ((code, v) in SafeCore.views(entries)) {
      rows += listOf(t(code), n(SafeCurrency.major(v.balanceMinor, code)), n(v.balanceMinor), n(v.history.size), n(v.invalid.size))
    }
    return rows
  }
}
