package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
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
 *  ⚠️ `expenseCategories` · `productCategories` · `productUnits` در
 *  `COLLECTIONS` نیستند چون ردیفِ شناسه‌دار نیستند، فهرستِ نام‌اند —
 *  جدا در `LIST_COLLECTIONS` می‌آیند (F0-04، پلهٔ ۳). `nextInvoiceNo` اصلاً
 *  همگام نمی‌شود: عددِ هر دستگاه مالِ خودش است.
 */
object LedgerDiff {

  /**
   *  نسخهٔ Schema — همان عددی که سرور برای `shop` می‌شناسد.
   *  پلهٔ ۳ (F0-04): سه فهرستِ تنظیماتی (`LIST_COLLECTIONS`) هم می‌روند.
   */
  const val SCHEMA_VERSION = 3

  val COLLECTIONS = listOf(
    "products", "warehouseEntries", "sales", "saleItems", "returns",
    "debtors", "transactions", "expenses",
    "suppliers", "purchases", "supplierPayments",
    "stockMovements", "priceHistory", "auditLog",
  )

  /**
   *  فهرست‌های تنظیماتی — F0-04 (پلهٔ ۳)، قرینهٔ بخشِ ۱-بِ `sync-core.js`.
   *
   *  در دفتر **فهرستِ نام**اند (`["لبنیات", "نوشیدنی"]`). تا پلهٔ ۲ اصلاً
   *  همگام نمی‌شدند: دسته‌ای که روی گوشی ساخته می‌شد روی کامپیوتر و وب
   *  نبود. روی خط هر نام یک ردیف است: `row_id` = ‎sha256(نام)‎ (۳۲ نویسهٔ
   *  اول)، `fields = {name}`. شناسه از خودِ نام است، پس دو دستگاه که یک
   *  نام را جدا بسازند **یک** ردیف می‌سازند.
   *
   *  ⚠️ سایهٔ پیش از پلهٔ ۳ این کلیدها را ندارد ⇒ نخستین تفاضل همهٔ
   *  نام‌های این دستگاه را یک بار «درج» می‌فرستد (نه حذف). و `applyRemote`
   *  نبودنِ کلید را نگه می‌دارد: اگر پیش‌فرض‌های مدل در سایه می‌نشستند،
   *  نامِ محلی «رفته» شمرده می‌شد و نامِ پیش‌فرضی که این‌جا پاک شده بود،
   *  روی همهٔ دستگاه‌ها حذف می‌شد.
   */
  val LIST_COLLECTIONS = listOf("productCategories", "productUnits", "expenseCategories")

  fun listRowId(name: String): String = OpHash.sha256Hex(name).substring(0, 32)

