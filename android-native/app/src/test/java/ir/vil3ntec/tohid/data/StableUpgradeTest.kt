package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 *  ارتقا از آخرین نسخهٔ پایدار (`Tohid-Native-3.2.337.apk`، ساخته از `93800f41`، ۱۴۰۵/۰۷/۲۱) به همین کد.
 *
 *  هر دکانی که امروز پایدار دارد با دکمهٔ «انتشارِ پایدار» یک‌جا این‌جا می‌آید؛ پس دفتر و صفِ همگام‌سازی‌ای
 *  که آن ساخت روی دیسک گذاشته باید بی هیچ کم‌وکاستی خوانده شود. ⛔ `ShopStore.load` خطای خواندنِ دفترِ ساده
 *  را بی‌صدا «دفترِ خالی» می‌کند و نخستین ذخیره رویش می‌نویسد — یک فیلدِ تازهٔ بی پیش‌فرض یا یک نوعِ
 *  عوض‌شده یعنی پاک شدنِ دفترِ همهٔ دکان‌ها.
 *
 *  پرونده‌های `src/test/resources/upgrade/stable-3.2.337/` با **خودِ کدِ آن ساخت** نوشته شده‌اند، نه با دست:
 *  `Models.kt` و `sync/v1/{LedgerDiff,OpHash,SyncOp,Ulid}.kt` ِ `93800f41` (`git show 93800f41:<path>`) با
 *  همان `Json` ِ `ShopStore` ِ آن روز کامپایل و یک دفترِ نمونه (همهٔ جدول‌ها و همهٔ فیلدها، فروشِ نسیه با
 *  تخفیف و مرجوعی، فروشِ لغوشده، قلمِ آزاد، واحدِ فروشِ دوم، اصلاحِ موجودی، خرید با بدهی، صندوق) ساخته شد:
 *   - `shop-data.json` — همان `shop-data.json` ِ دیسک؛
 *   - `shadow.json` — سایهٔ همگام‌سازی (`tohid-sync-v1 › shadow`) پس از ثبتِ فروشِ آفلاینِ `s2`؛
 *   - `oplog.json` — صفِ `oplog-<حساب>.json` با پنج op ِ فرستاده‌نشدهٔ همان فروش؛
 *   - `figures.txt` — موجودی، بدهی و گزارشِ فروشی که آن ساخت نشان می‌داد (رونوشتِ خط‌به‌خطِ
 *     `ShopStore.stock/debt/supplierDebt` و `ReportEngine.sales` ِ همان کامیت).
 *  این پرونده‌ها هرگز عوض نمی‌شوند: نسخهٔ پایدارِ منتشرشده عوض نمی‌شود.
 */
class StableUpgradeTest {

  private val dir: File =
    generateSequence(File("").absoluteFile) { it.parentFile }
      .map { File(it, "android-native/app/src/test/resources/upgrade/stable-3.2.337") }
      .firstOrNull { File(it, "shop-data.json").isFile }
      ?: error("پوشهٔ upgrade/stable-3.2.337 پیدا نشد (cwd=${File("").absoluteFile})")

  private fun text(name: String) = File(dir, name).readText(Charsets.UTF_8)

  /** همان `Json` ِ `ShopStore` (دیروز و امروز یکی) */
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  private val oldText: String by lazy { text("shop-data.json") }
  private val ledger: ShopData by lazy { json.decodeFromString<ShopData>(oldText) }

  @Test
  fun `دفتر پایدار قدیم با همه ردیف ها و مقدارها خوانده می شود`() {
    val d = ledger
    assertEquals(3, d.products.size)
    assertEquals(4, d.warehouseEntries.size)
    assertEquals(3, d.sales.size)
    assertEquals(5, d.saleItems.size)
    assertEquals(1, d.saleReturns.size)
    assertEquals(2, d.debtors.size)
    assertEquals(4, d.transactions.size)
    assertEquals(2, d.expenses.size)
    assertEquals(2, d.suppliers.size)
    assertEquals(2, d.purchases.size)
    assertEquals(1, d.supplierPayments.size)
    assertEquals(7, d.stockMovements.size)
    assertEquals(1, d.priceHistory.size)
    assertEquals(5, d.auditLog.size)
    assertEquals(1004, d.nextInvoiceNo)
    assertTrue("انترنت" in d.expenseCategories)

    val rice = d.products.first { it.id == "p1" }
    assertEquals("گرم", rice.saleUnit)
    assertEquals(0.001, rice.unitFactor, 0.0)
    assertEquals(listOf("6291234567890", "2000000000017"), rice.barcodes)
    assertTrue(rice.photo)
    assertEquals(false, d.products.first { it.id == "p3" }.active)
    assertEquals("2027-04-01", d.warehouseEntries.first { it.id == "w1" }.expiryDate)
    assertTrue(d.warehouseEntries.first { it.id == "w3" }.isAdjustment)
    assertEquals("مشتری پشیمان شد", d.sales.first { it.id == "s3" }.cancelReason)
    assertEquals("کیسهٔ پلاستیکی", d.saleItems.first { it.id == "si4" }.name)
    assertEquals(2.0, d.saleItems.first { it.id == "si1" }.returnedQty, 0.0)
    assertEquals(2000.0, d.auditLog.first { it.id == "a1" }.amount, 0.0)
    assertEquals(1, d.purchases.first { it.id == "pu1" }.invoiceNo)
  }

