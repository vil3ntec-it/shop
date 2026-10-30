package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.CustomerMerge
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.LedgerMergeFlag
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
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
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  ذخیرهٔ کهنه — دو نمونهٔ `ShopStore` روی یک پوشه، همان که برنامه دارد: صفحه (`MainActivity` /
 *  `desktop/Main.kt`) و کارگرِ پس‌زمینه (`SyncV1Worker` هر بار نمونهٔ تازه می‌سازد).
 *
 *  پیش‌تر کارگر opهای سرور را روی دیسک می‌نشاند و سایه را جلو می‌برد ولی دفترِ صفحه کهنه می‌ماند؛
 *  ذخیرهٔ بعدیِ صفحه کلِ دفترِ کهنه را می‌نوشت و `record` برای ردیفِ تازه DELETE و برای ویرایشِ
 *  تازه برگرداندن به سرور می‌فرستاد. همین سناریوها با Sync v1 و v3 و با رمزگذاریِ روشن و خاموش.
 *
 *  ⛔ همه پشتِ پرچمِ `shop.ledger_merge` (`LedgerMergeFlag`، پیش‌فرض خاموش)؛ این‌جا روشن، جز آزمونِ «پرچمِ
 *  خاموش» که همان رفتارِ `main` را قفل می‌کند.
 */
class StaleSaveTest {

  private val ctx = DesktopContext
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val kek get() = File(File(ctx.root, "keys"), "atrest-kek.key")
  private val engine get() = SyncV1Engine.of(ctx)
  private lateinit var account: String

