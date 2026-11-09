package ir.vil3ntec.tohid.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 *  DATA-SAFETY — کلیدهای دفترِ نسخهٔ **تازه‌تر** که این نسخه نمی‌شناسد، با برگشت به این نسخه (downgrade) دور ریخته نمی‌شوند
 *  (`docs/DATA-SAFETY-fa.md` §۷، «جدولِ سازگاری»).
 *
 *  ── اشکال ────────────────────────────────────────────────────────────
 *  دفتر از درِ مدل خوانده می‌شود (`ignoreUnknownKeys`)؛ نخستین ذخیره پس از برگشت به نسخهٔ کهنه‌تر هر فیلدِ تازه‌تر
 *  (و هر کلیدِ تازه‌ترِ سرِ دفتر) را از فایل پاک می‌کرد. دستگاهِ همگام‌شده نسخه‌اش را روی سرور دارد (A00: نسخهٔ کهنه
 *  کلیدی را که نمی‌شناسد `null` نمی‌کند)، ولی دفترِ دستگاهِ **هرگز همگام‌نشده** تنها نسخه بود: با به‌روزرسانیِ دوباره
 *  برنمی‌گشت.
 *
 *  ── حالا ─────────────────────────────────────────────────────────────
 *  [scan] (فقط وقتی خواندنِ سخت‌گیر کلیدِ ناشناخته دید — [strictOf]؛ دفترِ همین نسخه هیچ هزینهٔ تازه‌ای ندارد) کلیدهای
 *  ناشناختهٔ سرِ دفتر و ردیف‌هایی را که کلیدِ ناشناخته دارند (با شناسه) خام نگه می‌دارد؛ هر نوشتن همان‌ها را کنارِ
 *  رمزگذاریِ مدل می‌گذارد ([skeleton]، [row]). قاعده‌ها:
 *   • کلیدی که این نسخه می‌شناسد (با `@JsonNames`) همیشه از مدل است — حتی اگر مدل آن را ننوشت (`null` ِ `NEVER`)؛
 *   • ردیف با شناسه جور می‌شود؛ ردیفِ حذف‌شده کلیدهایش را هم می‌برد؛ ردیفِ ویرایش‌شده کلیدِ ناشناخته‌اش را نگه می‌دارد
 *     (همان ادغامِ فیلدبه‌فیلدِ سرور)؛
 *   • عنصرِ فهرستِ تودرتوی بی شناسه (`payments`، `purchaseUnits`…) فقط وقتی کلیدِ ناشناخته‌اش را نگه می‌دارد که همان
 *     جا و همان محتوای شناخته را دارد (عوض‌شده ⇒ کلیدِ کهنه نمی‌چسبد).
 *  ⛔ فقط برای دستگاهِ **هرگز همگام‌نشده** (`ShopStore.localOnly`): برای دستگاهِ همگام سرور مرجع است و کلیدِ کهنه‌ای که
 *     این‌جا بماند پس از به‌روزرسانیِ دوباره در تفاضل «تغییر» دیده می‌شد و مقدارِ تازه‌ترِ سرور را رونویسی می‌کرد.
 *  ⛔ هیچ خطایی این‌جا خواندن یا نوشتنِ دفتر را نمی‌اندازد: [scan] ِ ناموفق ⇒ `null` = همان رفتارِ پیشین.
 */
