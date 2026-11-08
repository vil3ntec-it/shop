package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.sync.SignOutSafety
import ir.vil3ntec.tohid.data.AccountBind
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SafeSignOut
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SignOutVault
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Keys
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  «خروج زدم، همه‌چیز پاک شد» و «با کدِ شاگرد به دکانِ کارفرما نمی‌رود» — روی دیسکِ واقعیِ کامپیوتر
 *  (همان `ShopStore`، `LedgerOwner`، `SyncV1Engine` و صفِ op ِ گوشی و آیفون). شرحِ ریشه سرِ
 *  `core/sync/AccountSafety.kt`؛ بخشِ سرور در `server/test/signout-restore.test.js`.
 */
class SignOutNoLossDesktopTest {

  private val ctx = DesktopContext
  private val n = System.nanoTime().toString(36)
  private val owner = "usr_own$n"
  private val staff = "usr_stf$n"
  private val shop = "shp_a$n"
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val sync get() = SyncStore(ctx)
  private val engine get() = SyncV1Engine.of(ctx)

  private fun clean() {
    //  بی توکن: هیچ درخواستی به سرورِ واقعی نمی‌رود (سرور ندارد که «همه را گرفتم» بگوید)
    sync.signOut()
    ledger.delete()
    File(ctx.filesDir, "shop-data.json.tmp").delete()
    LedgerOwner.remember(ctx, "", "")
    SyncStateV1.setCurrentAccount(ctx, "")
    sync.accountName = ""
    sync.accountId = ""
    ctx.filesDir.listFiles()?.filter { it.name.contains(n) }?.forEach { it.delete() }
    listOf(owner, staff).forEach { SignOutVault.drop(ctx, it) }
    engine.rebind()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }

  private val shopData = ShopData(
    products = listOf(
      Product(id = "p1-$n", name = "برنج", salePrice = 120.0),
      Product(id = "p2-$n", name = "شکر", salePrice = 90.0),
      Product(id = "p3-$n", name = "روغن", salePrice = 300.0),
    ),
    debtors = listOf(Debtor(id = "d1-$n", name = "احمد")),
  )

  /** صاحب وارد است (هر راهی)، دفترش را دارد و یک تغییرِ نرفته در صف */
  private fun ownerWithData(display: String = "صاحب"): ShopStore {
    val store = open()
    runBlocking { AccountBind.signedIn(ctx, store, owner, shop, display) }
    sync.accountName = display
    assertTrue(runBlocking { store.save(shopData) })
    //  تغییرِ دوم: سایه از پیش هست ⇒ op
    assertTrue(runBlocking { store.save(store.data.value.copy(products = store.data.value.products + Product(id = "p4-$n", name = "چای", salePrice = 50.0))) })
    assertTrue("تغییرِ نرفته در صف", engine.queuedOps().isNotEmpty())
    return store
  }

  private fun deletes(ops: List<SyncOp>) = ops.filter { it.type == SyncOp.DELETE }

  @Test fun `ورود با کدِ شاگرد پس از خروجِ صاحب — کلیدِ همگام‌سازی مالِ شاگرد، هیچ DELETE برای ردیف‌های صاحب`() {
    //  همان حالی که گزارش شد: صاحب پیش‌تر با «کدِ ایمیلی» وارد شده بود (کلیدِ v1 = شناسهٔ صاحب)
    val store = ownerWithData()
    val ownerOps = engine.queuedOps().map { it.opId }
    sync.signOut()

    //  شاگرد با کدِ شاگرد روی همین دستگاه، دکانِ همان صاحب
    val r = runBlocking { AccountBind.signedIn(ctx, store, staff, shop, "علی") }
    assertEquals(LedgerOwner.Result.SWITCHED, r)
    assertEquals(staff, SyncStateV1.currentAccount(ctx))
    assertEquals("صف/سایهٔ شاگرد مالِ خودش", SyncV1Keys.scoped(ctx, staff), engine.currentKey())
    assertEquals("هم‌ترازی نشده ⇒ نخستین دور Snapshot ِ دکانِ کارفرما را می‌گیرد", 0L, SyncStateV1(ctx, engine.currentKey()).reconciledAt)
    assertTrue(store.deskEmpty())
    assertEquals(shop, LedgerOwner.currentShop(ctx))

    //  شاگرد چیزی ثبت می‌کند ⇒ هیچ DELETE ای برای کالاهای صاحب ساخته نمی‌شود
    assertTrue(runBlocking { store.save(store.data.value.copy(products = listOf(Product(id = "ps-$n", name = "نان", salePrice = 10.0)))) })
    assertEquals(emptyList<SyncOp>(), deletes(engine.queuedOps()))
    val ownerQueue = OpQueue(ctx, SyncV1Keys.scoped(ctx, owner)).all()
    assertEquals("صفِ صاحب دست‌نخورده", ownerOps, ownerQueue.map { it.opId })
    assertEquals(emptyList<SyncOp>(), deletes(ownerQueue))

    //  صاحب برمی‌گردد (کدِ ایمیلی یا رمز — هر دو از `AccountBind`) ⇒ همهٔ دفترش سرِ جایش
    sync.signOut()
    assertEquals(LedgerOwner.Result.SWITCHED, runBlocking { AccountBind.signedIn(ctx, store, owner, shop, "صاحب") })
    assertEquals(4, store.data.value.products.size)
    assertEquals(1, store.data.value.debtors.size)
    assertEquals(ownerOps, engine.queuedOps().map { it.opId })
  }