  private fun clean() {
    AtRest.setWanted(ctx, false)
    ctx.filesDir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("ledger-stale") || it.name.startsWith("sync-v3-stale") }
      .forEach { it.deleteRecursively() }
    File(ctx.filesDir, "atrest").deleteRecursively()
    kek.delete()
    AtRest.keyring(ctx).forget()
  }

  @Before fun before() {
    clean()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    //  هر آزمون حسابِ خودش: سایه، صف و cursor ِ تازه
    account = "stale-${System.nanoTime()}"
    SyncStateV1.setCurrentAccount(ctx, account)
    engine.rebind()
    LedgerMergeFlag.set(ctx, true)
  }

  @After fun after() {
    LedgerMergeFlag.set(ctx, false)
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    SyncStateV1.setCurrentAccount(ctx, "")
    engine.rebind()
    clean()
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }
  private fun queued(): List<String> = OpQueue(ctx, account).all().map { "${it.table}:${it.rowId}:${it.type}" }

  private val p1 = Product(id = "stale-p1", name = "برنج", salePrice = 120.0)
  private val seed = ShopData(products = listOf(p1))
  private val sale = Sale(id = "stale-s1", finalTotal = 120.0, createdAt = 1_800_000_000_000L)

  private fun op(table: String, id: String, type: String, vararg f: Pair<String, Any>) = SyncOp(
    Ulid.next(), System.currentTimeMillis(), table, id, type,
    buildJsonObject { for ((k, v) in f) put(k, if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) },
  )

  /** دستگاهِ دیگر: کالای تازه، و نامِ تازهٔ برنج */
  private val fromOther = listOf(
    op("products", "stale-p2", SyncOp.INSERT, "name" to "شکر", "salePrice" to 90),
    op("products", "stale-p1", SyncOp.UPDATE, "name" to "برنج اعلا"),
  )

  /** همان دورِ کارگر: نمونهٔ تازه، `load`، Pull و نشاندن زیرِ قفلِ موتور (`SyncV1Worker.doWork`) */
  private fun workerPulls(ops: List<SyncOp>, cursor: Long = 7) {
    val w = open()
    engine.absorbRemote(ops, { w.ledgerNow() }, { w.applyFromServerNow(it) }, cursor)
  }

  /** صفحه با دفتری که در دست دارد: نخستین ذخیره سایه را می‌گذارد (بی op) */
  private fun screen(encrypted: Boolean = false): ShopStore {
    if (encrypted) assertTrue(AtRest.setWanted(ctx, true))
    val ui = open()
    assertTrue(runBlocking { ui.save(seed) })
    if (encrypted) {
      assertTrue(runBlocking { ui.convergeAtRest() }.ok)
      assertTrue(AtRestEnvelope.isSealed(ledger))
    }
    assertEquals(emptyList<String>(), queued())
    return ui
  }

  private fun reproduce(encrypted: Boolean) {
    val ui = screen(encrypted)
    val inHand = ui.data.value                      // دفتری که صفحه با آن فروش را می‌سازد
    workerPulls(fromOther)
    assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale)) })

    assertEquals("فقط فروشِ صفحه — نه DELETE ِ کالای تازه، نه برگرداندنِ نامِ تازه", listOf("sales:stale-s1:insert"), queued())
    val disk = open().data.value
    assertEquals(listOf("برنج اعلا", "شکر"), disk.products.map { it.name })
    assertEquals(listOf("stale-s1"), disk.sales.map { it.id })
    assertEquals(disk, ui.data.value)
    if (encrypted) assertTrue(AtRestEnvelope.isSealed(ledger))
  }

  @Test fun `بازتولید — ذخیرهٔ صفحه پس از Pull ِ کارگر هیچ ردیفِ تازه‌ای را حذف یا برنمی‌گرداند`() = reproduce(encrypted = false)

  @Test fun `پرچمِ خاموش (پیش‌فرض) — همان رفتارِ main، دفترِ صفحه کهنه می‌ماند و ذخیره‌اش کامل نوشته می‌شود`() {
    LedgerMergeFlag.set(ctx, false)
    val ui = screen()
    val inHand = ui.data.value
    workerPulls(fromOther)
    assertEquals("صفحه نوشتنِ کارگر را نمی‌گیرد", listOf("برنج"), ui.data.value.products.map { it.name })
    assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale)) })
    //  همان opهای `main`: کالای تازه DELETE و نامِ تازه برگردانده — همان باگی که پرچمِ روشن درمان می‌کند
    assertEquals(setOf("sales:stale-s1:insert", "products:stale-p2:delete", "products:stale-p1:update"), queued().toSet())
    assertEquals(listOf("برنج"), open().data.value.products.map { it.name })
  }

  @Test fun `همان، با رمزگذاریِ روشن`() = reproduce(encrypted = true)

  @Test fun `صفحه پس از نوشتنِ کارگر دفترِ تازه را می‌بیند`() {
    val ui = screen()
    workerPulls(fromOther)
    assertEquals(listOf("برنج اعلا", "شکر"), ui.data.value.products.map { it.name })
  }

  @Test fun `Pull ِ کارگر فروشی را که صفحه پس از بارِ کارگر ثبت کرد از دیسک پاک نمی‌کند`() {
    val ui = screen()
    val w = open()                                    // کارگر پیش از فروش بار شد
    assertTrue(runBlocking { ui.save(ui.data.value.copy(sales = listOf(sale))) })
    engine.absorbRemote(fromOther, { w.ledgerNow() }, { w.applyFromServerNow(it) }, 7)
    assertEquals(listOf("stale-s1"), open().data.value.sales.map { it.id })
    //  و ذخیرهٔ بعدی چیزی را حذف نمی‌کند
    val now = ui.data.value
    assertTrue(runBlocking { ui.save(now.copy(products = now.products.map { if (it.id == "stale-p2") it.copy(minStock = 3.0) else it })) })
    assertEquals(listOf("sales:stale-s1:insert", "products:stale-p2:update"), queued())
  }

  @Test fun `یک ردیف، هم‌زمان — فیلدهای جدا هر دو می‌مانند و فیلدِ یکسان مالِ کاربر است`() {
    val ui = screen()
    val inHand = ui.data.value
    workerPulls(listOf(op("products", "stale-p1", SyncOp.UPDATE, "name" to "برنج اعلا", "salePrice" to 200)))
    val edited = inHand.copy(products = inHand.products.map { it.copy(salePrice = 150.0, notes = "قفسهٔ دوم") })
    assertTrue(runBlocking { ui.save(edited) })
    val p = open().data.value.products.single()
    assertEquals("برنج اعلا", p.name)
    assertEquals(150.0, p.salePrice, 0.0)
    assertEquals("قفسهٔ دوم", p.notes)
    val sent = OpQueue(ctx, account).all().single()
    assertEquals(SyncOp.UPDATE, sent.type)
    assertEquals(setOf("salePrice", "notes"), sent.fields!!.keys)
    assertEquals("150", sent.fields!!["salePrice"]!!.jsonPrimitive.content)
  }

  @Test fun `حذفِ واقعیِ کاربر هنوز DELETE می‌سازد`() {
    val ui = screen()
    val inHand = ui.data.value
    workerPulls(fromOther.take(1))                    // کالای تازهٔ دستگاهِ دیگر
    assertTrue(runBlocking { ui.save(inHand.copy(products = emptyList())) })
    assertEquals(listOf("products:stale-p1:delete"), queued())
    assertEquals(listOf("stale-p2"), open().data.value.products.map { it.id })
  }

  @Test fun `نوشتنِ بیرون از این فرایند — مُهرِ فایل عوض شد، دفتر از نو خوانده می‌شود`() {
    for (encrypted in listOf(false, true)) {
      after(); before()
      val ui = screen(encrypted)
      val inHand = ui.data.value
      //  نویسندهٔ دیگری مستقیم روی فایل (نه از راهِ این فرایند): کالای تازه
      val other = seed.copy(products = seed.products + Product(id = "stale-p9", name = "نمک"))
      AtRest.writeText(ctx, ledger, Json.encodeToString(ShopData.serializer(), other))
      assertTrue(runBlocking { ui.save(inHand.copy(sales = listOf(sale))) })
      val disk = open().data.value
      assertEquals(listOf("stale-p1", "stale-p9"), disk.products.map { it.id })
      assertEquals(listOf("stale-s1"), disk.sales.map { it.id })
      assertFalse(queued().toString(), queued().any { it.endsWith(":delete") })
      assertEquals(encrypted, AtRestEnvelope.isSealed(ledger))
    }
  }

  @Test fun `کارگرِ نیمه‌راه پس از عوض شدنِ حساب روی دفترِ حسابِ تازه نمی‌نویسد`() {
    val ui = screen()
    val w = open()
    runBlocking { ui.stashTo("stale-a") }
    val thrown = runCatching { w.applyFromServerNow(seed.copy(products = seed.products + Product(id = "stale-px"))) }
    assertTrue(thrown.exceptionOrNull() is java.io.IOException)
    assertFalse("دفترِ حسابِ پیشین روی فایلِ حسابِ تازه ننشست", ledger.exists())
    File(ctx.filesDir, "ledger-stale_a.json").delete()
  }

  /* --------------------- CRM-10: sweep ِ کارگر پس از Pull --------------------- */

  @Test fun `پس از sweep ِ کارگر، ذخیرهٔ صفحه نه sweep را برمی‌گرداند نه DELETE می‌سازد`() {
    ShopPolicy.setCrmReports(ctx, true)
    try {
      val two = ShopData(
        debtors = listOf(Debtor(id = "stale-c1", name = "احمد", createdAt = 10), Debtor(id = "stale-c2", name = "احمد کابلی", createdAt = 20)),
        transactions = listOf(DebtTransaction(id = "stale-t1", debtorId = "stale-c1", type = "give", amount = 300.0, date = "2026-09-01")),
      )
      var seq = 0
      val merged = (CustomerMerge.merge(two, "stale-c2", "stale-c1", "2026-10-19", 1) { "stale-a${seq++}" } as CustomerMerge.Result.Ok).data
      //  دستگاهی آفلاین روی مشتریِ ادغام‌شده قرض داد — sweep جابه‌جایش می‌کند
      val late = DebtTransaction(id = "stale-late", debtorId = "stale-c1", type = "give", amount = 70.0, date = "2026-10-18")
      val ui = open()
      assertTrue(runBlocking { ui.save(merged.copy(transactions = merged.transactions + late)) })
      assertEquals(emptyList<String>(), queued())
      val inHand = ui.data.value

      val w = open()
      assertEquals(true, runBlocking { w.sweepMerged() })    // `SyncV1Worker.sweepAfterPull`
      val swept = queued()
      assertEquals(setOf("transactions:stale-late:update", "auditLog"), swept.map { if (it.startsWith("auditLog:")) "auditLog" else it }.toSet())
      assertEquals("صفحه sweep را می‌بیند", "stale-c2", ui.data.value.transactions.first { it.id == "stale-late" }.debtorId)

      //  صفحه با دفترِ پیش از sweep فروش ثبت می‌کند
      assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale)) })
      assertEquals("فقط فروشِ صفحه — نه برگرداندنِ debtorId، نه DELETE ِ ردِ دفترچه", listOf("sales:stale-s1:insert"), queued().drop(swept.size))
      val disk = open().data.value
      assertEquals("stale-c2", disk.transactions.first { it.id == "stale-late" }.debtorId)
      assertEquals(merged.auditLog.size + 1, disk.auditLog.size)
      assertEquals(listOf("stale-s1"), disk.sales.map { it.id })
      assertTrue("sweep ِ دوم کاری ندارد", runBlocking { w.sweepMerged() } == null)
    } finally {
      ShopPolicy.setCrmReports(ctx, false)
    }
  }

  /* --------------------------------- Sync v3 --------------------------------- */

  @Test fun `Sync v3 — همان ذخیرهٔ کهنه فقط فروشِ صفحه را در صف می‌گذارد`() {
    //  سایهٔ v3 همان دفترِ پس از Pull است (کارگر نشانده)؛ صفحه هنوز دفترِ پیش از آن را دارد
    val after = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(seed), fromOther))
    val dir = File(ctx.filesDir, "sync-v3-${OpQueue.fileKey(account)}")
    FileV3Store(dir).saveMeta(V3Meta(initialized = true, shadow = LedgerDiff.shadowOf(after).toString(), cursor = 7))
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    AtRest.writeText(ctx, ledger, Json.encodeToString(ShopData.serializer(), seed))

    val ui = open()
    val inHand = ui.data.value
    val w = open()
    w.applyFromServerNow(after)                       // همان `ledger.write` ِ `SyncV3Engine.pullOnce`
    assertEquals(listOf("برنج اعلا", "شکر"), ui.data.value.products.map { it.name })
    assertTrue(runBlocking { ui.save(inHand.copy(sales = listOf(sale))) })

    val entries = FileV3Store(dir).loadQueue()?.outbox.orEmpty()
    assertEquals(listOf("sales:stale-s1"), entries.map { "${it.table}:${it.rowId}" })
    assertEquals(listOf("برنج اعلا", "شکر"), open().data.value.products.map { it.name })
  }

  /* ------------- پیام‌ها: پایه‌ای که نگه داشته نشده، و ویرایشِ ردیفِ حذف‌شده ------------- */

  /** پیام‌های `ShopStore.notice` در میانِ [act] */
  private fun noticesOf(store: ShopStore, act: suspend () -> Unit): List<String> = runBlocking {
    val got = java.util.concurrent.CopyOnWriteArrayList<String>()
    val job = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { store.notice.collect { got += it } }
    act()
    delay(200)
    job.cancel()
    got.toList()
  }

  @Test fun `پایه‌ای که دیگر نگه داشته نشده (سه Pull) — ذخیره با پیامِ روشن رد می‌شود، بی هیچ DELETE`() {
    val ui = screen()
    val inHand = ui.data.value
    for (i in 1..3) workerPulls(listOf(op("products", "stale-n$i", SyncOp.INSERT, "name" to "کالا $i", "salePrice" to 10)), cursor = 7L + i)
    var ok = true
    val got = noticesOf(ui) { ok = ui.save(inHand.copy(sales = listOf(sale))) }
    assertFalse("ذخیرهٔ کهنه نوشته نشد", ok)
    assertEquals(listOf(ShopStore.STALE_BASE), got)
    assertEquals("هیچ opی — نه DELETE ِ کالاهای تازه", emptyList<String>(), queued())
    val disk = open().data.value
    assertEquals(4, disk.products.size)
    assertEquals(emptyList<String>(), disk.sales.map { it.id })
    //  همان ذخیره از دفترِ تازهٔ صفحه نوشته می‌شود
    val now = ui.data.value
    assertTrue(runBlocking { ui.save(now.copy(sales = listOf(sale))) })
    assertEquals(listOf("sales:stale-s1:insert"), queued())
  }

  @Test fun `ویرایشِ ردیفی که دستگاهِ دیگر حذف کرد پیام می‌دهد، بی‌صدا گم نمی‌شود`() {
    val ui = screen()
    val inHand = ui.data.value
    workerPulls(listOf(op("products", "stale-p1", SyncOp.DELETE)))
    var ok = false
    val edited = inHand.copy(products = inHand.products.map { it.copy(salePrice = 150.0) }, sales = listOf(sale))
    val got = noticesOf(ui) { ok = ui.save(edited) }
    assertTrue(ok)
    assertEquals(listOf(ShopStore.editsDropped(1)), got)
    assertEquals("حذف می‌برد", emptyList<String>(), open().data.value.products.map { it.id })
    assertEquals(listOf("sales:stale-s1:insert"), queued())
  }

  @Test fun `پاک کردنِ همه (ShopData()) با پرچمِ روشن هم دفتر را خالی می‌کند`() {
    val ui = screen()
    workerPulls(fromOther)
    assertTrue(runBlocking { ui.save(ShopData()) })
    assertEquals(emptyList<String>(), open().data.value.products.map { it.id })
  }
}
