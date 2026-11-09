package ir.vil3ntec.tohid.sync.v1

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  «این تصمیمِ صندوق روی همین دستگاه نشست» — بیرون از دفتر، کنارِ دفترِ کنارِ صف (`oplog-<حساب>-settled.json`، v3:
 *  پوشهٔ `sync-v3-<حساب>`). DATA-SAFETY §۷.
 *
 *  ── اشکال ────────────────────────────────────────────────────────────
 *  تنها نشانِ «یک بار» ِ `SetAsideCommit` فهرستِ `ShopData.setAside` ِ خودِ دفتر بود. نسخهٔ پیش از دستهٔ ۳۱ آن کلید را
 *  نمی‌شناسد و با نخستین ذخیره از فایل می‌اندازد (و «بازسازی از سرور» هم دفترِ بی آن می‌سازد)؛ دفترِ کنارِ صف (opهای
 *  ردشده) ولی می‌ماند. پس از به‌روزرسانیِ دوباره همان تصمیم دوباره برنامه می‌گرفت و opهای برگرداننده **دوباره**
 *  می‌نشستند: `$inc` ِ وارونه دو بار (مقدارِ برگشته، پرداخت، تسویهٔ قرض) ⇒ موجودی و پولِ دستگاه با سرور فرق می‌کرد.
 *
 *  ── حالا ─────────────────────────────────────────────────────────────
 *  پس از هر نشستنِ موفق شناسهٔ تصمیم و opهایش این‌جا هم نوشته می‌شود (`DiskWrite`)؛ «یک بار» = دفتر ∪ این‌جا. نوشتن
 *  **پس از** دفتر است: بسته شدن میانِ آن دو ⇒ دفتر هنوز نشان دارد و دورِ بعد همین‌جا را کامل نمی‌کند ولی دوباره هم
 *  نمی‌نشاند. نسخهٔ کهنه این پرونده را نمی‌شناسد و دست نمی‌زند.
 *  ⛔ خواندنِ ناموفق «هیچ» است (همان رفتارِ پیش از این، فقط دفتر)، هرگز خطا در همگام‌سازی. فقط زیرِ قفلِ موتور نوشته
 *  می‌شود (`SetAsideCommit`)؛ دیسک از بیرون (`OpQueue.settledMarks`، `SyncV3Runner`) — همان `DiskWrite`.
 */
class SetAsideMarks(private val readText: () -> String?, private val writeText: (String) -> Unit) {

  data class Marks(val ids: Set<String> = emptySet(), val ops: Set<String> = emptySet())

  fun read(): Marks = runCatching {
    val o = Json.parseToJsonElement(readText() ?: return Marks()) as JsonObject
    fun set(k: String) = (o[k] as? JsonArray).orEmpty().mapNotNullTo(LinkedHashSet()) { (it as? JsonPrimitive)?.content }
    Marks(set("ids"), set("ops"))
  }.getOrDefault(Marks())

  /** پس از نشستنِ تصمیمِ [id] با opهای [ops] — شکست بی‌صداست (دفتر خودش نشان دارد) */
  fun add(id: String, ops: Collection<String>) {
    val cur = read()
    if (id in cur.ids && cur.ops.containsAll(ops)) return
    val next = JsonObject(mapOf(
      "ids" to JsonArray((cur.ids + id).map(::JsonPrimitive)),
      "ops" to JsonArray((cur.ops + ops).map(::JsonPrimitive)),
    ))
    runCatching { writeText(next.toString()) }
  }

  companion object {
    /** نامِ پرونده کنارِ `oplog-<کلید>-dropped.json` (همان جابه‌جاییِ `SyncV1Keys.move`) */
    fun v1Name(fileKey: String) = "oplog-$fileKey-settled.json"
    const val V3_NAME = "set-aside-settled.json"
  }
}
