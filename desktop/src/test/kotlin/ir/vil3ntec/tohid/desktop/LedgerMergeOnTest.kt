package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.AtRest
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  `shop.ledger_merge` **پیش‌فرض روشن** (۱۴۰۵/۰۸/۱۸، `docs/DATA-SAFETY-fa.md` «دو دستگاه، یک حساب») — آنچه پیش از روشن
 *  کردن باید درست می‌بود و همین‌جا قفل است:
 *
 *   • پیش‌فرضِ برنامه روشن؛ پاسخِ سرورِ تازه (`v: 2`) هر دو سو را می‌گوید، خاموشِ سرورِ کهنه پیش‌فرضِ کهنهٔ خودش است؛
 *     `false` ای که نسخهٔ پیشین از آن پیش‌فرض نگه داشته بود (کلیدِ `on:`) دیگر خوانده نمی‌شود.
 *   • نقشِ کارمند (A16): سدِ نقش تغییرِ **خودِ صفحه** را می‌سنجد، نه خریدی که کارگر از دستگاهِ دیگر آورد —
 *     پیش‌تر فروشِ صندوق‌دار پس از هر Pull ِ خرید رد می‌شد (سرخ ⇐ سبز). خریدِ خودِ صندوق‌دار همچنان بسته.
 *   • ذخیرهٔ زنجیره‌ای پس از ادغام: صفحه‌ای که از نتیجهٔ ذخیرهٔ خودش ادامه داد پرداختِ پیشین را دوباره جمع می‌کرد
 *     (۱۰۰ + ۵۰ ⇒ ۲۵۰؛ سرخ ⇐ سبز). دو کلیکِ هم‌زمان از همان پایه هر دو فروش را نگه می‌دارد، بی DELETE.
 *   • بی‌اینترنت: فروش‌های صفِ نرفته با Pull ِ دیرِ دستگاه‌های دیگر و ذخیرهٔ کهنه هیچ‌کدام گم نمی‌شوند.
 *   • Sync v3: همان سدِ نقش و همان صف.
 *   • به‌روزرسانی: دفترِ نسخهٔ پایدارِ ۳.۲.۳۳۷ با پرچمِ روشن و ذخیرهٔ کهنه هیچ ردیفی نمی‌اندازد.
 */
class LedgerMergeOnTest {

  private val ctx = DesktopContext
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val engine get() = SyncV1Engine.of(ctx)
  private val flagPrefs get() = ctx.getSharedPreferences("tohid-ledger-merge", 0)
  private lateinit var account: String

