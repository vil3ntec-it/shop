package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  سازگاریِ دفتر میانِ نسخه‌ها — `docs/DATA-SAFETY-fa.md` §۷ («جدولِ سازگاری»)، با `ShopStore` ِ واقعی روی دیسک.
 *
 *  نمونه‌ها (`android-native/app/src/test/resources/compat/`، ساختهٔ `tools/compat-ledger/regen.sh` با **مدلِ خودِ هر
 *  نسخه** از `git show`؛ شناسهٔ کامیت‌ها در `REFS.txt`):
 *   • `old-seed1.json`         — کهنه‌ترین دفترِ نمونهٔ مخزن (`tools/shop-migrate/…/fixture-seed1.json`) که main ِ دستهٔ ۳۰
 *                                (`e02fe4561`، آخرین نسخهٔ نصب‌شده) باز و ذخیره کرد؛
 *   • `old-maximal.json`       — دفترِ دستهٔ ۳۰ با **همهٔ** فیلدهایی که آن نسخه می‌شناسد، پر (از توصیف‌گرِ خودِ مدل)؛
 *   • `new-maximal.json`       — همان برای این نسخه (دستهٔ ۳۱)؛
 *   • `old-resaved-new.json`   — دستهٔ ۳۰ دفترِ این نسخه را باز کرد (بی خطا) و ذخیره کرد = «برگشت به نسخهٔ کهنه‌تر».
 */
class LedgerCompatDiskTest {

  private val ctx = DesktopContext
  private val dir get() = ctx.filesDir
  private val ledger get() = File(dir, "shop-data.json")
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private lateinit var before: Set<String>

  private fun clean() {
    AtRest.setWanted(ctx, false)
    dir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    AutoBackup.dir(ctx).listFiles().orEmpty().forEach { it.delete() }
  }

  private fun state(): SyncStateV1 {
    val sync = ir.vil3ntec.tohid.sync.SyncStore(ctx)
    return SyncStateV1(ctx, SyncStateV1.currentAccount(ctx).ifBlank { sync.accountName }.ifBlank { sync.deviceUid })
  }

  private fun neverSynced() = state().apply { cursor = 0L; reconciledAt = 0L; snapshotAt = 0L }

  @Before fun setUp() { clean(); neverSynced(); before = dir.list().orEmpty().toSet() }

  @After fun tearDown() {
    clean(); neverSynced()
    //  فقط پرونده‌هایی که همین آزمون ساخت (صف و ژورنالِ همگام‌سازیِ ذخیره‌ها)
    dir.listFiles().orEmpty().filter { it.name !in before && it.isFile }.forEach { it.delete() }
  }

  private fun fixture(name: String): File {
    var d: File? = File("").absoluteFile
    while (d != null) {
      val f = File(d, "android-native/app/src/test/resources/compat/$name")
      if (f.isFile) return f
      d = d.parentFile
    }
    throw AssertionError("نمونهٔ سازگاریِ $name پیدا نشد")
  }

  private fun tree(f: File): JsonObject = Json.parseToJsonElement(f.readText()).jsonObject
  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  /** هر کلید و مقدارِ [raw] در [out] هست (عدد با عدد: `301` = `301.0`) — مسیرِ نخستین ناهمخوانی، یا `null` */
  private fun missing(raw: JsonElement, out: JsonElement?, path: String = ""): String? = when (raw) {
    is JsonObject -> if (out !is JsonObject) path else raw.entries.firstNotNullOfOrNull { (k, v) -> missing(v, out[k], "$path/$k") }
    is JsonArray -> if (out !is JsonArray || out.size != raw.size) path else raw.indices.firstNotNullOfOrNull { missing(raw[it], out[it], "$path/$it") }
    is JsonPrimitive -> when {
      out !is JsonPrimitive -> path
      !raw.isString && !out.isString && raw.content.toDoubleOrNull() != null -> path.takeIf { raw.content.toDouble() != out.content.toDoubleOrNull() }
      else -> path.takeIf { raw != out }
    }
  }