  private fun listName(e: JsonElement?): String = when (e) {
    is JsonPrimitive -> if (e.isString) e.content.trim() else ""
    is JsonObject -> (e["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
    else -> ""
  }

  /** فهرستِ نام ⇒ نام‌های بی خالی و بی تکرار؛ `null` یعنی کلید اصلاً نیست. */
  private fun listNames(tree: JsonObject?, key: String): List<String>? {
    val arr = tree?.get(key) as? JsonArray ?: return null
    val out = ArrayList<String>(arr.size)
    for (e in arr) {
      val n = listName(e)
      if (n.isNotEmpty() && n !in out) out += n
    }
    return out
  }

  private fun nameArray(list: List<String>): JsonArray = JsonArray(list.map { JsonPrimitive(it) })

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
   *  نامِ فیلدهایی که مدلِ **همین نسخه** برای هر مجموعه می‌شناسد — نامِ
   *  سریال و نام‌های `@JsonNames`، خوانده از خودِ `ShopData.serializer()`،
   *  پس فیلدِ تازهٔ مدل بی هیچ فهرستِ دستی همین‌جا هم هست.
   */
  @OptIn(ExperimentalSerializationApi::class)
  private val MODEL_FIELDS: Map<String, Set<String>> by lazy {
    val root = ShopData.serializer().descriptor
    COLLECTIONS.associateWith { key ->
      val at = root.getElementIndex(key)
      if (at == CompositeDecoder.UNKNOWN_NAME) return@associateWith emptySet()
      val row = root.getElementDescriptor(at).getElementDescriptor(0)
      val names = HashSet<String>()
      for (i in 0 until row.elementsCount) {
        names += row.getElementName(i)
        for (a in row.getElementAnnotations(i)) if (a is JsonNames) names += a.names
      }
      names
    }
  }

  /**
   *  آیا مدلِ این نسخه کلیدِ [key] را در ردیف‌های [table] می‌شناسد؟
   *  مجموعه‌ای که مدل ندارد (نباید پیش بیاید) «می‌شناسد» حساب می‌شود، یعنی
   *  همان رفتارِ پیشین.
   */
  fun isModelField(table: String, key: String): Boolean {
    val known = MODEL_FIELDS[table]
    return known.isNullOrEmpty() || key in known
  }

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
    //  فهرست‌ها: نبودنِ کلید یعنی «هنوز هیچ‌وقت نرفته» — همان‌طور می‌ماند
    for (key in LIST_COLLECTIONS) (tree[key] as? JsonArray)?.let { put(key, it) }
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
   *
   *  ⛔ **فقط فیلدی که مدلِ همین نسخه می‌شناسد** (`isModelField`). کلیدی که
   *  مدل ندارد را برنامهٔ تازه‌تر یا سرور نوشته (`payments`، `approval`،
   *  `orderId`، …)؛ دفترِ این نسخه آن را از درِ مدل نمی‌خواند، پس «نبودنش»
   *  در `after` یعنی «نمی‌شناسم»، نه «کاربر پاکش کرد». پیش از A00 همین‌جا
   *  `null` می‌رفت و دادهٔ نسخهٔ تازه‌تر روی سرور پاک می‌شد.
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
      if (!isModelField(table, k)) continue
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
   *
   *  ⛔ **مقایسه به زبانِ مدل** (A00): سایه می‌تواند ردیفِ خامِ سرور باشد
   *  (Snapshot، `unionRows`، سایه‌ای که نسخهٔ پیشین روی دیسک گذاشته) —
   *  با پیش‌فرض‌هایی که ندارد یا کلیدِ تودرتویی (`payments[].custody`) که
   *  مدل نمی‌شناسد. ردیفی که خام با دفتر فرق دارد یک بار از درِ مدل رد
   *  می‌شود (`modelView`) و تفاضل با همان گرفته می‌شود؛ پس ردیفی که کاربر
   *  دست نزده هیچ opی نمی‌سازد و فیلدی که عوض نشده، حتی اگر مدل بخشی از
   *  درونش را نشناسد، دوباره نوشته نمی‌شود.
   */
  fun diff(shadow: JsonObject?, next: JsonObject, now: Long = System.currentTimeMillis()): List<SyncOp> {
    val ops = ArrayList<SyncOp>()
    for (table in COLLECTIONS) {
      val before = rowsOf(shadow, table).associateBy { idOf(it) }
      val after = rowsOf(next, table)
      val afterIds = HashSet<String>(after.size)
      //  فقط ردیف‌هایی که خام فرق دارند — معمولاً همان چند ردیفی که کاربر عوض کرده
      val view = modelView(table, after.mapNotNull { row ->
        before[idOf(row)]?.takeIf { changedFields(table, bodyOf(it), bodyOf(row)) != null }
      })

      for (row in after) {
        val id = idOf(row)
        afterIds += id
        val was = before[id]
        if (was == null) {
          ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.INSERT, bodyOf(row))
        } else {
          val fields = changedFields(table, bodyOf(view[id] ?: was), bodyOf(row))
          if (fields != null) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.UPDATE, fields)
        }
      }
      for (id in before.keys) {
        if (id !in afterIds) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.DELETE, null)
      }
    }

