package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.branch.BranchBook
import ir.vil3ntec.tohid.core.branch.BranchKey
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.server.DataPartition
import ir.vil3ntec.tohid.data.AccountBind
import ir.vil3ntec.tohid.data.BranchDesk
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  شعبه درونِ یک دکان، فازِ ۲ ِ برنامه (`docs/BRANCHES-fa.md` §۸) روی همان کدِ گوشی: دفترِ هر شعبه جدا روی دیسک
 *  (حساب@دکان#شعبه)، سرآیندِ `X-Branch-Id` فقط برای شعبهٔ دیگر، شعبهٔ اصلی بایت‌به‌بایت همان امروز، و رفتن به حسابِ
 *  دیگر از میزِ شعبه دفترِ اصلی را رونویسی نمی‌کند.
 */
class BranchDeskDesktopTest {

  private val ctx = DesktopContext
  private val n = System.nanoTime().toString(36)
  private val owner = "usr_bo$n"
  private val other = "usr_bx$n"
  private val shop = "shp_b$n"
  private val A = "brn_" + "a1".repeat(12)
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val base get() = DataPartition.keyIn("", owner, shop)
  private fun vaultFile(key: String) = File(ctx.filesDir, "ledger-" + key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(48) + ".json")

  private fun clean() {
    BranchDesk.reset(ctx)
    BranchDesk.forgetCache()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    SyncStore(ctx).signOut()
    ledger.delete()
    LedgerOwner.remember(ctx, "", "")
    SyncStateV1.setCurrentAccount(ctx, "")
    SyncStore(ctx).accountId = ""
    listOf(base, BranchKey.vaultKey(base, A), DataPartition.keyIn("", other, shop)).forEach { vaultFile(it).delete() }
    ctx.filesDir.listFiles()?.filter { it.name.contains(n) }?.forEach { it.deleteRecursively() }
    SyncV1Engine.of(ctx).rebind()
  }

  @Before fun before() = clean()
  @After fun after() = clean()

  private fun open(): ShopStore = ShopStore(ctx).also { runBlocking { it.load() } }
  private fun ids(s: ShopStore) = s.data.value.products.map { it.id }.toSet()
  private val branchA = BranchBook.Branch(A, "الف", archived = false, main = false)
  private val main = BranchBook.Branch(BranchKey.MAIN, "", archived = false, main = true)

  private fun ownerOnMain(): ShopStore {
    val store = open()
    runBlocking { AccountBind.signedIn(ctx, store, owner, shop, "صاحب") }
    assertTrue(runBlocking { store.save(ShopData(products = listOf(Product(id = "m1-$n", name = "برنج", salePrice = 10.0)))) })
    return store
  }

  @Test
  fun `شعبهٔ اصلی همان امروز است — بی سرآیند، همان بایگانی`() {
    assertEquals(BranchKey.MAIN, BranchDesk.current(ctx))
    assertTrue(BranchDesk.headers(ctx).isEmpty())
    assertEquals(base, BranchDesk.vaultOf(ctx, base))
    assertTrue("اصلی بی هیچ درخواست", runBlocking { BranchDesk.confirm(ctx) })
  }

  @Test
  fun `دفترِ هر شعبه جداست و با برگشتن سرِ جایش می‌آید`() {
    val store = ownerOnMain()
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    assertEquals(BranchDesk.Outcome.SWITCHED, runBlocking { BranchDesk.switchTo(ctx, store, branchA) })
    assertEquals(A, BranchDesk.current(ctx))
    assertEquals(mapOf("X-Branch-Id" to A), BranchDesk.headers(ctx))
    assertTrue("دفترِ تازهٔ شعبه خالی است (هیچ فروشِ اصلی در آن نیست)", ids(store).isEmpty())
    assertTrue("دفترِ اصلی زیرِ همان نامِ امروز بایگانی شد", vaultFile(base).isFile)
    assertTrue(runBlocking { store.save(ShopData(products = listOf(Product(id = "a1-$n", name = "چای", salePrice = 5.0)))) })
    assertEquals("" , SyncChoice.set(ctx, SyncChoice.V3, 0))
    assertEquals("تا میز روی شعبه است v1 برنمی‌گردد", "branch", SyncChoice.set(ctx, SyncChoice.V1, 0))

    assertEquals(BranchDesk.Outcome.SWITCHED, runBlocking { BranchDesk.switchTo(ctx, store, main) })
    assertEquals(setOf("m1-$n"), ids(store))
    assertTrue(BranchDesk.headers(ctx).isEmpty())
    assertTrue(vaultFile(BranchKey.vaultKey(base, A)).isFile)
    assertEquals(BranchDesk.Outcome.SAME, runBlocking { BranchDesk.switchTo(ctx, store, main) })
    assertEquals(BranchDesk.Outcome.SWITCHED, runBlocking { BranchDesk.switchTo(ctx, store, branchA) })
    assertEquals(setOf("a1-$n"), ids(store))
  }

  @Test
  fun `حسابِ دیگر از میزِ شعبه ⇒ دفترِ شعبه زیرِ نامِ خودش، اصلی دست‌نخورده، میزِ تازه اصلی`() {
    val store = ownerOnMain()
    SyncChoice.set(ctx, SyncChoice.V3, 0)
    runBlocking { BranchDesk.switchTo(ctx, store, branchA) }
    assertTrue(runBlocking { store.save(ShopData(products = listOf(Product(id = "a2-$n", name = "قند", salePrice = 7.0)))) })
    runBlocking { AccountBind.signedIn(ctx, store, other, shop, "دیگر") }
    assertEquals(BranchKey.MAIN, BranchDesk.current(ctx))
    assertTrue(BranchDesk.headers(ctx).isEmpty())
    assertTrue("حسابِ دیگر دفترِ خالیِ خودش", ids(store).isEmpty())
    //  صاحب برمی‌گردد ⇒ دفترِ اصلیِ خودش، نه دفترِ شعبه
    runBlocking { AccountBind.signedIn(ctx, store, owner, shop, "صاحب") }
    assertEquals(setOf("m1-$n"), ids(store))
    assertEquals(BranchKey.MAIN, BranchDesk.current(ctx))
    assertEquals("«انتخابِ شعبه» دوباره پرسیده می‌شود", "", BranchDesk.pickedFor(ctx))
    assertEquals(BranchDesk.Outcome.SWITCHED, runBlocking { BranchDesk.switchTo(ctx, store, branchA) })
    assertEquals(setOf("a2-$n"), ids(store))
  }

  @Test
  fun `بی دکان، آموزش یا صفِ v1 ِ پر ⇒ هیچ جابه‌جایی`() {
    val bare = open()
    assertEquals(BranchDesk.Outcome.NO_SHOP, runBlocking { BranchDesk.switchTo(ctx, bare, branchA) })
    assertEquals(BranchKey.MAIN, BranchDesk.current(ctx))
    val store = ownerOnMain()
    //  v1 با تغییرِ نرفته (همان ذخیرهٔ بالا) ⇒ اول همگام‌سازی
    if (SyncV1Engine.of(ctx).queueSize() > 0) {
      assertEquals(BranchDesk.Outcome.PENDING, runBlocking { BranchDesk.switchTo(ctx, store, branchA) })
      assertFalse(SyncChoice.isV3(ctx))
      assertEquals(BranchKey.MAIN, BranchDesk.current(ctx))
      assertEquals(setOf("m1-$n"), ids(store))
    }
  }

  @Test
  fun `تأییدِ شعبه پیش از هر دورِ v3 — بی پاسخ نه، پس از پاسخِ روشن یک دقیقه بس`() {
    val store = ownerOnMain()
    SyncChoice.set(ctx, SyncChoice.V3, 0)
    runBlocking { BranchDesk.switchTo(ctx, store, branchA) }
    assertFalse("سرور جواب نداد ⇒ دورِ شعبه نمی‌رود", runBlocking { BranchDesk.confirm(ctx) })
    BranchDesk.confirmed(A, on = true)
    assertTrue(runBlocking { BranchDesk.confirm(ctx) })
    assertFalse(BranchDesk.offSeen)
    BranchDesk.confirmed(A, on = false)
    assertTrue(BranchDesk.offSeen)
  }

  @Test
  fun `سرآیندِ شعبه روی درخواستِ حساب‌دار می‌رود و روی درخواستِ بی‌نامِ خریدار نه`() {
    val server = TestHttpServer()
    try {
      val seen = mutableListOf<String?>()
      server.on("/api/v1/x") { r -> seen += r.headers["x-branch-id"]; 200 to "{}" }
      val engine = HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true, online = { true }, identity = { mapOf("X-Device" to "d1") + BranchKey.headers(A) })
      runBlocking { engine.send("GET", "/x", token = "t") }
      runBlocking { engine.send("GET", "/x", anonymous = true) }
      assertEquals(listOf(A, null), seen)
    } finally {
      server.close()
    }
    val backend = listOf("../android-native/app/src/main/java", "android-native/app/src/main/java").map { File(it, "ir/vil3ntec/tohid/data/repo/Backend.kt") }.first { it.isFile }.readText()
    assertTrue("همهٔ درخواست‌ها از همان `identity`", backend.contains(") + ir.vil3ntec.tohid.data.BranchDesk.headers(app)"))
  }

  @Test
  fun `پیامِ روشنِ ردهای شعبه به‌جای متنِ فنیِ سرور`() {
    val f = ApiFailure.fromHttp(403, """{"error":{"code":"branch_unsupported","message":"این در فقط شعبهٔ اصلی را دارد؛ شعبهٔ دیگر از همگام‌سازیِ v3"}}""", authenticated = true)
    assertEquals("branch_unsupported", f.code)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrBranchDesk.refusal(BranchBook.Refusal.UNSUPPORTED), f.userMessage)
    assertNull(BranchBook.refusalOf("not_found"))
  }
}
