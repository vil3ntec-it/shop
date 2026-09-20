package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull

/**
 *  تفاضلِ دفتر ⇒ opها — قرینهٔ دقیقِ `license/sync-core.js`.
 *
 *  ── چرا «تفاضل» و نه «هر DAO خودش op بنویسد» ──────────────────────
 *  دفترِ این برنامه یک سند است (`ShopData`)، نه چند جدولِ Room، و تنها
 *  درِ نوشتنش `ShopStore.save()` است. اگر می‌خواستیم هر یک از صدها جای
 *  نوشتن خودش op بسازد، کافی بود یکی‌شان فراموش شود تا آن تغییر برای
 *  همیشه از همگام‌سازی بیفتد و هیچ‌کس نفهمد.
 *
 *  با تفاضل در همان یک در، «نوشتنی که op نسازد» از نظرِ **ساختاری**
 *  ممکن نیست — قوی‌تر از قاعدهٔ «هیچ نوشتنی بیرونِ Repository».
 *  ⛔ این را به «هر تابع خودش op بنویسد» برنگردانید.
 *
 *  ── مجموعه‌ها ──────────────────────────────────────────────────────
 *  همان نام‌هایی که سرور در `lib/sync.js → TABLES` می‌شناسد. نامِ بیرونِ
 *  این فهرست `rejected` می‌گیرد.
 *
 *  ⚠️ `expenseCategories` · `productCategories` · `productUnits` و
 *  `nextInvoiceNo` این‌جا **نیستند** و این عمدی است: ردیفِ شناسه‌دار
 *  نیستند و سرور برایشان جدولی ندارد.
 */
object LedgerDiff {

  /** نسخهٔ Schema — همان عددی که سرور برای `shop` می‌شناسد. */
  const val SCHEMA_VERSION = 2

  val COLLECTIONS = listOf(
    "products", "warehouseEntries", "sales", "saleItems", "returns",
    "debtors", "transactions", "expenses",
    "suppliers", "purchases", "supplierPayments",
    "stockMovements", "priceHistory", "auditLog",
  )

  /**
   *  فیلدهای شمارنده — دلتا می‌روند، نه «مقدارِ تازه» (بندِ ۲۰.۴).
   *
   *  ⚠️ موجودیِ انبار این‌جا نیست چون اصلاً ذخیره نمی‌شود: از ردیف‌های
   *  ورود منهای ردیف‌های فروش حساب می‌شود، پس دو فروشِ هم‌زمان از دو
   *  گوشی از روزِ اول جمع‌پذیر بوده‌اند.
   *
   *  آن‌چه واقعاً روی خودِ ردیف جمع می‌شود همین چند تاست: مقدارِ
   *  مرجوع‌شده، و پولی که روی یک فاکتور یا یک خرید می‌نشیند.
   *  ⛔ فیلدی را بی دلیل اضافه نکنید: دلتا روی فیلدی که شمارنده نیست
   *  یعنی عددی که با هر همگام‌سازی دو برابر می‌شود.
   */
  val INC_FIELDS: Map<String, Set<String>> = mapOf(
    "sales" to setOf("paidAmount", "remaining", "debtGiven", "debtSettled"),
    "saleItems" to setOf("returnedQty"),
    "purchases" to setOf("paidAmount", "debt"),
  )

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  fun isInc(table: String, field: String): Boolean =
    INC_FIELDS[table]?.contains(field) == true