  private fun clean() {
    AtRest.setWanted(ctx, false)
    ctx.filesDir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("sync-v3-lmon") }
      .forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    flagPrefs.edit().clear().commit()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    account = "lmon-${System.nanoTime()}"
    SyncStateV1.setCurrentAccount(ctx, account)
    engine.rebind()
  }

  @After fun after() {
    ShopRole.remember(ctx, ShopRole.OWNER)
    flagPrefs.edit().clear().commit()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    SyncStateV1.setCurrentAccount(ctx, "")
    engine.rebind()
    clean()
  }

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }
  private fun queued(): List<String> = OpQueue(ctx, account).all().map { "${it.table}:${it.rowId}:${it.type}" }

  private fun op(table: String, id: String, type: String, vararg f: Pair<String, Any>) = SyncOp(
    Ulid.next(), System.currentTimeMillis(), table, id, type,
    buildJsonObject { for ((k, v) in f) put(k, if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) },
  )

  private fun workerPulls(ops: List<SyncOp>, cursor: Long = 7) {
    val w = open()
    engine.absorbRemote(ops, { w.ledgerNow() }, { w.applyFromServerNow(it) }, cursor)
  }

  private val p1 = Product(id = "lmon-p1", name = "برنج", salePrice = 120.0)
  private val seed = ShopData(products = listOf(p1))
  private fun sale(id: String) = Sale(id = id, finalTotal = 120.0, createdAt = 1_800_000_000_000L, date = "2026-11-08")

  /** صفحه با دفتری که در دست دارد: نخستین ذخیره سایه را می‌گذارد (بی op) */
  private fun screen(start: ShopData = seed): ShopStore {
    val ui = open()
    assertTrue(runBlocking { ui.save(start) })
    assertEquals(emptyList<String>(), queued())
    return ui
  }

  /** خریدِ دستگاهِ دیگر (صاحب یا انباردار) */
  private val otherPurchase = listOf(op("purchases", "lmon-pu1", SyncOp.INSERT, "productId" to "lmon-p1", "quantity" to 5, "unitPrice" to 90))

  /* ---------------------------- پیش‌فرض و سازگاری ---------------------------- */

  @Test fun `پیش‌فرضِ برنامه روشن است — بی هیچ پاسخی از سرور، ذخیرهٔ کهنه چیزی را حذف نمی‌کند`() {
    assertTrue(LedgerMergeFlag.enabled(ctx))
    val ui = screen()
    val inHand = ui.data.value
    workerPulls(listOf(op("products", "lmon-p2", SyncOp.INSERT, "name" to "شکر", "salePrice" to 90)))
    assertTrue(runBlocking { ui.save(inHand.copy(sales = listOf(sale("lmon-s1")))) })
    assertEquals(listOf("sales:lmon-s1:insert"), queued())
    assertEquals(listOf("lmon-p1", "lmon-p2"), open().data.value.products.map { it.id })
  }

  @Test fun `پاسخِ سرور — تازه (v2) هر دو سو، کهنه فقط روشنِ صریح؛ خطا هیچ`() {
    assertEquals(true, LedgerMergeFlag.decide(true, 2))
    assertEquals("خاموشِ صریحِ سرورِ تازه نوشته می‌شود", false, LedgerMergeFlag.decide(false, 2))
    assertEquals(true, LedgerMergeFlag.decide(true, null))
    assertNull("خاموشِ سرورِ کهنه همان پیش‌فرضِ کهنهٔ اوست، نه تصمیمِ صاحب‌کار", LedgerMergeFlag.decide(false, null))
    assertNull(LedgerMergeFlag.decide(null, 2))
    //  خاموشِ صریح هنوز کار می‌کند: همان رفتارِ پیشین
    LedgerMergeFlag.set(ctx, false)
    assertFalse(LedgerMergeFlag.enabled(ctx))
    LedgerMergeFlag.set(ctx, true)
    assertTrue(LedgerMergeFlag.enabled(ctx))
  }

  @Test fun `به‌روزرسانی از نسخهٔ پیشین — «خاموش» ِ نگه‌داشته از پیش‌فرضِ کهنه دیگر خوانده نمی‌شود`() {
    //  نسخهٔ پیشین پاسخِ همیشگیِ «خاموش» ِ سرور را زیرِ `on:<دکان>` نگه می‌داشت
    flagPrefs.edit().putBoolean("on:-", false).commit()
    assertTrue(LedgerMergeFlag.enabled(ctx))
  }

  @Test fun `به‌روزرسانی — دفترِ نسخهٔ پایدارِ ۳٫۲٫۳۳۷ با پرچمِ روشن و ذخیرهٔ کهنه هیچ ردیفی نمی‌اندازد`() {
    val fixture = File("../android-native/app/src/test/resources/upgrade/stable-3.2.337/shop-data.json")
    assertTrue(fixture.isFile)
    val old = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }.decodeFromString(ShopData.serializer(), fixture.readText())
    fixture.copyTo(ledger, overwrite = true)
    val ui = open()
    assertEquals(old, ui.data.value)
    assertTrue(runBlocking { ui.save(ui.data.value) })
    val inHand = ui.data.value
    workerPulls(listOf(op("products", "lmon-up", SyncOp.INSERT, "name" to "کالای دستگاهِ دیگر", "salePrice" to 10)))
    assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale("lmon-up-s"))) })
    assertFalse(queued().toString(), queued().any { it.endsWith(":delete") })
    val disk = open().data.value
    assertEquals(old.products.map { it.id } + "lmon-up", disk.products.map { it.id })
    assertEquals(old.sales.map { it.id } + "lmon-up-s", disk.sales.map { it.id })
    assertEquals(old.debtors, disk.debtors)
    assertEquals(old.transactions, disk.transactions)
  }

  /* --------------------------------- نقشِ کارمند --------------------------------- */

  @Test fun `صندوق‌دار — فروشِ صفحهٔ کهنه پس از Pull ِ خریدِ دستگاهِ دیگر ثبت می‌شود، نه «اجازهٔ خرید ندارید»`() {
    val ui = screen()
    ShopRole.remember(ctx, ShopRole.CASHIER)
    val inHand = ui.data.value
    workerPulls(otherPurchase)
    var ok = false
    val refused = roleBlocks(ui) { ok = ui.save(inHand.copy(sales = listOf(sale("lmon-c1")))) }
    assertEquals("سدِ نقش فقط تغییرِ خودِ صفحه را می‌سنجد", emptyList<String>(), refused)
    assertTrue(ok)
    assertEquals(listOf("sales:lmon-c1:insert"), queued())
    val disk = open().data.value
    assertEquals(listOf("lmon-pu1"), disk.purchases.map { it.id })
    assertEquals(listOf("lmon-c1"), disk.sales.map { it.id })
  }

  @Test fun `صندوق‌دار — خریدِ خودش همچنان پیش از دیسک بسته است (A16)`() {
    val ui = screen()
    ShopRole.remember(ctx, ShopRole.CASHIER)
    val now = ui.data.value
    var ok = true
    val refused = roleBlocks(ui) { ok = ui.save(now.copy(purchases = listOf(Purchase(id = "lmon-pu9", productId = "lmon-p1")))) }
    assertFalse(ok)
    assertEquals(1, refused.size)
    assertEquals(emptyList<String>(), queued())
    assertEquals(emptyList<String>(), open().data.value.purchases.map { it.id })
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

  /* ---------------------------- هم‌زمانی و زنجیره ---------------------------- */

  private val debtor = Debtor(id = "lmon-d1", name = "کریم", createdAt = 10)
  private val credit = Sale(
    id = "lmon-x", finalTotal = 500.0, paymentMethod = "debt", debtorId = "lmon-d1", paidAmount = 0.0, remaining = 500.0,
    debtGiven = 500.0, createdAt = 1_800_000_000_000L, date = "2026-11-08",
  )
  private fun pay(id: String, amount: Double) =
    DebtTransaction(id = id, debtorId = "lmon-d1", type = "receive", amount = amount, date = "2026-11-08")

  @Test fun `ذخیرهٔ زنجیره‌ای پس از ادغام — پرداختِ پیشین دوباره جمع نمی‌شود (۱۰۰ + ۵۰ = ۱۵۰)`() {
    val ui = screen(seed.copy(debtors = listOf(debtor), sales = listOf(credit)))
    val inHand = ui.data.value
    workerPulls(listOf(op("products", "lmon-p2", SyncOp.INSERT, "name" to "شکر", "salePrice" to 90)))
    //  پرداختِ ۱۰۰ از دفترِ کهنهٔ صفحه ⇒ ادغام
    val x0 = inHand.sales.single()
    val first = inHand.copy(
      sales = listOf(x0.copy(paidAmount = 100.0, remaining = 400.0, debtSettled = 100.0)),
      transactions = inHand.transactions + pay("lmon-t1", 100.0),
    )
    assertTrue(runBlocking { ui.save(first) })
    //  صفحه از نتیجهٔ ذخیرهٔ خودش ادامه می‌دهد (نه از `data`): پرداختِ ۵۰ روی همان فاکتور
    val x1 = first.sales.single()
    val second = first.copy(
      sales = listOf(x1.copy(paidAmount = 150.0, remaining = 350.0, debtSettled = 150.0)),
      transactions = first.transactions + pay("lmon-t2", 50.0),
    )
    assertTrue(runBlocking { ui.save(second) })
    val disk = open().data.value
    val x = disk.sales.single()
    assertEquals(150.0, x.paidAmount, 0.0)
    assertEquals(350.0, x.remaining, 0.0)
    assertEquals(150.0, x.debtSettled, 0.0)
    assertEquals(listOf("lmon-t1", "lmon-t2"), disk.transactions.map { it.id })
    assertEquals("کالای دستگاهِ دیگر می‌ماند", listOf("lmon-p1", "lmon-p2"), disk.products.map { it.id })
    assertFalse(queued().toString(), queued().any { it.endsWith(":delete") })
  }

  @Test fun `دو کلیکِ هم‌زمان از همان دفترِ کهنه پس از Pull — هر دو فروش می‌مانند، بی DELETE`() {
    val ui = screen()
    val inHand = ui.data.value
    workerPulls(listOf(op("products", "lmon-p2", SyncOp.INSERT, "name" to "شکر", "salePrice" to 90)))
    assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale("lmon-a"))) })
    assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale("lmon-b"))) })
    assertEquals(listOf("sales:lmon-a:insert", "sales:lmon-b:insert"), queued())
    assertEquals(setOf("lmon-a", "lmon-b"), open().data.value.sales.map { it.id }.toSet())
  }

  @Test fun `بی‌اینترنت — فروش‌های صفِ نرفته با Pull ِ دیر و ذخیرهٔ کهنه هیچ‌کدام گم نمی‌شوند`() {
    val ui = screen()
    //  دو فروشِ بی‌اینترنت: در صف می‌مانند (هیچ push ای نیست)
    assertTrue(runBlocking { ui.save(ui.data.value.let { it.copy(sales = it.sales + sale("lmon-o1")) }) })
    assertTrue(runBlocking { ui.save(ui.data.value.let { it.copy(sales = it.sales + sale("lmon-o2")) }) })
    val inHand = ui.data.value
    //  اینترنت برگشت: دستگاه‌های دیگر کالا و خرید آورده بودند؛ صفحه هنوز دفترِ پیش از Pull را دارد
    workerPulls(otherPurchase + op("products", "lmon-p3", SyncOp.INSERT, "name" to "روغن", "salePrice" to 300))
    assertTrue(runBlocking { ui.save(inHand.copy(sales = inHand.sales + sale("lmon-o3"))) })
    assertEquals(listOf("sales:lmon-o1:insert", "sales:lmon-o2:insert", "sales:lmon-o3:insert"), queued())
    val disk = open().data.value
    assertEquals(setOf("lmon-o1", "lmon-o2", "lmon-o3"), disk.sales.map { it.id }.toSet())
    assertEquals(listOf("lmon-p1", "lmon-p3"), disk.products.map { it.id })
    assertEquals(listOf("lmon-pu1"), disk.purchases.map { it.id })
  }

  /* --------------------------------- Sync v3 --------------------------------- */

  @Test fun `Sync v3 — فروشِ صندوق‌دار پس از Pull ِ خرید فقط همان فروش را در صف می‌گذارد`() {
    val after = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(seed), otherPurchase))
    val dir = File(ctx.filesDir, "sync-v3-${OpQueue.fileKey(account)}")
    FileV3Store(dir).saveMeta(V3Meta(initialized = true, shadow = LedgerDiff.shadowOf(after).toString(), cursor = 7))
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    AtRest.writeText(ctx, ledger, Json.encodeToString(ShopData.serializer(), seed))
    ShopRole.remember(ctx, ShopRole.CASHIER)

    val ui = open()
    val inHand = ui.data.value
    open().applyFromServerNow(after)                  // همان `ledger.write` ِ `SyncV3Engine.pullOnce`
    var ok = false
    val refused = roleBlocks(ui) { ok = ui.save(inHand.copy(sales = listOf(sale("lmon-v3")))) }
    assertEquals(emptyList<String>(), refused)
    assertTrue(ok)
    val entries = FileV3Store(dir).loadQueue()?.outbox.orEmpty()
    assertEquals(listOf("sales:lmon-v3"), entries.map { "${it.table}:${it.rowId}" })
    val disk = open().data.value
    assertEquals(listOf("lmon-pu1"), disk.purchases.map { it.id })
    assertEquals(listOf("lmon-v3"), disk.sales.map { it.id })
    dir.deleteRecursively()
  }
}
