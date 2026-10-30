package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.round

/**
 *  ادغامِ سه‌طرفهٔ دفتر — وقتی دفترِ دیسک از دفتری که صفحه با آن ساخت جلوتر است.
 *
 *  ── چرا ──────────────────────────────────────────────────────────
 *  صفحه و کارگرِ پس‌زمینه (`SyncV1Worker`، `SyncV3Runner`) هر کدام نمونهٔ خودشان از
 *  `ShopStore` را دارند. کارگر opهای سرور را روی دیسک می‌نشاند و سایه را جلو می‌برد؛ صفحه
 *  هنوز دفترِ پیش از آن را در دست دارد. پیش‌تر ذخیرهٔ بعدیِ صفحه همان دفترِ کهنه را کامل
 *  می‌نوشت و `record` تفاضلش را با سایهٔ تازه می‌گرفت: ردیفی که کارگر آورده بود DELETE
 *  می‌شد و ویرایشِ دستگاهِ دیگر برمی‌گشت — روی همهٔ دستگاه‌ها.
 *
 *  ── قاعده ────────────────────────────────────────────────────────
 *    پایه    = دفتری که صفحه با آن ساخت ([baseOf])
 *    مالِ ما = دفترِ تازهٔ صفحه
 *    مالِ آن‌ها = دفترِ همین لحظهٔ دیسک
 *
 *  فقط آنچه صفحه نسبت به پایه عوض کرد روی دیسک می‌نشیند؛ بقیه همان دیسک است. پس `record`
 *  فقط برای تغییرِ واقعیِ کاربر op می‌سازد.
 *
 *    • ردیف: افزودهٔ هر طرف می‌ماند. حذف همیشه می‌برد — حذفِ کاربر DELETE می‌سازد و ردیفی که
 *      دستگاهِ دیگر حذف کرد با ویرایشِ کهنهٔ صفحه زنده نمی‌شود (همان قاعدهٔ v3: «ویرایشِ
 *      قدیمی‌تر از حذف ردیف را زنده نمی‌کند»، و همان کاری که `applyRemote` با دفتر می‌کند).
 *    • فیلد: فقط یک طرف عوض کرد ⇒ همان؛ هر دو، یکسان ⇒ همان؛ هر دو، متفاوت ⇒ کاربر برنده.
 *    • شمارنده‌ها (`LedgerDiff.INC_FIELDS`: پولِ رسیده، مانده، مقدارِ برگشتی) جمع‌پذیرند:
 *      دیسک + (مالِ ما − پایه). «کاربر برنده» این‌جا یعنی پرداختِ دستگاهِ دیگر گم شود.
 *    • فهرست‌های نام (`LIST_COLLECTIONS`): افزوده و حذفِ هر طرف.
 *    • شمارهٔ فاکتورِ بعدی: هر دو جلو رفته ⇒ بزرگ‌تر (شماره هرگز تکرار نشود).
 *
 *  بی `Context` تا آزمون‌پذیر باشد (`LedgerMergeTest`)؛ فراخوان قفلِ نوشتن را دارد.
 */
object LedgerMerge {

  fun merge(base: ShopData, ours: ShopData, theirs: ShopData): ShopData {
    if (theirs === base || ours === base) return if (ours === base) theirs else ours
    val b = LedgerDiff.toJson(base)
    val o = LedgerDiff.toJson(ours)
    val t = LedgerDiff.toJson(theirs)
    //  دیسک همان پایه است (مثلاً همان دفتر از نو رمز/خوانده شد) ⇒ همان دفترِ صفحه، بی شیءِ تازه
    if (t == b) return ours
    if (o == b) return theirs
    val out = LinkedHashMap<String, JsonElement>()
    for (key in LinkedHashSet(o.keys + t.keys)) {
      val v = when (key) {
        in LedgerDiff.COLLECTIONS -> rows(key, b[key], o[key], t[key])
        in LedgerDiff.LIST_COLLECTIONS -> names(b[key], o[key], t[key])
        INVOICE_COUNTER -> counter(b[key], o[key], t[key])
        else -> if (o[key] == b[key]) t[key] else o[key]
      }
      if (v != null) out[key] = v
    }
    return LedgerDiff.fromJson(JsonObject(out))
  }

  /**
   *  دفتری که [next] از آن ساخته شد.
   *
   *  صفحه دفتر را با `copy` عوض می‌کند، پس ردیف‌ها و فهرست‌هایی که دست نخورده‌اند **همان
   *  شیء**اند. [earlier] دفترهایی است که پیش از جایگزینی از بیرون (نوشتنِ همتا، خواندنِ دوبارهٔ
   *  دیسک، دفترِ ادغام‌شده) روی صفحه بودند؛ هر کدام از خواندنِ JSON ِ جدا آمده‌اند و هیچ شیئی با
   *  هم ندارند. پس دفتری که بیشترین ردیفِ مشترک (همان شیء) را با [next] دارد پایه است؛ برابری یا
   *  هیچ نشانه ⇒ [current] (رفتارِ همیشه).
   */
  fun baseOf(next: ShopData, current: ShopData, earlier: List<ShopData>): ShopData {
    if (earlier.isEmpty()) return current
    var best = current
    var score = kinship(next, current)
    for (c in earlier.asReversed()) {
      if (c === current) continue
      val s = kinship(next, c)
      if (s > score) { best = c; score = s }
    }
    return best
  }