  /** همهٔ مسیرهای کلید (جای عنصرِ فهرست ⇒ `*`) */
  private fun paths(e: JsonElement, p: String = "", out: MutableSet<String> = sortedSetOf()): Set<String> {
    when (e) {
      is JsonObject -> e.forEach { (k, v) -> out += "$p/$k"; paths(v, "$p/$k", out) }
      is JsonArray -> e.forEach { paths(it, "$p/*", out) }
      else -> {}
    }
    return out
  }

  /** همان عددهای `MigrationGoldenTest` (موجودی، قرض، بدهیِ تأمین‌کننده، گزارشِ ماه‌به‌ماه) + «دخل»: جمعِ پرداخت‌ها */
  private fun figures(d: ShopData): List<String> {
    val out = ArrayList<String>()
    d.products.map { it.id }.distinct().forEach { out += "stock/$it=${ShopStore.stock(d, it)}" }
    d.debtors.map { it.id }.distinct().forEach { out += "debt/$it=${ShopStore.debt(d, it)}" }
    d.suppliers.map { it.id }.distinct().forEach { out += "supplierDebt/$it=${ShopStore.supplierDebt(d, it)}" }
    fun report(label: String, from: String, to: String) {
      val r = ReportEngine.sales(d, from, to)
      listOf(
        "count" to r.count, "gross" to r.gross, "discount" to r.discount, "net" to r.net, "cogs" to r.cogs,
        "grossProfit" to r.grossProfit, "expenses" to r.expenses, "netProfit" to r.netProfit, "returnAmount" to r.returnAmount,
      ).forEach { (k, v) -> out += "report/$label/$k=$v" }
    }
    report("all", "0000-01-01", "9999-12-31")
    (d.sales.map { it.date } + d.saleReturns.map { it.date } + d.expenses.map { it.date })
      .mapNotNull { ir.vil3ntec.tohid.Jalali.ofIso(it) }.map { it.year to it.month }.distinct()
      .forEach { (jy, jm) -> val (from, to) = ReportEngine.jalaliMonth(jy, jm); report("%04d-%02d".format(jy, jm), from, to) }
    return out.sorted()
  }

  private fun cash(d: ShopData) = listOf(
    "paid=${d.sales.sumOf { it.paidAmount }}", "received=${d.transactions.filter { it.type == "receive" }.sumOf { it.amount }}",
    "supplierPaid=${d.supplierPayments.sumOf { it.amount }}", "expenses=${d.expenses.sumOf { it.amount }}",
  )

  private fun golden(): List<String> {
    var d: File? = File("").absoluteFile
    while (d != null) {
      val f = File(d, "tools/shop-migrate/src/test/resources/golden/legacy-golden.txt")
      if (f.isFile) return f.readLines().filter { it.isNotBlank() }
      d = d.parentFile
    }
    throw AssertionError("legacy-golden.txt پیدا نشد")
  }

  /** دفترِ [f] را این نسخه باز می‌کند، یک ردیف را ویرایش و ذخیره می‌کند ⇒ متنِ روی دیسک */
  private fun loadEditSave(f: File): Pair<ShopData, JsonObject> {
    f.copyTo(ledger, overwrite = true)
    val s = open()
    assertNull("دفترِ ${f.name} بی خطا خوانده می‌شود", s.unreadable.value)
    val d = s.data.value
    val p = d.products.first()
    assertTrue(runBlocking { s.save(d.copy(products = listOf(p.copy(notes = p.notes + " ✓")) + d.products.drop(1))) })
    return d to tree(ledger)
  }

  /* ----------------------------- نسخهٔ تازه دفترِ کهنه را می‌خواند ----------------------------- */