class LedgerExtras private constructor(
  /** کلیدهای ناشناختهٔ سرِ دفتر، و کلیدِ شناخته‌ای (جز جدول‌های ردیفی) که درونش کلیدِ ناشناخته هست — خام */
  private val top: JsonObject,
  /** جدول ⇒ شناسه ⇒ ردیفِ خام (فقط ردیف‌هایی که کلیدِ ناشناخته دارند) */
  private val rows: Map<String, Map<String, JsonObject>>,
) {

  /** چند کلید/ردیف نگه داشته شده — برای آزمون و گزارش */
  val size: Int get() = top.size + rows.values.sumOf { it.size }

  fun hasRows(table: String): Boolean = rows[table]?.isNotEmpty() == true

  /** متنِ دفترِ بی ردیفِ [bare] (`LedgerTables.bare`) با کلیدهای نگه‌داشتهٔ سرِ دفتر */
  fun skeleton(json: Json, bare: ShopData): String {
    val enc = json.encodeToJsonElement(ShopData.serializer(), bare) as JsonObject
    return if (top.isEmpty()) enc.toString() else merge(top, enc, ShopData.serializer().descriptor).toString()
  }

  /** متنِ یک ردیفِ [table] با کلیدهای نگه‌داشته‌اش؛ `null` ⇒ ردیفی بی کلیدِ نگه‌داشته (همان رمزگذارِ همیشه) */
  fun <T> row(json: Json, table: String, serializer: KSerializer<T>, value: T, id: String): String? {
    val raw = rows[table]?.get(id) ?: return null
    return merge(raw, json.encodeToJsonElement(serializer, value), serializer.descriptor).toString()
  }

  /** کلِ دفتر — «رمزگذاریِ داده» (`AtRest.writeText`) و پشتیبانِ بیرونی */
  fun text(json: Json, d: ShopData): String = tree(json, d).toString()

  fun tree(json: Json, d: ShopData): JsonObject {
    val enc = json.encodeToJsonElement(ShopData.serializer(), d) as JsonObject
    val out = LinkedHashMap<String, JsonElement>(merge(top, enc, ShopData.serializer().descriptor) as JsonObject)
    for (t in LedgerTables.ALL) {
      val raw = rows[t.key] ?: continue
      val list = out[t.key] as? JsonArray ?: continue
      out[t.key] = JsonArray(list.map { r -> idOf(r)?.let(raw::get)?.let { merge(it, r, t.serializer.descriptor) } ?: r })
    }
    return JsonObject(out)
  }

  companion object {
    private val TABLES: Map<String, LedgerTables.Table<*>> = LedgerTables.ALL.associateBy { it.key }

    @Volatile private var strict: Pair<Json, Json>? = null

    /** همان [json] ولی کلیدِ ناشناخته ⇒ خطا: خواندنِ نخست، تا دفترِ بی کلیدِ تازه‌تر یک بار و همان‌طور خوانده شود */
    fun strictOf(json: Json): Json {
      strict?.let { (a, b) -> if (a === json) return b }
      return Json(json) { ignoreUnknownKeys = false }.also { strict = json to it }
    }

    /** کلیدهای ناشناختهٔ متنِ دفتر؛ هیچ ⇒ `null`. هر خطا (حتی کمبودِ حافظه) ⇒ `null` = رفتارِ پیشین */
    fun scan(text: String, json: Json): LedgerExtras? = runCatching {
      val tree = json.parseToJsonElement(text) as? JsonObject ?: return null
      val desc = ShopData.serializer().descriptor
      val known = knownKeys(desc)
      val top = LinkedHashMap<String, JsonElement>()
      val rows = HashMap<String, Map<String, JsonObject>>()
      for ((k, v) in tree) {
        val t = TABLES[k]
        if (t != null) {
          val rowDesc = t.serializer.descriptor
          val keep = HashMap<String, JsonObject>()
          (v as? JsonArray)?.forEach { r -> if (r is JsonObject && unknownInside(r, rowDesc)) idOf(r)?.let { keep[it] = r } }
          if (keep.isNotEmpty()) rows[k] = keep
        } else if (k !in known) {
          top[k] = v
        } else {
          val i = desc.getElementIndex(k)
          if (i >= 0 && unknownInside(v, desc.getElementDescriptor(i))) top[k] = v
        }
      }
      if (top.isEmpty() && rows.isEmpty()) null else LedgerExtras(JsonObject(top), rows)
    }.getOrNull()

    private fun idOf(r: JsonElement): String? = ((r as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content

    @OptIn(ExperimentalSerializationApi::class)
    private fun knownKeys(d: SerialDescriptor): Set<String> = buildSet {
      for (i in 0 until d.elementsCount) {
        add(d.getElementName(i))
        d.getElementAnnotations(i).filterIsInstance<JsonNames>().forEach { addAll(it.names) }
      }
    }

    private fun isClass(d: SerialDescriptor) = d.kind == StructureKind.CLASS || d.kind == StructureKind.OBJECT

    /** نامِ کلید ⇒ جای عنصر (نامِ اصلی یا `@JsonNames`)؛ نبود ⇒ -1 */
    @OptIn(ExperimentalSerializationApi::class)
    private fun indexOf(d: SerialDescriptor, key: String): Int {
      val i = d.getElementIndex(key)
      if (i >= 0) return i
      for (j in 0 until d.elementsCount) if (d.getElementAnnotations(j).filterIsInstance<JsonNames>().any { key in it.names }) return j
      return -1
    }

    /** آیا جایی درونِ [v] (به شکلِ [d]) کلیدی هست که این نسخه نمی‌شناسد؟ */
    private fun unknownInside(v: JsonElement, d: SerialDescriptor): Boolean = when {
      isClass(d) && v is JsonObject -> v.entries.any { (k, x) -> indexOf(d, k).let { i -> i < 0 || unknownInside(x, d.getElementDescriptor(i)) } }
      d.kind == StructureKind.LIST && v is JsonArray && d.elementsCount > 0 -> d.getElementDescriptor(0).let { e -> v.any { unknownInside(it, e) } }
      else -> false
    }

    /** [raw] بی کلیدهای ناشناخته — برای سنجیدنِ «همان محتوای شناخته» ِ عنصرِ بی شناسه */
    private fun known(raw: JsonElement, d: SerialDescriptor): JsonElement = when {
      isClass(d) && raw is JsonObject -> JsonObject(raw.mapNotNull { (k, x) -> indexOf(d, k).takeIf { it >= 0 }?.let { k to known(x, d.getElementDescriptor(it)) } }.toMap())
      d.kind == StructureKind.LIST && raw is JsonArray && d.elementsCount > 0 -> JsonArray(raw.map { known(it, d.getElementDescriptor(0)) })
      else -> raw
    }

    /** رمزگذاریِ مدل ([enc]) + کلیدهای ناشناختهٔ [raw]؛ کلیدِ شناخته همیشه از [enc] */
    internal fun merge(raw: JsonElement, enc: JsonElement, d: SerialDescriptor): JsonElement = when {
      isClass(d) && raw is JsonObject && enc is JsonObject -> {
        val out = LinkedHashMap<String, JsonElement>(enc)
        for ((k, v) in raw) {
          val i = indexOf(d, k)
          if (i < 0) { if (k !in out) out[k] = v; continue }
          val mine = out[k] ?: continue
          out[k] = merge(v, mine, d.getElementDescriptor(i))
        }
        JsonObject(out)
      }
      d.kind == StructureKind.LIST && raw is JsonArray && enc is JsonArray && d.elementsCount > 0 -> {
        val e = d.getElementDescriptor(0)
        if (!isClass(e)) enc
        else if (e.getElementIndex("id") >= 0) {
          val byId = raw.mapNotNull { r -> idOf(r)?.let { it to r } }.toMap()
          JsonArray(enc.map { x -> idOf(x)?.let(byId::get)?.let { merge(it, x, e) } ?: x })
        } else if (raw.size == enc.size) {
          JsonArray(enc.mapIndexed { i, x -> if (known(raw[i], e) == known(x, e)) merge(raw[i], x, e) else x })
        } else enc
      }
      else -> enc
    }
  }
}