  /**
   *  `ShopStore` کلِ فایل را از نو می‌نویسد؛ هر کلیدِ قدیمی که مدلِ امروز نشناسد یا مقدارش عوض شود، با
   *  نخستین ذخیرهٔ پس از به‌روزرسانی برای همیشه می‌رود. کلیدِ تازه (با پیش‌فرض) آزاد است.
   */
  @Test
  fun `نخستین ذخیره پس از به روزرسانی هیچ کلید یا مقدار قدیمی را نمی اندازد`() {
    val before = json.parseToJsonElement(oldText).jsonObject
    val after = json.parseToJsonElement(json.encodeToString(ledger)).jsonObject
    val lost = ArrayList<String>()
    same(before, after, "", lost)
    assertEquals("این‌ها با ذخیرهٔ نخست گم یا عوض می‌شوند: $lost", emptyList<String>(), lost)
  }

  /** «عددی که فروشنده روی کاغذ دارد نباید با به‌روزرسانی عوض شود» (سرِ `ShopStore`) */
  @Test
  fun `موجودی بدهی و گزارش فروش همان عددهای پایدار قدیم است`() {
    val figures = text("figures.txt").lines().filter { it.isNotBlank() }.map { it.split(' ') }
    assertEquals(16, figures.size)
    for (minor in listOf(false, true)) {
      DebtMinorFlag.override = minor
      try {
        val report = ReportEngine.sales(ledger, "2026-10-01", "2026-10-31")
        for ((kind, key, value) in figures) {
          val want = value.toDouble()
          val got = when (kind) {
            "stock" -> ShopStore.stock(ledger, key)
            "debt" -> ShopStore.debt(ledger, key)
            "supplierDebt" -> ShopStore.supplierDebt(ledger, key)
            "sales" -> when (key) {
              "count" -> report.count.toDouble()
              "gross" -> report.gross
              "discount" -> report.discount
              "net" -> report.net
              "cogs" -> report.cogs
              "grossProfit" -> report.grossProfit
              "expenses" -> report.expenses
              "netProfit" -> report.netProfit
              "returnAmount" -> report.returnAmount
              else -> error("رقمِ ناشناخته $key")
            }
            else -> error("ردیفِ ناشناخته $kind")
          }
          assertEquals("$kind $key (debt_minor=$minor)", want, got, 1e-9)
        }
      } finally {
        DebtMinorFlag.override = null
      }
    }
  }

  /** فروشِ آفلاینی که پیش از به‌روزرسانی به سرور نرسید: همان op، همان `op_id` و همان هش ⇒ سرور تکراری را می‌شناسد */
  @Test
  fun `صف همگام سازی پایدار قدیم بیت به بیت همان op ها را می فرستد`() {
    val raw = (json.parseToJsonElement(text("oplog.json")) as JsonArray).map { it.jsonObject }
    assertEquals(5, raw.size)
    for (o in raw) {
      val op = SyncOp.fromJson(o)
      assertNotNull("خوانده نشد: $o", op)
      assertEquals(o, op!!.toJson())
      assertEquals((o["hash"] as JsonPrimitive).content, op.hash)
    }
  }