  @Test fun `کهنه‌ترین دفتر و ذخیرهٔ دستهٔ ۳۰ — همان ردیف‌ها، عددها و کلیدها`() {
    val seed = run {
      var d: File? = File("").absoluteFile
      var hit: File? = null
      while (d != null && hit == null) { hit = File(d, "tools/shop-migrate/src/test/resources/golden/fixture-seed1.json").takeIf { it.isFile }; d = d.parentFile }
      hit ?: throw AssertionError("fixture-seed1.json پیدا نشد")
    }
    val direct = json.decodeFromString(ShopData.serializer(), seed.readText())
    for (f in listOf(seed, fixture("old-seed1.json"))) {
      val raw = tree(f)
      val (read, saved) = loadEditSave(f)
      for ((k, v) in raw) if (v is JsonArray) assertEquals("${f.name}: شمارِ $k", v.size, read.let { (json.encodeToJsonElement(ShopData.serializer(), it).jsonObject[k] as JsonArray).size })
      //  ذخیرهٔ این نسخه: هر کلید و مقدارِ دفترِ کهنه سرِ جایش (جز همان یادداشتی که عوض شد)
      val edited = raw.toMutableMap().also { m ->
        val ps = m.getValue("products").jsonArray
        m["products"] = JsonArray(listOf(saved.getValue("products").jsonArray[0]) + ps.drop(1))
      }
      assertNull("${f.name}: ذخیرهٔ این نسخه کلید یا مقداری انداخت", missing(JsonObject(edited), saved))
      //  همان عددها: مرجعِ main (`legacy-golden.txt` = `MigrationGoldenTest` روی هر نسخه) و دخل همان خواندنِ مستقیم
      assertEquals("${f.name}: موجودی، قرض، بدهی و گزارش", golden(), figures(read))
      assertEquals("${f.name}: دخل", cash(direct), cash(read))
      val again = open().data.value
      assertEquals("${f.name}: پس از ذخیره همان عددها", golden(), figures(again))
      assertEquals(cash(direct), cash(again))
    }
  }

  @Test fun `دفترِ ساختِ پایدارِ 3_2_337 — همان ردیف‌ها، عددها و کلیدها`() {
    //  `StableUpgradeTest`: نوشته با کدِ همان ساخت (`93800f41`)؛ این‌جا از راهِ واقعیِ `ShopStore` روی دیسک
    val f = run {
      var d: File? = File("").absoluteFile
      var hit: File? = null
      while (d != null && hit == null) { hit = File(d, "android-native/app/src/test/resources/upgrade/stable-3.2.337/shop-data.json").takeIf { it.isFile }; d = d.parentFile }
      hit ?: throw AssertionError("stable-3.2.337/shop-data.json پیدا نشد")
    }
    val raw = tree(f)
    val direct = json.decodeFromString(ShopData.serializer(), f.readText())
    val (read, saved) = loadEditSave(f)
    val edited = JsonObject(raw + ("products" to JsonArray(listOf(saved.getValue("products").jsonArray[0]) + raw.getValue("products").jsonArray.drop(1))))
    assertNull("ذخیرهٔ این نسخه کلید یا مقداری انداخت", missing(edited, saved))
    assertEquals(figures(direct), figures(read))
    assertEquals(cash(direct), cash(read))
    val again = open().data.value
    assertEquals(figures(direct), figures(again))
    assertTrue(read.sales.isNotEmpty() && read.saleReturns.isNotEmpty())
  }

  @Test fun `دفترِ پرِ دستهٔ ۳۰ — هر کلید و مقدار می‌ماند`() {
    val f = fixture("old-maximal.json")
    val raw = tree(f)
    val (read, saved) = loadEditSave(f)
    for (k in listOf("products", "warehouseEntries", "sales", "saleItems", "returns", "debtors", "transactions", "expenses",
      "suppliers", "purchases", "supplierPayments", "stockMovements", "priceHistory", "auditLog", "safeEntries")) {
      assertEquals("$k: یک ردیف", 1, raw.getValue(k).jsonArray.size)
    }
    assertEquals(1, read.products.size)
    val p = saved.getValue("products").jsonArray[0].jsonObject
    val edited = JsonObject(raw + ("products" to JsonArray(listOf(JsonObject(raw.getValue("products").jsonArray[0].jsonObject + ("notes" to p.getValue("notes")))))))
    assertNull("کلید یا مقدارِ دستهٔ ۳۰ پس از ذخیرهٔ این نسخه", missing(edited, saved))
  }