  /**
   *  دفتر به شکلِ JSON — همان شکلی که روی خط می‌رود.
   *
   *  ⛔ **عددها به شکلِ جاوااسکریپت نوشته می‌شوند**، یعنی `120` نه
   *  `120.0`.
   *
   *  ── چرا این یک خط حیاتی است ──────────────────────────────────────
   *  قیمت‌ها در `ShopData` از نوعِ `Double`اند و kotlinx آن‌ها را
   *  `120.0` می‌نویسد. سرور اثرِ انگشتِ op را روی چیزی که **دریافت**
   *  کرده دوباره می‌سازد و `JSON.stringify(120)` در Node می‌شود `120`.
   *  پس هشِ اندروید (`…"salePrice":120.0…`) با هشِ سرور
   *  (`…"salePrice":120…`) یکی درنمی‌آمد و **هر opِ قیمت‌دار
   *  `hash_mismatch` می‌گرفت** — یعنی همگام‌سازیِ اندروید بی‌صدا هیچ
   *  کاری نمی‌کرد.
   *
   *  و سودِ دومش: ردیفی که اندروید می‌نویسد با ردیفی که وب می‌نویسد
   *  بایت‌به‌بایت یکی می‌شود، پس دفترِ سرور دو شکل نمی‌گیرد.
   *
   *  ⚠️ عددِ کسری دست نمی‌خورد (`2.5` همان `2.5` است). عددِ خیلی بزرگ
   *  (بالای ۲^۵۳) در دو زبان دو شکل می‌شود؛ در دفترِ یک دکان چنین
   *  عددی نیست و اگر روزی بود، همان‌جا باید دیده شود.
   */
  fun toJson(data: ShopData): JsonObject =
    jsNumbers(json.encodeToJsonElement(ShopData.serializer(), data)) as JsonObject

  /** عددِ صحیحِ `Double` ⇒ عددِ بی اعشار، مثلِ جاوااسکریپت. */
  private fun jsNumbers(e: JsonElement): JsonElement = when (e) {
    is JsonArray -> JsonArray(e.map { jsNumbers(it) })
    is JsonObject -> JsonObject(e.mapValues { (_, v) -> jsNumbers(v) })
    is JsonPrimitive -> {
      val d = if (e.isString) null else e.doubleOrNull
      if (d != null && e.content.contains('.') && d == Math.rint(d) &&
        !d.isInfinite() && Math.abs(d) < 9.007199254740992E15
      ) JsonPrimitive(d.toLong()) else e
    }
  }

  fun fromJson(o: JsonObject): ShopData =
    json.decodeFromJsonElement(ShopData.serializer(), o)

  /** فقط مجموعه‌های همگام‌شدنی — سایه چیزِ دیگری لازم ندارد. */
  fun shadowOf(data: ShopData): JsonObject = shadowOf(toJson(data))

  fun shadowOf(tree: JsonObject): JsonObject = buildJsonObject {
    for (key in COLLECTIONS) put(key, tree[key] as? JsonArray ?: JsonArray(emptyList()))
  }

  private fun rowsOf(tree: JsonObject?, key: String): List<JsonObject> =
    (tree?.get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }
      ?.filter { idOf(it).isNotEmpty() } ?: emptyList()

  private fun idOf(row: JsonObject): String =
    (row["id"] as? JsonPrimitive)?.content.orEmpty()

  /** ردیف بی `id` — همان چیزی که داخلِ op می‌نشیند. */
  private fun bodyOf(row: JsonObject): JsonObject =
    JsonObject(row.filterKeys { it != "id" })