  /**
   *  شروعِ نخستِ پس از به‌روزرسانی (`SyncCommit.start` ِ موتورها): سایهٔ بی مهرِ آن ساخت درمان می‌شود، op های
   *  صف دست نمی‌خورند و نشانِ «ساختِ پیشین» می‌گیرند، و ذخیرهٔ بی تغییر هیچ op ِ ردیفی نمی‌سازد — نه
   *  «update» ِ فیلدهای تازه با پیش‌فرض روی همهٔ ردیف‌ها (که مقدارِ سرور را می‌پوشاند)، نه حذف. تنها چیزِ تازه
   *  فهرست‌های نام (`LIST_COLLECTIONS`) است که آن ساخت هرگز نمی‌فرستاد: یک بار، فقط درج.
   */
  @Test
  fun `شروع نخست پس از به روزرسانی صف را نگه می دارد و op ردیفی نمی سازد`() {
    val queued = (json.parseToJsonElement(text("oplog.json")) as JsonArray).map { SyncOp.fromJson(it.jsonObject)!! }
    val disk = Disk(shadow = text("shadow.json"), queue = ArrayList(queued))
    assertTrue("سایهٔ آن ساخت مهر ندارد", !LedgerDiff.isStamped(json.parseToJsonElement(disk.shadow).jsonObject))
    val session = SyncCommit(SyncCommit.Journal(Files.createTempDirectory("upgrade").toFile().resolve("sync-journal.json")), disk)

    var legacy = ""
    val start = session.start(ledger) { legacy = it }
    assertTrue("درمان", start.healed)
    assertEquals(queued.maxOf { it.opId }, start.legacyUpTo)
    assertEquals(start.legacyUpTo, legacy)
    assertEquals("صفِ پیشین دست نخورد", queued, disk.queue)

    session.record(ledger)
    val fresh = disk.queue.drop(queued.size)
    val rows = fresh.filter { it.table !in LedgerDiff.LIST_COLLECTIONS }
    assertEquals("op ِ ردیفی پس از ذخیرهٔ بی تغییر: ${rows.map { "${it.type} ${it.table}/${it.rowId} ${it.fields}" }}", emptyList<SyncOp>(), rows)
    assertTrue(fresh.all { it.type == SyncOp.INSERT })
    val names = (ledger.productCategories + ledger.productUnits + ledger.expenseCategories).toSet()
    assertEquals(names, fresh.map { (it.fields!!["name"] as JsonPrimitive).content }.toSet())

    //  کارِ واقعیِ پس از به‌روزرسانی همان یک op است
    val n = disk.queue.size
    session.record(ledger.copy(debtors = ledger.debtors + Debtor(id = "d3", name = "مشتری تازه", createdAt = 1L)))
    assertEquals(listOf("insert debtors/d3"), disk.queue.drop(n).map { "${it.type} ${it.table}/${it.rowId}" })
  }

  /** ادغامِ مشتری (CRM-10) در دفترِ آن ساخت نبود ⇒ sweep ِ شروع چیزی نمی‌نویسد */
  @Test
  fun `جاروی شروع روی دفتر پایدار قدیم چیزی نمی نویسد`() {
    assertNull(CustomerMerge.sweep(ledger, today = "2026-11-07", now = 1L) { "x" })
  }

  private class Disk(var shadow: String, val queue: ArrayList<SyncOp>) : SyncCommit.Parts {
    override fun shadow(): String = shadow
    override fun commit(shadow: String, cursor: Long?) { this.shadow = shadow }
    override fun queued(): Set<String> = queue.mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) { queue += ops }
  }

  /** هر کلیدِ [a] در [b] با همان مقدار (عدد با مقدار، نه با نوشتار)؛ کلیدِ تازهٔ [b] آزاد است */
  private fun same(a: JsonElement, b: JsonElement?, path: String, out: MutableList<String>) {
    when (a) {
      is JsonObject -> {
        if (b !is JsonObject) { out += "$path (شکل)"; return }
        for ((k, v) in a) {
          if (k !in b) out += "$path/$k" else same(v, b[k], "$path/$k", out)
        }
      }
      is JsonArray -> {
        if (b !is JsonArray || b.size != a.size) { out += "$path (شمار)"; return }
        a.forEachIndexed { i, v -> same(v, b[i], "$path[$i]", out) }
      }
      is JsonNull -> if (b != null && b !is JsonNull) out += "$path (null ⇐ $b)"
      is JsonPrimitive -> {
        val p = b as? JsonPrimitive
        val equal = when {
          p == null || p is JsonNull -> false
          !a.isString && !p.isString && a.doubleOrNull != null -> a.doubleOrNull == p.doubleOrNull
          else -> a.isString == p.isString && a.content == p.content
        }
        if (!equal) out += "$path ($a ⇐ $b)"
      }
    }
  }
}