  /* ----------------------------- برگشت به نسخهٔ کهنه‌تر (downgrade) ----------------------------- */

  /** آنچه دستهٔ ۳۰ از دفترِ این نسخه می‌اندازد — همان جدولِ §۷ ِ `DATA-SAFETY-fa.md` (هر تغییر ⇒ آن جدول هم) */
  private val lostOnDowngrade = sortedSetOf(
    "/setAside",
    "/products/*/purchaseUnits",
    "/warehouseEntries/*/costMinor", "/warehouseEntries/*/countedAt", "/warehouseEntries/*/seenMilli", "/warehouseEntries/*/correctedAt",
    "/saleItems/*/priceSource",
    "/debtors/*/specialPrices",
    "/purchases/*/unitName", "/purchases/*/unitQtyMilli", "/purchases/*/unitFactorMilli",
    "/stockMovements/*/correctedAt",
    "/auditLog/*/viewer", "/auditLog/*/role", "/auditLog/*/device", "/auditLog/*/view",
  )

  @Test fun `برگشت به دستهٔ ۳۰ — فقط همین فیلدهای تازه می‌افتند`() {
    val newer = tree(fixture("new-maximal.json"))
    val older = tree(fixture("old-resaved-new.json"))
    val lost = paths(newer) - paths(older)
    val roots = lost.filterTo(sortedSetOf()) { p -> lost.none { q -> q != p && p.startsWith("$q/") } }
    assertEquals(lostOnDowngrade, roots)
    //  هر چه ماند همان مقدار است
    fun strip(e: JsonElement, p: String = ""): JsonElement = when (e) {
      is JsonObject -> JsonObject(e.filterKeys { "$p/$it" !in lostOnDowngrade }.mapValues { (k, v) -> strip(v, "$p/$k") })
      is JsonArray -> JsonArray(e.map { strip(it, "$p/*") })
      else -> e
    }
    assertNull(missing(strip(newer), older))
    //  و برگشتن به این نسخه: دفترِ ذخیره‌شدهٔ دستهٔ ۳۰ همان ردیف‌ها را دارد (فیلدهای افتاده فقط از سرور برمی‌گردند)
    val (read, _) = loadEditSave(fixture("old-resaved-new.json"))
    assertEquals(1, read.warehouseEntries.size)
    assertNull(read.warehouseEntries.single().costMinor)
    assertTrue(read.setAside.isEmpty())
  }

  /* ------------------ این نسخه کلیدِ نسخهٔ تازه‌تر را نگه می‌دارد (LedgerExtras) ------------------ */

  /** دفترِ «نسخهٔ بعدی»: همان دفترِ این نسخه با کلیدهایی که این نسخه نمی‌شناسد، در هر لایه */
  private fun future(): JsonObject {
    val t = tree(fixture("new-maximal.json")).toMutableMap()
    fun row(k: String, add: Map<String, JsonElement>) {
      t[k] = JsonArray(t.getValue(k).jsonArray.map { JsonObject(it.jsonObject + add) })
    }
    t["futureCollection"] = Json.parseToJsonElement("""[{"id":"fc1","amountMinor":12500}]""")
    row("warehouseEntries", mapOf("futureCostMinor" to JsonPrimitive(99_000)))
    row("debtors", mapOf("futureTier" to JsonPrimitive("gold")))
    //  تودرتو: پرداختِ فاکتور (فهرستِ بی شناسه) و سندِ کنارگذاشته (با شناسه)
    t["sales"] = JsonArray(t.getValue("sales").jsonArray.map { s ->
      val o = s.jsonObject
      JsonObject(o + ("payments" to JsonArray(o.getValue("payments").jsonArray.map { JsonObject(it.jsonObject + ("futureRef" to JsonPrimitive("r-1"))) })))
    })
    row("setAside", mapOf("futureAudit" to JsonPrimitive("a-1")))
    return JsonObject(t)
  }

  private fun at(t: JsonObject, vararg p: String): JsonElement? {
    var e: JsonElement? = t
    for (k in p) e = when (e) { is JsonObject -> e[k]; is JsonArray -> e.getOrNull(k.toInt()); else -> null }
    return e
  }