  /**
   *  چند فهرست و ردیفِ [a] همان شیءِ [b] است. فهرستِ همان شیء — حتی خالی — هم نشانه است: هر خواندنِ
   *  JSON فهرستِ تازه می‌سازد، پس فقط دفتری که [a] با `copy` از آن ساخته شد آن را دارد.
   */
  internal fun kinship(a: ShopData, b: ShopData): Long {
    var n = 0L
    for (f in LISTS) {
      val x = f(a)
      val y = f(b)
      if (x === y) { n += 1 + x.size; continue }
      if (x.isEmpty() || y.isEmpty()) continue
      val mine: MutableSet<Any?> = Collections.newSetFromMap(IdentityHashMap<Any?, Boolean>(y.size))
      mine.addAll(y)
      for (r in x) if (r in mine) n++
    }
    return n
  }

  private const val INVOICE_COUNTER = "nextInvoiceNo"

  private val LISTS: List<(ShopData) -> List<Any?>> = listOf(
    { it.debtors }, { it.transactions }, { it.expenses }, { it.expenseCategories }, { it.products },
    { it.productCategories }, { it.productUnits }, { it.warehouseEntries }, { it.sales }, { it.saleItems },
    { it.saleReturns }, { it.suppliers }, { it.purchases }, { it.supplierPayments }, { it.stockMovements },
    { it.priceHistory }, { it.auditLog },
  )

  /* ------------------------------ ردیف‌ها ------------------------------ */

  private fun idOf(row: JsonObject): String? =
    (row["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

  private fun objects(e: JsonElement?): List<JsonObject> = (e as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

  private fun byId(e: JsonElement?): LinkedHashMap<String, JsonObject> {
    val out = LinkedHashMap<String, JsonObject>()
    for (r in objects(e)) idOf(r)?.let { out[it] = r }
    return out
  }

  private fun rows(table: String, b: JsonElement?, o: JsonElement?, t: JsonElement?): JsonElement? {
    if (o == null) return t
    if (t == null) return o
    if (o == b) return t
    if (t == b) return o
    val base = byId(b)
    val theirs = byId(t)
    val mine = objects(o)
    val out = ArrayList<JsonElement>(maxOf(mine.size, theirs.size))
    val seen = HashSet<String>()
    for (row in mine) {
      val id = idOf(row)
      if (id == null) { out += row; continue }
      seen += id
      val was = base[id]
      val now = theirs[id]
      when {
        now == null && was == null -> out += row                 // کاربر افزود
        now == null -> Unit                                      // دیسک حذف کرد ⇒ حذف می‌برد
        else -> out += fields(table, was, row, now)
      }
    }
    for ((id, row) in theirs) {
      if (id in seen) continue
      if (id !in base) out += row                                // دیسک افزود (opِ سرور، دستگاهِ دیگر)
      //  در پایه بود و کاربر حذفش کرد ⇒ حذف می‌برد
    }
    //  ردیفِ بی شناسهٔ دیسک (نباید باشد) گم نشود
    for (row in objects(t)) if (idOf(row) == null && row !in out) out += row
    return JsonArray(out)
  }

  private fun fields(table: String, was: JsonObject?, mine: JsonObject, now: JsonObject): JsonObject {
    //  هر دو افزودند (همان شناسه): کاربر برنده، فیلدهایی که فقط دیسک دارد می‌مانند
    if (was == null) return JsonObject(LinkedHashMap<String, JsonElement>(now).apply { putAll(mine) })
    if (mine == was) return now
    if (now == was || now == mine) return mine
    val out = LinkedHashMap<String, JsonElement>()
    for (k in LinkedHashSet(mine.keys + now.keys + was.keys)) {
      val b = was[k]
      val o = mine[k]
      val t = now[k]
      val v = when {
        o == b -> t
        t == b || o == t -> o
        LedgerDiff.isInc(table, k) -> sum(b, o, t) ?: o
        else -> o
      }
      if (v != null) out[k] = v
    }
    return JsonObject(out)
  }

  /* ---------------------------- فهرستِ نام‌ها ---------------------------- */

  private fun strings(e: JsonElement?): List<String> =
    (e as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList()

  private fun names(b: JsonElement?, o: JsonElement?, t: JsonElement?): JsonElement? {
    if (o == null) return t
    if (t == null) return o
    if (o == b) return t
    if (t == b) return o
    val base = strings(b).toHashSet()
    val theirs = strings(t)
    val theirSet = theirs.toHashSet()
    val out = LinkedHashSet<String>()
    for (n in strings(o)) if (n !in base || n in theirSet) out += n
    for (n in theirs) if (n !in base) out += n
    return JsonArray(out.map { JsonPrimitive(it) })
  }

  /* ------------------------------ عددها ------------------------------ */

  private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

  private fun number(d: Double): JsonPrimitive =
    if (!d.isInfinite() && d == round(d) && abs(d) < 9.007199254740992E15) JsonPrimitive(d.toLong()) else JsonPrimitive(d)

  /** شمارنده: دیسک + (مالِ ما − پایه) */
  private fun sum(b: JsonElement?, o: JsonElement?, t: JsonElement?): JsonElement? {
    val ov = num(o) ?: return null
    val tv = num(t) ?: return null
    return number(tv + (ov - (num(b) ?: 0.0)))
  }

  private fun counter(b: JsonElement?, o: JsonElement?, t: JsonElement?): JsonElement? = when {
    o == b -> t ?: o
    t == null || t == b -> o
    else -> {
      val x = num(o)
      val y = num(t)
      if (x != null && y != null) number(maxOf(x, y)) else o
    }
  }
}