  @Test fun `خروج و ورودِ دوبارهٔ همان حساب پس از بستن و باز کردن — همهٔ داده و صف سرِ جایش`() {
    val store = ownerWithData()
    val before = store.data.value
    val queued = engine.queuedOps().map { it.opId }
    sync.signOut()

    //  برنامه بسته و باز شد؛ ورودِ دکانِ نامعلوم (برگهٔ کدِ ایمیلی بی پاسخِ «دکانِ من») هم دفتر را جابه‌جا نمی‌کند
    val again = open()
    engine.rebind()
    assertEquals(LedgerOwner.Result.SAME, runBlocking { AccountBind.signedIn(ctx, again, owner, null, "صاحب") })
    assertEquals(before, again.data.value)
    assertEquals(queued, engine.queuedOps().map { it.opId })
    assertEquals(emptyList<SyncOp>(), deletes(engine.queuedOps()))
  }

  @Test fun `کلیدِ کهنهٔ نامِ نمایشی یک بار به شناسهٔ همان حساب می‌رود — صفِ نرفته گم نمی‌شود`() {
    //  پیش از این اصلاح: ورودِ رمز کلید را «نامِ نمایشی» می‌گذاشت
    val store = open()
    LedgerOwner.remember(ctx, owner, shop)
    sync.accountName = "حسین$n"
    engine.rebind()
    assertEquals(SyncV1Keys.scoped(ctx, "حسین$n"), engine.currentKey())
    assertTrue(runBlocking { store.save(shopData) })
    assertTrue(runBlocking { store.save(store.data.value.copy(debtors = store.data.value.debtors + Debtor(id = "d2-$n", name = "محمود"))) })
    val legacy = engine.queuedOps().map { it.opId }
    assertTrue(legacy.isNotEmpty())
    sync.signOut()
    engine.rebind()

    assertEquals(LedgerOwner.Result.SAME, runBlocking { AccountBind.signedIn(ctx, store, owner, shop, "حسین$n") })
    assertEquals(SyncV1Keys.scoped(ctx, owner), engine.currentKey())
    assertEquals(legacy, engine.queuedOps().map { it.opId })
    assertTrue("سایه هم آمد", SyncStateV1(ctx, engine.currentKey()).shadow.isNotBlank())
    assertFalse("کلیدِ کهنه خالی شد", SyncV1Keys.hasState(ctx, SyncV1Keys.scoped(ctx, "حسین$n")))
  }

  @Test fun `خروج با تغییرِ نرفته و بی نشست یا اینترنت بسته است و هیچ چیزی پاک نمی‌شود`() {
    val store = ownerWithData()
    val before = store.data.value
    val queued = engine.queuedOps().map { it.opId }
    //  بی توکن (یا بی اینترنت): سرور تأیید نمی‌کند ⇒ نگه داشته
    val (verdict, flush) = runBlocking { SafeSignOut.check(ctx, store) }
    assertTrue(verdict is SignOutSafety.Hold)
    assertEquals(queued.size, (verdict as SignOutSafety.Hold).pending)
    assertTrue(flush.queued > 0)
    assertEquals(before, store.data.value)
    assertEquals(queued, engine.queuedOps().map { it.opId })
    assertEquals(before, open().data.value)
  }

