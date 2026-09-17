package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 *  ══ پشتیبانِ ابری — «گوشی که رفت، دفتر نرود» ══════════════════════
 *
 *  ── چه چیزی نبود ───────────────────────────────────────────────────
 *  `AutoBackup` هر شب سه نسخه روی **خودِ گوشی** می‌گذارد و
 *  `BackupBundle` یک ZIP برای بیرون بردنِ دستی می‌سازد. هر دو خوب‌اند و
 *  سرِ جایشان می‌مانند — ولی هیچ‌کدام از دستِ **گم شدنِ گوشی** نجات
 *  نمی‌دهد، و آن یکی هم به حافظهٔ آدمی بسته است که سرش شلوغ است.
 *
 *  حالا همان ZIP به سرور هم می‌رود و فهرستش در خودِ برنامه دیده
 *  می‌شود — فهرستِ **همین دکان**، نه چیزِ دیگری.
 *
 *  ── سه قاعده ───────────────────────────────────────────────────────
 *
 *  ⛔ **خواندن و برگرداندن هیچ‌وقت قفل نمی‌شود.** دادهٔ دکان‌دار مالِ
 *     خودش است؛ اشتراکِ تمام‌شده نباید بینِ او و پشتیبانش دیوار بکشد.
 *     (این قاعده روی سرور اجرا می‌شود، این‌جا فقط تکرار نمی‌شود.)
 *
 *  ⛔ **بی‌اینترنت هیچ‌چیزی نمی‌شکند.** نرفتنِ پشتیبان یک خطا نیست، یک
 *     «دفعهٔ بعد» است — نسخهٔ محلی سرِ جایش می‌ماند.
 *
 *  ⚠️ **سهم دو پله دارد** و سرور تصمیمش را می‌گیرد، نه برنامه. حسابِ
 *     بی‌اشتراک جای کمتری دارد؛ همان را `Stats` می‌گوید تا کاربر
 *     غافلگیر نشود.
 */
class BackupRepository(private val api: ApiClient) {

  data class Item(
    val id: String,
    val name: String,
    val bytes: Long,
    val kind: String,
    val label: String,
    val appVersion: String,
    val createdAt: Long,
  )

  data class Stats(
    val count: Int = 0,
    val keep: Int = 0,
    val usedBytes: Long = 0,
    val quotaBytes: Long = 0,
    val maxBytes: Long = 0,
    val lastAt: Long = 0,
    val paid: Boolean = false,
  )

  data class Listing(
    val items: List<Item> = emptyList(),
    val stats: Stats = Stats(),
  )

  /** فهرستِ پشتیبان‌های همین دکان. */
  suspend fun list(): ApiResult<Listing> = result {
    val body = api.get(ApiEndpoints.Backups.ROOT)
    Listing(
      items = (body["backups"] as? JsonArray).orEmpty().mapNotNull { item(it as? JsonObject) },
      stats = stats(body["stats"] as? JsonObject),
    )
  }

  /**
   *  فرستادنِ یک پشتیبانِ تازه.
   *
   *  @param bytes خودِ فایل — همان ZIPی که `BackupBundle` می‌سازد
   *  @param manual «دستی» یعنی کاربر خودش دکمه را زده، نه کارِ شبانه
   *
   *  ⚠️ `ext` به سرور می‌گوید پسوندِ فایل چه باشد. نامِ فایل را **سرور**
   *  می‌سازد، نه ما: نامی که از گوشی بیاید می‌تواند `../` داشته باشد.
   */
  suspend fun upload(
    bytes: ByteArray,
    label: String = "",
    manual: Boolean = false,
    appVersion: String = "",
    ext: String = "zip",
  ): ApiResult<Item> = result {
    val path = ApiEndpoints.withQuery(
      ApiEndpoints.Backups.ROOT,
      mapOf("ext" to ext, "kind" to (if (manual) "manual" else "auto"), "label" to label),
    )
    val body = api.postBytes(
      path,
      bytes,
      headers = if (appVersion.isBlank()) emptyMap() else mapOf("X-App-Version" to appVersion),
    )
    item(body["backup"] as? JsonObject) ?: error("پاسخِ سرور شکلِ درستی نداشت")
  }

  suspend fun remove(id: String): ApiResult<Unit> = result {
    api.delete(ApiEndpoints.Backups.one(id))
    Unit
  }

  private fun item(o: JsonObject?): Item? {
    o ?: return null
    val id = ApiJson.text(o, "id")
    if (id.isBlank()) return null
    return Item(
      id = id,
      name = ApiJson.text(o, "name"),
      bytes = ApiJson.long(o, "bytes", 0),
      kind = ApiJson.text(o, "kind"),
      label = ApiJson.text(o, "label"),
      appVersion = ApiJson.text(o, "appVersion"),
      createdAt = ApiJson.long(o, "createdAt", 0),
    )
  }

  private fun stats(o: JsonObject?): Stats {
    o ?: return Stats()
    return Stats(
      count = ApiJson.int(o, "count", 0),
      keep = ApiJson.int(o, "keep", 0),
      usedBytes = ApiJson.long(o, "usedBytes", 0),
      quotaBytes = ApiJson.long(o, "quotaBytes", 0),
      maxBytes = ApiJson.long(o, "maxBytes", 0),
      lastAt = ApiJson.long(o, "lastAt", 0),
      paid = ApiJson.bool(o, "paid", false),
    )
  }

  private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
}