    //  فهرست‌های تنظیماتی: فقط درج و حذف؛ نام همان شناسه است
    for (table in LIST_COLLECTIONS) {
      val after = listNames(next, table) ?: continue      // دفتری که کلید ندارد چیزی پاک نمی‌کند
      val before = (listNames(shadow, table) ?: emptyList()).associateBy { listRowId(it) }
      val afterIds = HashSet<String>(after.size)
      for (name in after) {
        val id = listRowId(name)
        afterIds += id
        if (id in before) continue
        ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.INSERT, buildJsonObject { put("name", JsonPrimitive(name)) })
      }
      for (id in before.keys) {
        if (id !in afterIds) ops += SyncOp(Ulid.next(now), now, table, id, SyncOp.DELETE, null)
      }
    }
    return ops
  }

  /**
   *  ردیف‌های خامِ [rows] از جدولِ [table] همان‌طور که مدلِ این نسخه
   *  می‌نویسدشان (پیش‌فرض‌ها پر، کلیدِ ناشناخته بیرون)، به شناسه.
   *  ردیفی که مدل نتواند بخواند بیرون می‌ماند و تفاضلش خام گرفته می‌شود.
   */
  private fun modelView(table: String, rows: List<JsonObject>): Map<String, JsonObject> {
    if (rows.isEmpty()) return emptyMap()
    fun shape(list: List<JsonObject>): List<JsonObject>? = try {
      rowsOf(toJson(fromJson(JsonObject(mapOf(table to JsonArray(list))))), table)
    } catch (_: Exception) {
      null
    }
    shape(rows)?.takeIf { it.size == rows.size }?.let { all -> return all.associateBy { idOf(it) } }
    //  یک ردیفِ ناخوانا بقیه را از راهِ مدل بیرون نیندازد
    val out = HashMap<String, JsonObject>()
    for (r in rows) shape(listOf(r))?.singleOrNull()?.let { out[idOf(it)] = it }
    return out
  }

  /**
   *  درختِ [next] (از درِ مدل) + کلیدهایی از همان ردیف در [shadow] که مدلِ
   *  این نسخه نمی‌شناسد — رفت‌وبرگشتِ JSON ِ خام (A00).
   *
   *  سایه‌ای که با این ساخته شود آنچه برنامهٔ تازه‌تر یا سرور روی ردیف
   *  گذاشته (`payments`، `orderId`، …) را نگه می‌دارد؛ `changedFields` هم
   *  هرگز برای آن‌ها `null` نمی‌سازد. ردیفی که در [next] نیست (پاک‌شده)
   *  همان‌طور بیرون می‌ماند.
   */
  fun keepUnknown(shadow: JsonObject?, next: JsonObject): JsonObject {
    if (shadow == null) return next
    var out: LinkedHashMap<String, JsonElement>? = null
    for (table in COLLECTIONS) {
      val before = rowsOf(shadow, table).associateBy { idOf(it) }
      val rows = next[table] as? JsonArray
      if (before.isEmpty() || rows == null) continue
      var changed = false
      val list = rows.map { r ->
        val row = r as? JsonObject ?: return@map r
        val was = before[idOf(row)] ?: return@map r
        val extra = was.filterKeys { it !in row && !isModelField(table, it) }
        if (extra.isEmpty()) return@map r
        changed = true
        JsonObject(LinkedHashMap<String, JsonElement>(row).apply { putAll(extra) })
      }
      if (changed) (out ?: LinkedHashMap<String, JsonElement>(next).also { out = it })[table] = JsonArray(list)
    }
    return out?.let { JsonObject(it) } ?: next
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
    //  فهرست‌ها: فقط کلیدی که در درخت هست یا opی به آن رسیده
    val lists = LinkedHashMap<String, MutableList<String>>()
    for (key in LIST_COLLECTIONS) listNames(tree, key)?.let { lists[key] = it.toMutableList() }
    val touchedLists = HashSet<String>()

    for (op in ops) {
      if (op.table in LIST_COLLECTIONS) {
        touchedLists += op.table
        val names = lists.getOrPut(op.table) { mutableListOf() }
        if (op.type == SyncOp.DELETE) {
          names.removeAll { listRowId(it) == op.rowId }
        } else {
          val name = listName(op.fields)
          if (name.isNotEmpty() && name !in names) names += name
        }
        continue
      }
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
    for ((key, list) in lists) if (key in touchedLists) out[key] = nameArray(list)
    val raw = JsonObject(out)

    /*
     *  ⛔ **درختِ خروجی از همان درِ مدل رد می‌شود** — و این تزئین نیست،
     *  بستنِ حلقهٔ پژواک است.
     *
     *  opی که از سرور می‌آید فقط فیلدهای **عوض‌شده** را دارد
     *  (`{name: "فلفل"}`). اگر همان ردیفِ ناقص در درخت بنشیند، سایه
     *  (`shadowOf`) ناقص می‌ماند ولی دفترِ محلی از `fromJson` پر می‌شود و
     *  پیش‌فرض‌ها را می‌گیرد — پس دورِ بعد `diff` آن پیش‌فرض‌ها را
     *  «تغییرِ تازه» می‌بیند و همان را به سرور پس می‌فرستد. یک رفت‌وبرگشتِ
     *  بیهوده برای هر ردیفی که از دستگاهِ دیگر می‌آید.
     *
     *  با یک بار عبور از `fromJson`/`toJson`، سایه و دفتر **همان** شکل را
     *  می‌گیرند و `diff` صفر می‌شود. سنجه‌اش «اعمالِ opهای رسیده هیچ opِ
     *  تازه‌ای نمی‌سازد» است.
     *
     *  ⚠️ و اگر روزی ردیفی برسد که مدل نتواند بخواندش، درختِ خام برمی‌گردد:
     *  یک پژواکِ اضافه بهتر از پرتاب شدنِ استثنا وسطِ همگام‌سازی است.
     */
    val shaped = try {
      toJson(fromJson(raw))
    } catch (_: Exception) {
      raw
    }
    //  ⚠️ پیش‌فرضِ مدل جای فهرستی که در درخت نبود نمی‌نشیند (شرح بالای `LIST_COLLECTIONS`)
    //  و کلیدی که مدل نمی‌شناسد در ردیف می‌ماند (A00، `keepUnknown`): سایه همان
    //  چیزی را دارد که سرور دارد؛ `fromJson` ِ دفتر آن را نادیده می‌گیرد
    return keepListsOf(raw, keepUnknown(raw, shaped))
  }

  private fun keepListsOf(src: JsonObject, dst: JsonObject): JsonObject {
    val out = LinkedHashMap<String, JsonElement>(dst)
    for (key in LIST_COLLECTIONS) {
      val v = src[key]
      if (v is JsonArray) out[key] = v else out.remove(key)
    }
    return JsonObject(out)
  }

  /**
   *  Snapshot ⇒ دفتر — گوشیِ نو، یک بار.
   *
   *  پاسخِ سرور `{tables: {products: [{id, data}], …}}` است.
   */
  /** ردیف‌های `base` به‌اضافهٔ ردیف‌هایی از `extra` که شناسه‌شان در `base` نیست. */
  fun unionRows(base: JsonObject, extra: JsonObject): JsonObject {
    val out = LinkedHashMap<String, JsonElement>(base)
    for (key in COLLECTIONS) {
      val mine = rowsOf(base, key)
      val ids = mine.mapTo(HashSet()) { idOf(it) }
      val added = rowsOf(extra, key).filter { idOf(it) !in ids }
      if (added.isEmpty() && base[key] != null) continue
      out[key] = JsonArray(mine + added)
    }
    for (key in LIST_COLLECTIONS) {
      val extraNames = listNames(extra, key) ?: continue
      val mine = listNames(base, key) ?: emptyList()
      out[key] = nameArray(mine + extraNames.filter { it !in mine })
    }
    return JsonObject(out)
  }

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
    //  فهرست‌ها: آنچه سرور دارد، با ترتیبِ محلی برای نام‌های مشترک
    for (key in LIST_COLLECTIONS) {
      val rows = tables[key] as? JsonArray ?: continue
      val server = ArrayList<String>()
      for (r in rows) {
        val n = listName((r as? JsonObject)?.get("data"))
        if (n.isNotEmpty() && n !in server) server += n
      }
      val keep = (listNames(base, key) ?: emptyList()).filter { it in server }
      out[key] = nameArray(keep + server.filter { it !in keep })
    }
    return JsonObject(out)
  }
}