  @Test fun `خروجِ اجباری — تغییرهای نرفته رمزدار برای همان حساب، حسابِ دیگر نمی‌بیند، همان حساب که برگشت دوباره در صف`() {
    val store = ownerWithData()
    val queued = engine.queuedOps().map { it.opId }
    val sealed = SafeSignOut.sealForAccount(ctx)
    if (sealed < 0) {
      //  رمز روی این دستگاه نیست ⇒ هیچ چیزی پاک نشد: صفِ ساده زیرِ کلیدِ همین حساب
      assertEquals(queued, engine.queuedOps().map { it.opId })
      return
    }
    assertEquals(queued.size, sealed)
    assertTrue("صفِ ساده خالی شد", engine.queuedOps().isEmpty())
    val vault = SignOutVault.file(ctx, owner)
    assertTrue(vault.exists())
    val raw = String(vault.readBytes(), Charsets.ISO_8859_1)
    assertFalse("گاوصندوق رمزدار است، نه JSON ِ ساده", queued.any { raw.contains(it) } || raw.contains("p4-$n"))
    sync.signOut()

    //  حسابِ دیگری وارد می‌شود: نه دفتر، نه صفِ صاحب
    runBlocking { AccountBind.signedIn(ctx, store, staff, "shp_b$n", "دیگری") }
    assertTrue(store.deskEmpty())
    assertTrue(engine.queuedOps().none { it.opId in queued })
    assertTrue(SignOutVault.has(ctx, owner))
    sync.signOut()

    //  همان حساب برمی‌گردد ⇒ دفتر و همان opها (همان op_id) جلوی صف، گاوصندوق پاک
    runBlocking { AccountBind.signedIn(ctx, store, owner, shop, "صاحب") }
    assertEquals(4, store.data.value.products.size)
    assertEquals(queued, engine.queuedOps().map { it.opId })
    assertFalse(SignOutVault.has(ctx, owner))
  }

  @Test fun `همان حساب به دکانِ دیگر می‌پیوندد — حالِ همگام‌سازیِ دکانِ پیشین کنارِ دفترش، دکانِ تازه از Snapshot`() {
    val store = ownerWithData()
    val queued = engine.queuedOps().map { it.opId }
    //  همان حساب با کدِ دکانِ دیگر (حسابِ واردشده) ⇒ `LedgerOwner.shopChanged`
    assertEquals(LedgerOwner.Result.SWITCHED, runBlocking { LedgerOwner.shopChanged(ctx, store, "shp_b$n") })
    assertTrue(store.deskEmpty())
    assertTrue("دکانِ تازه هم‌تراز نشده ⇒ Snapshot", engine.queuedOps().isEmpty())
    assertEquals(0L, SyncStateV1(ctx, engine.currentKey()).reconciledAt)
    assertTrue(SyncStateV1(ctx, engine.currentKey()).shadow.isBlank())
    assertTrue(runBlocking { store.save(store.data.value.copy(products = listOf(Product(id = "pb-$n", name = "نمک", salePrice = 5.0)))) })
    assertEquals(emptyList<SyncOp>(), deletes(engine.queuedOps()))
    //  برگشت به دکانِ نخست ⇒ دفتر و صفِ همان دکان سرِ جایش
    assertEquals(LedgerOwner.Result.SWITCHED, runBlocking { LedgerOwner.shopChanged(ctx, store, shop) })
    assertEquals(4, store.data.value.products.size)
    assertEquals(queued, engine.queuedOps().map { it.opId })
  }

  @Test fun `دفترِ کاملاً خالی روی سایهٔ پر هیچ DELETE نمی‌فرستد و هم‌ترازیِ دوباره را می‌خواهد`() {
    val store = ownerWithData()
    val queued = engine.queuedOps().map { it.opId }
    assertTrue(runBlocking { store.save(ShopData()) })
    assertEquals(queued, engine.queuedOps().map { it.opId })
    assertEquals(emptyList<SyncOp>(), deletes(engine.queuedOps()))
    assertEquals(0L, SyncStateV1(ctx, engine.currentKey()).reconciledAt)
  }
}
