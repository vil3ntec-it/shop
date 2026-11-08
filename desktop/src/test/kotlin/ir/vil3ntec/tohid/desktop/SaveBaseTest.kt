package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.LedgerMergeFlag
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.sync.v3.FileV3Store
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.sync.v3.V3Meta
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  پایهٔ صریحِ ذخیره — `ShopStore.save(base, next)` (۱۴۰۵/۰۸/۱۸، `docs/DATA-SAFETY-fa.md` «دو ذخیره از یک صفحهٔ کهنه»).
 *
 *  هر صفحه دفتری را که با آن ساخت ([base]) همراهِ دفترِ تازه می‌دهد؛ پایه دیگر از روی شیءهای مشترک حدس زده نمی‌شود.
 *  پیش از این، دو ذخیره از **یک** دفترِ کهنهٔ صفحه بی هیچ Pull ِ میانه — دو کلیک پیش از تازه شدنِ صفحه، یا نوشتنِ
 *  پس‌زمینهٔ همین نمونه (سابقهٔ کشو) میانِ ساختن و ذخیره — دومی اولی را می‌برد و `record` برایش DELETE می‌فرستاد؛
 *  با `shop.ledger_merge` روشن یا خاموش. با پرچمِ خاموشِ صریح سدِ نقش (A16) هم در `save` اجرا نمی‌شد.
 *
 *  هر سناریو با پرچمِ پیش‌فرض (روشن) و با خاموشِ صریح؛ بی‌اینترنت (صفِ نرفته، باز کردنِ دوباره)، Sync v3، و نقشِ کارمند.
 *  «سرخ ⇐ سبز»: همین آزمون‌ها روی کدِ پیشین (ذخیرهٔ بی پایه) سرخ دیده شدند (PR).
 */
class SaveBaseTest {

  private val ctx = DesktopContext
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val engine get() = SyncV1Engine.of(ctx)
  private lateinit var account: String