  private fun numberOf(e: JsonElement?): Double? =
    (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

  /**
   *  فیلدهای عوض‌شدهٔ یک ردیف؛ `null` یعنی هیچ چیزی عوض نشده.
   *
   *  فیلدی که از ردیف برداشته شده با `null`ِ صریح می‌رود، وگرنه سرور
   *  مقدارِ قدیمی را برای همیشه نگه می‌دارد.
   */
  fun changedFields(table: String, before: JsonObject, after: JsonObject): JsonObject? {
    val out = LinkedHashMap<String, JsonElement>()
    for ((k, v) in after) {
      if (k == "id") continue
      val old = before[k]
      if (old != null && OpHash.canonical(old) == OpHash.canonical(v)) continue
      if (isInc(table, k)) {
        val now = numberOf(v)
        if (now != null) {
          val was = numberOf(old) ?: 0.0
          val delta = now - was
          if (delta == 0.0) continue
          out[k] = buildJsonObject { put("\$inc", JsonPrimitive(trim(delta))) }
          continue
        }
      }
      out[k] = v
    }
    for ((k, v) in before) {
      if (k == "id" || after.containsKey(k) || v is JsonNull) continue
      out[k] = JsonNull
    }
    return if (out.isEmpty()) null else JsonObject(out)
  }

  /** عددِ گرد — تا `3.0` به شکلِ `3` برود، همان‌طور که جاوااسکریپت می‌نویسد. */
  private fun trim(d: Double): Number =
    if (d == Math.rint(d) && !d.isInfinite() && Math.abs(d) < 9.007199254740992E15) d.toLong() else d

  /**
   *  تفاضلِ سایه با دفترِ همین حالا.
   *
   *  ترتیبِ خروجی همان ترتیبِ خودِ فهرست‌هاست، تا «فاکتور پیش از اقلامش»
   *  بماند.
   */
  fun diff(shadow: JsonObject?, next: JsonObject, now: Long = System.currentTimeMillis()): List<SyncOp> {
    val ops = ArrayList<SyncOp>()
    for (table in COLLECTIONS) {
      val before = rowsOf(shadow, table).associateBy { idOf(it) }
      val after = rowsOf(next, table)
      val afterIds = HashSet<String>(after.size)

      for (row in after) {
        val id = idOf(row)
        afterIds += id
        val was = before[id]
        if (was == null) {
          ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.INSERT, bodyOf(row))
        } else {
          val fields = changedFields(table, bodyOf(was), bodyOf(row))
          if (fields != null) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.UPDATE, fields)
        }
      }
      for (id in before.keys) {
        if (id !in afterIds) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.DELETE, null)
      }
    }
    return ops
  }

  /**
   *  اعمالِ opهای دستگاه‌های دیگر روی دفترِ محلی.
   *
   *  ⚠️ نتیجه باید هم روی دفتر بنشیند و هم روی **سایه**، در یک لحظه.
   *  اگر فقط روی دفتر بنشیند، تفاضلِ بعدی همان را «تغییرِ تازهٔ خودم»
   *  می‌بیند و به سرور پس می‌فرستد — حلقهٔ بی‌پایانِ رفت‌وبرگشت.
   */
  fun applyRemote(tree: JsonObject, ops: List<SyncOp>): JsonObject {
    val tables = LinkedHashMap<String, MutableList<JsonObject>>()
    for (key in COLLECTIONS) tables[key] = rowsOf(tree, key).toMutableList()

    for (op in ops) {
      val list = tables[op.table] ?: continue          // جدولِ ناشناخته: رد
      val at = list.indexOfFirst { idOf(it) == op.rowId }
      if (op.type == SyncOp.DELETE) {
        if (at >= 0) list.removeAt(at)
        continue
      }
      val current = if (at >= 0) LinkedHashMap(list[at]) else linkedMapOf<String, JsonElement>()
      for ((f, v) in (op.fields ?: JsonObject(emptyMap()))) {
        val inc = (v as? JsonObject)?.get("\$inc")
        val incNum = numberOf(inc)
        if (inc != null && incNum != null) {
          val cur = numberOf(current[f]) ?: 0.0
          current[f] = JsonPrimitive(trim(cur + incNum))
        } else {
          current[f] = v
        }
      }
      current["id"] = JsonPrimitive(op.rowId)
      val row = JsonObject(current)
      if (at >= 0) list[at] = row else list.add(row)
    }

    val out = LinkedHashMap<String, JsonElement>(tree)
    for ((key, list) in tables) out[key] = JsonArray(list)
    return JsonObject(out)
  }

  /**
   *  Snapshot ⇒ دفتر — گوشیِ نو، یک بار.
   *
   *  پاسخِ سرور `{tables: {products: [{id, data}], …}}` است.
   */
  fun fromSnapshot(snapshot: JsonObject, base: JsonObject): JsonObject {
    val tables = snapshot["tables"] as? JsonObject ?: JsonObject(emptyMap())
    val out = LinkedHashMap<String, JsonElement>(base)
    for (key in COLLECTIONS) {
      val rows = tables[key] as? JsonArray ?: continue
      val list = ArrayList<JsonObject>(rows.size)
      for (r in rows) {
        val o = r as? JsonObject ?: continue
        val id = (o["id"] as? JsonPrimitive)?.content ?: continue
        val body = o["data"] as? JsonObject ?: JsonObject(emptyMap())
        val row = LinkedHashMap<String, JsonElement>(body)
        row["id"] = JsonPrimitive(id)
        list += JsonObject(row)
      }
      out[key] = JsonArray(list)
    }
    return JsonObject(out)
  }
}