  @Test fun `بی‌همگام — کلیدِ نسخهٔ تازه‌تر در هر لایه می‌ماند`() {
    val fut = future()
    ledger.writeText(fut.toString())
    val s = open()
    assertNull(s.unreadable.value)
    val d = s.data.value
    //  ویرایشِ ردیفی که کلیدِ ناشناخته دارد + پاک کردنِ فیلدِ شناخته‌ای که مدل `null` نمی‌نویسد
    val w = d.warehouseEntries.single()
    val debtor = d.debtors.single()
    assertTrue(runBlocking { s.save(d.copy(warehouseEntries = listOf(w.copy(notes = "ویرایش", costMinor = null)), debtors = listOf(debtor.copy(name = "تازه")))) })
    var out = tree(ledger)
    assertEquals(fut["futureCollection"], out["futureCollection"])
    assertEquals(JsonPrimitive(99_000), at(out, "warehouseEntries", "0", "futureCostMinor"))
    assertEquals(JsonPrimitive("ویرایش"), at(out, "warehouseEntries", "0", "notes"))
    assertNull("کلیدِ شناخته‌ای که این نسخه پاک کرد برنمی‌گردد", at(out, "warehouseEntries", "0", "costMinor"))
    assertEquals(JsonPrimitive("gold"), at(out, "debtors", "0", "futureTier"))
    assertEquals(JsonPrimitive("تازه"), at(out, "debtors", "0", "name"))
    assertEquals(JsonPrimitive("r-1"), at(out, "sales", "0", "payments", "0", "futureRef"))
    assertEquals(JsonPrimitive("a-1"), at(out, "setAside", "0", "futureAudit"))
    //  همان در بازِ دوباره (کلیدها از فایلِ تازه)
    val s2 = open()
    assertTrue(runBlocking { s2.save(s2.data.value.copy(expenses = emptyList())) })
    out = tree(ledger)
    assertEquals(JsonPrimitive(99_000), at(out, "warehouseEntries", "0", "futureCostMinor"))
    //  عنصرِ بی شناسه‌ای که عوض شد کلیدِ کهنه نمی‌گیرد
    val sale = s2.data.value.sales.single()
    val pays = sale.payments.orEmpty()
    assertTrue(pays.isNotEmpty())
    assertTrue(runBlocking { s2.save(s2.data.value.copy(sales = listOf(sale.copy(payments = pays.map { it.copy(amountMinor = it.amountMinor + 1) })))) })
    out = tree(ledger)
    assertNull(at(out, "sales", "0", "payments", "0", "futureRef"))
    //  ردیفِ حذف‌شده کلیدش را هم می‌برد
    assertTrue(runBlocking { s2.save(s2.data.value.copy(debtors = emptyList())) })
    assertEquals(0, tree(ledger).getValue("debtors").jsonArray.size)
    assertTrue(tree(ledger).toString().let { "gold" !in it })
    //  «رمزگذاریِ داده»: همان کلیدها در متنِ رمزدار
    assertTrue(AtRest.setWanted(ctx, true))
    val s3 = open()
    assertTrue(runBlocking { s3.save(s3.data.value.copy(expenses = emptyList(), productUnits = s3.data.value.productUnits + "جعبه")) })
    val plain = Json.parseToJsonElement(AtRest.readText(ctx, ledger)).jsonObject
    assertEquals(fut["futureCollection"], plain["futureCollection"])
    assertEquals(JsonPrimitive(99_000), at(plain, "warehouseEntries", "0", "futureCostMinor"))
  }

  @Test fun `همگام‌شده — کلیدِ نسخهٔ تازه‌تر نگه داشته نمی‌شود`() {
    state().cursor = 7L
    ledger.writeText(future().toString())
    val s = open()
    assertTrue(runBlocking { s.save(s.data.value.copy(expenses = emptyList())) })
    val out = tree(ledger)
    assertNull(out["futureCollection"])
    assertNull(at(out, "warehouseEntries", "0", "futureCostMinor"))
    assertFalse(out.toString().contains("futureRef"))
  }
}