  private fun clean() {
    AtRest.setWanted(ctx, false)
    ctx.filesDir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("sync-v3-sbase") }
      .forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    LedgerMergeFlag.set(ctx, LedgerMergeFlag.DEFAULT_ON)
    LedgerMergeFlag.setBase(ctx, LedgerMergeFlag.BASE_DEFAULT_ON)
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    account = "sbase-${System.nanoTime()}"
    SyncStateV1.setCurrentAccount(ctx, account)
    engine.rebind()
  }

  @After fun after() {
    ShopRole.remember(ctx, ShopRole.OWNER)
    LedgerMergeFlag.set(ctx, LedgerMergeFlag.DEFAULT_ON)
    LedgerMergeFlag.setBase(ctx, LedgerMergeFlag.BASE_DEFAULT_ON)
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    SyncStateV1.setCurrentAccount(ctx, "")
    engine.rebind()
    clean()
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }
  private fun queued(): List<String> = OpQueue(ctx, account).all().map { "${it.table}:${it.rowId}:${it.type}" }
  private fun deletes(): List<String> = queued().filter { it.endsWith(":delete") }
  private fun save(store: ShopStore, base: ShopData, next: ShopData): Boolean = runBlocking { store.save(base, next) }

  private fun op(table: String, id: String, type: String, vararg f: Pair<String, Any>) = SyncOp(
    Ulid.next(), System.currentTimeMillis(), table, id, type,
    buildJsonObject { for ((k, v) in f) put(k, if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) },
  )

  /** همان دورِ کارگر: نمونهٔ تازه، `load`، Pull و نشاندن زیرِ قفلِ موتور (`SyncV1Worker.doWork`) */
  private fun workerPulls(ops: List<SyncOp>, cursor: Long = 7) {
    val w = open()
    engine.absorbRemote(ops, { w.ledgerNow() }, { w.applyFromServerNow(it) }, cursor)
  }

  private val p1 = Product(id = "sbase-p1", name = "برنج", salePrice = 120.0)
  private val seed = ShopData(products = listOf(p1))
  private fun sale(id: String) = Sale(id = id, finalTotal = 120.0, createdAt = 1_800_000_000_000L, date = "2026-11-08")
  private fun ShopData.plus(s: Sale) = copy(sales = sales + s)

  /** پرچمِ `shop.ledger_merge`: پیش‌فرض (روشن) یا خاموشِ صریحِ سرور */
  private fun mode(on: Boolean) = LedgerMergeFlag.set(ctx, on)

  /** صفحه با دفتری که در دست دارد: نخستین ذخیره سایه را می‌گذارد (بی op) */
  private fun screen(start: ShopData = seed): ShopStore {
    val ui = open()
    assertTrue(save(ui, ui.data.value, start))
    assertEquals(emptyList<String>(), queued())
    return ui
  }

  /** `ShopStore.roleBlocked` در میانِ [act] */
  private fun roleBlocks(store: ShopStore, act: suspend () -> Unit): List<Pair<String, String>> = runBlocking {
    val got = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
    val job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { store.roleBlocked.collect { got += it } }
    act()
    delay(150)
    job.cancel()
    got.toList()
  }

  /* ------------------------ دو ذخیره از یک صفحهٔ کهنه، بی Pull ------------------------ */

  private fun twoStaleSaves(on: Boolean) {
    mode(on)
    val ui = screen()
    val inHand = ui.data.value                      // صفحه هنوز همین را نشان می‌دهد (دو کلیک پیش از تازه شدن)
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-a"))))
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-b"))))
    assertEquals("هیچ DELETE ای برای فروشِ نخست", emptyList<String>(), deletes())
    assertEquals(listOf("sales:sbase-a:insert", "sales:sbase-b:insert"), queued())
    val disk = open().data.value
    assertEquals(setOf("sbase-a", "sbase-b"), disk.sales.map { it.id }.toSet())
    assertEquals(listOf("sbase-p1"), disk.products.map { it.id })
    assertEquals(disk, ui.data.value)
  }

  @Test fun `دو ذخیره از یک صفحهٔ کهنه بی Pull — هر دو فروش می‌مانند (پیش‌فرض)`() = twoStaleSaves(on = true)

  @Test fun `دو ذخیره از یک صفحهٔ کهنه بی Pull — هر دو فروش می‌مانند (خاموشِ صریح)`() = twoStaleSaves(on = false)

  /* ------------------------------- پول: دو پرداخت ------------------------------- */

  private val debtor = Debtor(id = "sbase-d1", name = "کریم", createdAt = 10)
  private val credit = Sale(
    id = "sbase-x", finalTotal = 500.0, paymentMethod = "debt", debtorId = "sbase-d1", paidAmount = 0.0, remaining = 500.0,
    debtGiven = 500.0, createdAt = 1_800_000_000_000L, date = "2026-11-08",
  )
  private fun pay(id: String, amount: Double) =
    DebtTransaction(id = id, debtorId = "sbase-d1", type = "receive", amount = amount, date = "2026-11-08")

  /** پرداختِ [amount] روی همان فاکتورِ قرضی، ساخته از [d] — همان کارِ صفحهٔ قرض‌داران */
  private fun paid(d: ShopData, id: String, amount: Double): ShopData = d.copy(
    sales = d.sales.map { x ->
      if (x.id != credit.id) x
      else x.copy(paidAmount = x.paidAmount + amount, remaining = x.remaining - amount, debtSettled = x.debtSettled + amount)
    },
    transactions = d.transactions + pay(id, amount),
  )

  private fun twoStalePayments(on: Boolean) {
    mode(on)
    val ui = screen(seed.copy(debtors = listOf(debtor), sales = listOf(credit)))
    val inHand = ui.data.value
    assertTrue(save(ui, inHand, paid(inHand, "sbase-t1", 100.0)))
    assertTrue(save(ui, inHand, paid(inHand, "sbase-t2", 50.0)))
    val x = open().data.value.sales.single { it.id == credit.id }
    assertEquals("۱۰۰ + ۵۰، نه فقط ۵۰", 150.0, x.paidAmount, 0.0)
    assertEquals(350.0, x.remaining, 0.0)
    assertEquals(150.0, x.debtSettled, 0.0)
    assertEquals(setOf("sbase-t1", "sbase-t2"), open().data.value.transactions.map { it.id }.toSet())
    assertEquals(emptyList<String>(), deletes())
  }

  @Test fun `دو پرداخت از یک صفحهٔ کهنه — هر دو جمع می‌شوند (پیش‌فرض)`() = twoStalePayments(on = true)

  @Test fun `دو پرداخت از یک صفحهٔ کهنه — هر دو جمع می‌شوند (خاموشِ صریح)`() = twoStalePayments(on = false)

  private fun chain(on: Boolean, pull: Boolean) {
    mode(on)
    val ui = screen(seed.copy(debtors = listOf(debtor), sales = listOf(credit)))
    val inHand = ui.data.value
    if (pull) workerPulls(listOf(op("products", "sbase-p2", SyncOp.INSERT, "name" to "شکر", "salePrice" to 90)))
    val first = paid(inHand, "sbase-t1", 100.0)
    assertTrue(save(ui, inHand, first))
    //  صفحه از نتیجهٔ ذخیرهٔ خودش ادامه می‌دهد: پایه همان دفترِ فرستاده است
    assertTrue(save(ui, first, paid(first, "sbase-t2", 50.0)))
    val disk = open().data.value
    assertEquals("۱۰۰ + ۵۰ = ۱۵۰، پرداختِ پیشین دوباره جمع نمی‌شود", 150.0, disk.sales.single().paidAmount, 0.0)
    assertEquals(listOf("sbase-t1", "sbase-t2"), disk.transactions.map { it.id })
    if (pull) assertEquals(listOf("sbase-p1", "sbase-p2"), disk.products.map { it.id })
    assertEquals(emptyList<String>(), deletes())
  }

  /**
   *  Pull با پرچمِ خاموش این‌جا نیست: خاموش یعنی صفحه نوشتنِ کارگر را نمی‌بیند و دفترِ کهنه‌اش را کامل می‌نویسد — همان
   *  خاموش‌کنِ `shop.ledger_merge` (`StaleSaveTest`)؛ پایهٔ صریح فقط نوشتن‌های همین نمونه را می‌بیند.
   */
  @Test fun `زنجیره از نتیجهٔ ذخیرهٔ خود — بی Pull روشن و خاموش، پس از Pull روشن`() {
    for ((on, pull) in listOf(true to false, false to false, true to true)) {
      after(); before()
      chain(on, pull)
    }
  }

  /* ------------------- نوشتنِ پس‌زمینهٔ همین نمونه میانِ ساختن و ذخیره ------------------- */

  private fun backgroundThenStale(on: Boolean) {
    mode(on)
    val ui = screen()
    val inHand = ui.data.value
    //  کشوی بی فروش / چاپِ دوباره: ردیفِ سابقه روی دفترِ همان لحظه (`appendAudit`) — صفحه هنوز دفترِ پیش از آن را دارد
    assertTrue(runBlocking { ui.appendAudit { AuditEntry(id = "sbase-au", type = "no_sale", date = "2026-11-08", createdAt = 1) } })
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-c"))))
    val disk = open().data.value
    assertEquals("ردیفِ سابقه نمی‌افتد", listOf("sbase-au"), disk.auditLog.map { it.id })
    assertEquals(listOf("sbase-c"), disk.sales.map { it.id })
    assertEquals(emptyList<String>(), deletes())
  }

  @Test fun `نوشتنِ پس‌زمینهٔ همین نمونه و سپس ذخیرهٔ صفحهٔ کهنه — هیچ‌کدام نمی‌افتد (پیش‌فرض)`() = backgroundThenStale(on = true)

  @Test fun `نوشتنِ پس‌زمینهٔ همین نمونه و سپس ذخیرهٔ صفحهٔ کهنه — هیچ‌کدام نمی‌افتد (خاموشِ صریح)`() = backgroundThenStale(on = false)

  /* ---------------------------------- بی‌اینترنت ---------------------------------- */

  private val otherPurchase = listOf(op("purchases", "sbase-pu1", SyncOp.INSERT, "productId" to "sbase-p1", "quantity" to 5, "unitPrice" to 90))

  @Test fun `بی‌اینترنت — دو ذخیرهٔ کهنهٔ صفِ نرفته، باز کردنِ دوباره، Pull ِ دیر و ذخیرهٔ سوم — هیچ فروشی گم نمی‌شود`() {
    val ui = screen()
    val inHand = ui.data.value
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-o1"))))
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-o2"))))
    //  برنامه بسته و دوباره باز شد: دفتر و صف همان
    val again = open()
    assertEquals(setOf("sbase-o1", "sbase-o2"), again.data.value.sales.map { it.id }.toSet())
    assertEquals(listOf("sales:sbase-o1:insert", "sales:sbase-o2:insert"), queued())
    //  اینترنت برگشت: کارگر خریدِ دستگاهِ دیگر را آورد؛ صفحه هنوز همان دفترِ نخست را دارد
    workerPulls(otherPurchase + op("products", "sbase-p3", SyncOp.INSERT, "name" to "روغن", "salePrice" to 300))
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-o3"))))
    assertEquals(listOf("sales:sbase-o1:insert", "sales:sbase-o2:insert", "sales:sbase-o3:insert"), queued())
    val disk = open().data.value
    assertEquals(setOf("sbase-o1", "sbase-o2", "sbase-o3"), disk.sales.map { it.id }.toSet())
    assertEquals(listOf("sbase-p1", "sbase-p3"), disk.products.map { it.id })
    assertEquals(listOf("sbase-pu1"), disk.purchases.map { it.id })
  }

  /* ---------------------------------- Sync v3 ---------------------------------- */

  @Test fun `Sync v3 — دو ذخیره از یک صفحهٔ کهنه هر دو فروش را در صف می‌گذارند`() {
    val dir = File(ctx.filesDir, "sync-v3-${OpQueue.fileKey(account)}")
    FileV3Store(dir).saveMeta(V3Meta(initialized = true, shadow = LedgerDiff.shadowOf(seed).toString(), cursor = 7))
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    AtRest.writeText(ctx, ledger, Json.encodeToString(ShopData.serializer(), seed))
    val ui = open()
    val inHand = ui.data.value
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-v3a"))))
    assertTrue(save(ui, inHand, inHand.plus(sale("sbase-v3b"))))
    val entries = FileV3Store(dir).loadQueue()?.outbox.orEmpty()
    assertEquals(setOf("sales:sbase-v3a", "sales:sbase-v3b"), entries.map { "${it.table}:${it.rowId}" }.toSet())
    assertEquals(setOf("sbase-v3a", "sbase-v3b"), open().data.value.sales.map { it.id }.toSet())
    dir.deleteRecursively()
  }

  /* --------------------------------- نقشِ کارمند --------------------------------- */

  private fun cashierTwoSales(on: Boolean) {
    mode(on)
    val ui = screen()
    ShopRole.remember(ctx, ShopRole.CASHIER)
    val inHand = ui.data.value
    var a = false
    var b = false
    val refused = roleBlocks(ui) {
      a = ui.save(inHand, inHand.plus(sale("sbase-k1")))
      b = ui.save(inHand, inHand.plus(sale("sbase-k2")))
    }
    assertEquals(emptyList<Pair<String, String>>(), refused)
    assertTrue(a && b)
    assertEquals(listOf("sales:sbase-k1:insert", "sales:sbase-k2:insert"), queued())
  }

  @Test fun `صندوق‌دار — دو فروش از یک صفحهٔ کهنه هر دو ثبت می‌شوند (پیش‌فرض)`() = cashierTwoSales(on = true)

  @Test fun `صندوق‌دار — دو فروش از یک صفحهٔ کهنه هر دو ثبت می‌شوند (خاموشِ صریح)`() = cashierTwoSales(on = false)

  private fun refusedWrite(on: Boolean, role: String, change: (ShopData) -> ShopData) {
    mode(on)
    val ui = screen()
    ShopRole.remember(ctx, role)
    val now = ui.data.value
    var ok = true
    val refused = roleBlocks(ui) { ok = ui.save(now, change(now)) }
    assertFalse("$role نباید بنویسد", ok)
    assertEquals(1, refused.size)
    assertEquals(emptyList<String>(), queued())
    assertEquals(now, open().data.value)
  }

  @Test fun `سدِ نقش با خاموشِ صریح هم — خریدِ صندوق‌دار پیش از دیسک بسته است`() =
    refusedWrite(on = false, ShopRole.CASHIER) { it.copy(purchases = listOf(Purchase(id = "sbase-pu9", productId = "sbase-p1"))) }

  @Test fun `سدِ نقش با خاموشِ صریح هم — فروشِ انباردار پیش از دیسک بسته است`() =
    refusedWrite(on = false, ShopRole.STOREKEEPER) { it.plus(sale("sbase-sk")) }

  @Test fun `سدِ نقش با پیش‌فرض — همان دو رد`() {
    refusedWrite(on = true, ShopRole.CASHIER) { it.copy(purchases = listOf(Purchase(id = "sbase-pu8", productId = "sbase-p1"))) }
    after(); before()
    refusedWrite(on = true, ShopRole.STOREKEEPER) { it.plus(sale("sbase-sk2")) }
  }

  @Test fun `سدِ نقش با خاموشِ صریح — صندوق‌دار پس از نوشتنِ پس‌زمینهٔ همین نمونه فروش را ثبت می‌کند و سابقه می‌ماند`() {
    mode(false)
    val ui = screen()
    ShopRole.remember(ctx, ShopRole.CASHIER)
    val inHand = ui.data.value
    assertTrue(runBlocking { ui.appendAudit { AuditEntry(id = "sbase-au2", type = "no_sale", date = "2026-11-08", createdAt = 2) } })
    var ok = false
    val refused = roleBlocks(ui) { ok = ui.save(inHand, inHand.plus(sale("sbase-k3"))) }
    assertEquals("فقط تغییرِ خودِ صفحه سنجیده می‌شود", emptyList<Pair<String, String>>(), refused)
    assertTrue(ok)
    val disk = open().data.value
    assertEquals(listOf("sbase-k3"), disk.sales.map { it.id })
    assertEquals(listOf("sbase-au2"), disk.auditLog.map { it.id })
  }

  /* ------------------------- بی تعارضِ تازه رفتار همان است ------------------------- */

  /** همان چهار ذخیره، هر بار از دفترِ همین حالا؛ [explicit] ⇒ با پایه، وگرنه راهِ پیشینِ بی پایه */
  private fun noConflictRun(explicit: Boolean): Pair<ShopData, List<String>> {
    val ui = screen(seed.copy(debtors = listOf(debtor), sales = listOf(credit)))
    val steps: List<(ShopData) -> ShopData> = listOf(
      { it.plus(sale("sbase-n1")) },
      { paid(it, "sbase-nt", 40.0) },
      { d -> d.copy(products = d.products.map { it.copy(salePrice = 130.0) }) },
      { d -> d.copy(products = d.products.map { it.copy(minStock = 2.0) }) },
    )
    for (step in steps) {
      val d = ui.data.value
      val next = step(d)
      assertTrue(if (explicit) save(ui, d, next) else runBlocking { ui.save(next) })
      //  همان شیءِ دفترِ صفحه نوشته می‌شود — نه دفترِ ادغام‌شدهٔ تازه
      assertTrue("بی تعارض دفترِ صفحه خودش نوشته می‌شود", ui.data.value === next)
    }
    return open().data.value to queued()
  }

  @Test fun `بی تعارض — ذخیره با پایه همان دفتر و همان صفِ راهِ پیشین را می‌سازد، روشن و خاموش`() {
    for (on in listOf(true, false)) {
      after(); before()
      mode(on)
      val explicit = noConflictRun(explicit = true)
      after(); before()
      mode(on)
      val legacy = noConflictRun(explicit = false)
      assertEquals(legacy.first, explicit.first)
      assertEquals(legacy.second, explicit.second)
      assertTrue(explicit.second.toString(), explicit.second.isNotEmpty() && explicit.second.none { it.endsWith(":delete") })
    }
  }

  @Test fun `پاک کردنِ همه جایگزینیِ کامل است، روشن و خاموش`() {
    for (on in listOf(true, false)) {
      after(); before()
      mode(on)
      val ui = screen()
      assertTrue(runBlocking { ui.replaceAll(ShopData()) })
      assertEquals(ShopData(), open().data.value)
    }
  }

  /* ---------------------- خاموش‌کنِ اضطراری: `shop.save_base` ---------------------- */

  @Test fun `پرچمِ پایه — پیش‌فرض روشن؛ پاسخِ سرور فقط بولیِ صریح`() {
    assertTrue(LedgerMergeFlag.BASE_DEFAULT_ON)
    assertTrue(LedgerMergeFlag.base(ctx))
    fun body(s: String) = Json.parseToJsonElement(s) as kotlinx.serialization.json.JsonObject
    assertEquals(null, LedgerMergeFlag.baseFrom(body("""{"enabled":true,"v":2}""")))
    assertEquals(false, LedgerMergeFlag.baseFrom(body("""{"enabled":true,"v":2,"base":false}""")))
    assertEquals(true, LedgerMergeFlag.baseFrom(body("""{"base":true}""")))
    assertEquals(null, LedgerMergeFlag.baseFrom(body("""{"base":"false"}""")))
    assertEquals(null, LedgerMergeFlag.baseFrom(body("""{"base":{"x":1}}""")))
    assertEquals(null, LedgerMergeFlag.baseFrom(body("""{"base":null}""")))
    LedgerMergeFlag.setBase(ctx, false)
    assertFalse(LedgerMergeFlag.base(ctx))
  }

  /** همان دو ذخیرهٔ کهنه و خریدِ صندوق‌دار؛ [explicit] ⇒ `save(base, next)`، وگرنه راهِ پیشینِ بی پایه */
  private fun switchRun(on: Boolean, explicit: Boolean): Pair<ShopData, List<String>> {
    mode(on)
    val ui = screen()
    val inHand = ui.data.value
    for (id in listOf("sbase-w1", "sbase-w2")) {
      val next = inHand.plus(sale(id))
      assertTrue(if (explicit) save(ui, inHand, next) else runBlocking { ui.save(next) })
    }
    ShopRole.remember(ctx, ShopRole.CASHIER)
    val now = ui.data.value
    val bought = now.copy(purchases = listOf(Purchase(id = "sbase-pw", productId = "sbase-p1")))
    if (explicit) save(ui, now, bought) else runBlocking { ui.save(bought) }
    ShopRole.remember(ctx, ShopRole.OWNER)
    return open().data.value to queued()
  }

  @Test fun `خاموش‌کنِ اضطراری — base=false همان رفتارِ پیشین بیت‌به‌بیت، روشن و خاموش`() {
    for (on in listOf(true, false)) {
      after(); before()
      LedgerMergeFlag.setBase(ctx, false)
      val switched = switchRun(on, explicit = true)
      after(); before()
      LedgerMergeFlag.setBase(ctx, false)
      val legacy = switchRun(on, explicit = false)
      assertEquals(legacy.first, switched.first)
      assertEquals(legacy.second, switched.second)
      //  روشن (پیش‌فرض) همان سناریو هر دو فروش را نگه می‌دارد
      after(); before()
      val fixed = switchRun(on, explicit = true)
      assertEquals(setOf("sbase-w1", "sbase-w2"), fixed.first.sales.map { it.id }.toSet())
      assertTrue(fixed.second.toString(), fixed.second.none { it.endsWith(":delete") })
      assertEquals("خریدِ صندوق‌دار در هر دو حالت بسته", emptyList<String>(), fixed.first.purchases.map { it.id })
    }
  }
}
