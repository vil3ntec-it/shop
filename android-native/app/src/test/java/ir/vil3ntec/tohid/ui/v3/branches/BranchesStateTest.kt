package ir.vil3ntec.tohid.ui.v3.branches

import ir.vil3ntec.tohid.core.branch.BranchBook
import ir.vil3ntec.tohid.core.branch.BranchKey
import ir.vil3ntec.tohid.core.branch.BranchTally
import ir.vil3ntec.tohid.core.i18n.StrBranchDesk
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ShopBranchesSource
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  شعبه درونِ یک دکان، فازِ ۲ ِ برنامه (`docs/BRANCHES-fa.md` §۸): «انتخابِ شعبه» پس از ورود، صفحهٔ «شعبه‌ها» ِ صاحب و
 *  گزارشِ هر شعبه و جمع — بی سرور. پرچمِ خاموش (پیش‌فرض) ⇒ هیچ دفترچه، پرسش، ردیف یا عددی.
 */
class BranchesStateTest {

  private val A = "brn_" + "a".repeat(24)
  private val B = "brn_" + "b".repeat(24)

  private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

  private fun book(owner: Boolean, mine: String, branches: String): ApiResult<BranchBook.Answer> =
    ApiResult.Success(BranchBook.of(json("""{"enabled":true,"all":$owner,"mine":$mine,"branches":$branches}""")))

  private val three = """[{"id":"","name":"","status":"active"},{"id":"$A","name":"الف","status":"active"},{"id":"$B","name":"ب","status":"archived"}]"""

  private class Fake(var next: ApiResult<BranchBook.Answer>) : ShopBranchesSource {
    val writes = mutableListOf<String>()
    var members: ApiResult<BranchBook.Members> = ApiResult.Success(BranchBook.Members(emptyList(), emptyList()))
    var write: ApiResult<Unit> = ApiResult.Success(Unit)
    var report: ApiResult<BranchTally.View?> = ApiResult.Success(null)
    val reports = mutableListOf<Int>()
    override suspend fun book() = next
    override suspend fun create(name: String) = write.also { writes += "create:$name" }
    override suspend fun update(id: String, name: String?, archived: Boolean?) = write.also { writes += "update:$id:$name:$archived" }
    override suspend fun members() = members
    override suspend fun setMember(memberId: String, branches: List<String>) =
      members.also { writes += "member:$memberId:" + branches.joinToString(",") }
    override suspend fun report(days: Int, todayIso: String) = report.also { reports += days }
  }

  @After fun after() = BranchLive.reset()

  @Test
  fun `پرچمِ خاموش، ۴۰۴ یا ۴۰۳ ⇒ هیچ چیزِ تازه؛ بی‌اینترنت همان پیشین`() = runBlocking {
    val f = Fake(ApiResult.Success(BranchBook.Answer.Off))
    assertNull(BranchLive.probe(f, BranchKey.MAIN, afterSignIn = true))
    assertNull(BranchLive.book)
    assertNull(BranchLive.pick)
    assertFalse(BranchLive.visible)
    assertTrue("خاموش دیده شد ⇒ میزِ شعبه به اصلی برمی‌گردد", BranchLive.off)
    f.next = book(true, "[\"\",\"$A\"]", three)
    BranchLive.probe(f, BranchKey.MAIN, afterSignIn = false)
    assertTrue(BranchLive.visible)
    assertFalse(BranchLive.off)
    f.next = ApiResult.Failure(ApiFailure.Offline())
    assertNull(BranchLive.probe(f, BranchKey.MAIN, afterSignIn = true))
    assertNotNull("بی‌اینترنت پنهان نمی‌کند", BranchLive.book)
    f.next = ApiResult.Failure(ApiFailure.NotFound("no", "not_found"))
    BranchLive.probe(f, BranchKey.MAIN, afterSignIn = false)
    assertNull(BranchLive.book)
    assertTrue(BranchLive.off)
  }

  @Test
  fun `پس از ورود با چند شعبه می‌پرسد، فقط شعبه‌های باز؛ یک شعبه بی پرسش؛ هیچ ⇒ پیامِ روشن`() = runBlocking {
    val f = Fake(book(true, "[\"\",\"$A\"]", three))
    val p = BranchLive.probe(f, BranchKey.MAIN, afterSignIn = true)
    assertTrue(p is BranchBook.Pick.Ask)
    assertEquals("بایگانی هرگز انتخاب نیست", listOf(BranchKey.MAIN, A), (p as BranchBook.Pick.Ask).choices.map { it.id })
    assertEquals(p, BranchLive.pick)
    assertTrue("شعبهٔ جاری باز است ⇒ می‌شود بست", BranchLive.dismissible(BranchKey.MAIN))
    assertFalse("شعبهٔ بایگانی‌شده ⇒ باید برگزید", BranchLive.dismissible(B))
    assertEquals(BranchBook.Pick.Stay, BranchLive.probe(f, BranchKey.MAIN, afterSignIn = false))
    assertNull("ماندن پرسشی باز نمی‌گذارد", BranchLive.pick)
    //  کارمندِ فقط «الف»
    f.next = book(false, "[\"$A\"]", """[{"id":"$A","name":"الف","status":"active"}]""")
    val use = BranchLive.probe(f, BranchKey.MAIN, afterSignIn = true)
    assertEquals(A, (use as BranchBook.Pick.Use).branch.id)
    assertNull(BranchLive.pick)
    assertFalse("یک شعبه و نه صاحب ⇒ ردیفی نیست", BranchLive.visible)
    //  کارمندِ بی هیچ شعبهٔ باز
    f.next = book(false, "[]", "[]")
    assertEquals(BranchBook.Pick.None, BranchLive.probe(f, A, afterSignIn = false))
    assertEquals(BranchBook.Pick.None, BranchLive.pick)
    assertEquals(StrBranchDesk.mainName, ir.vil3ntec.tohid.core.i18n.branchName("", main = true))
  }

  @Test
  fun `پیامِ هر پایانِ تعویض روشن است و همان دو حالتِ موفق پیامِ خطا ندارند`() {
    ir.vil3ntec.tohid.data.BranchDesk.Outcome.values().forEach { o ->
      val t = BranchLive.outcomeText(o, "الف")
      if (o == ir.vil3ntec.tohid.data.BranchDesk.Outcome.SAME) assertNull(t) else assertTrue(o.name, !t.isNullOrBlank())
    }
    BranchBook.Refusal.values().forEach { assertTrue(it.name, ir.vil3ntec.tohid.core.i18n.branchRefusalText(it).isNotBlank()) }
  }

  @Test
  fun `صفحهٔ صاحب — اعضا فقط برای صاحب، نوشتن با پیامِ روشن، اصلی بایگانی نمی‌شود`() = runBlocking {
    val f = Fake(book(true, "[\"\",\"$A\"]", three))
    f.members = ApiResult.Success(BranchBook.membersOf(json(
      """{"branches":$three,"members":[{"id":"m1","userId":"u1","name":"کریم","role":"staff","branches":[""]}]}""",
    )))
    val s = BranchesPageState(f)
    s.load()
    assertEquals(listOf("m1"), s.members.map { it.id })
    assertFalse(s.off || s.failed || s.busy)
    assertFalse("نامِ تهی ⇒ هیچ", s.create("  "))
    assertTrue(s.create("مرکز"))
    assertEquals(StrBranchDesk.saved, s.message)
    assertFalse("اصلی بایگانی نمی‌شود", s.setArchived(BranchKey.MAIN, true))
    assertTrue(s.setArchived(A, true))
    assertTrue(s.rename(BranchKey.MAIN, "اصلیِ تازه"))
    val m = s.members.first()
    assertEquals(listOf(BranchKey.MAIN, A), s.toggled(m, A))
    assertEquals("برداشتنِ تنها شعبه ⇒ همان اصلی", listOf(BranchKey.MAIN), s.toggled(m, BranchKey.MAIN))
    assertTrue(s.setMember(m.id, s.toggled(m, A)))
    assertEquals(listOf("create:مرکز", "update:$A:null:true", "update::اصلیِ تازه:null", "member:m1:,$A"), f.writes)
    f.write = ApiResult.Failure(ApiFailure.Forbidden("شعبه‌ها را فقط صاحبِ دکان می‌سازد و می‌دهد", "owner_only"))
    assertFalse(s.create("دیگر"))
    assertTrue(s.message!!.startsWith(StrBranchDesk.notSaved("").trimEnd()))
    //  کارمند ⇒ اعضا خوانده نمی‌شوند
    f.next = book(false, "[\"$A\"]", """[{"id":"$A","name":"الف","status":"active"}]""")
    s.load()
    assertTrue(s.members.isEmpty())
    //  پرچمِ خاموش ⇒ «روشن نیست»
    f.next = ApiResult.Failure(ApiFailure.NotFound("no", "not_found"))
    s.load()
    assertTrue(s.off)
    assertNull(s.book)
  }

  @Test
  fun `گزارش — هر شعبه از سرور و جمع فقط وقتی سرور داد (صاحب)`() = runBlocking {
    val f = Fake(ApiResult.Success(BranchBook.Answer.Off))
    val s = BranchReportState(f)
    s.load("2026-11-14")
    assertTrue("بی enabled ⇒ روشن نیست", s.off)
    val owner = BranchTally.of(json(
      """{"enabled":true,"timezone":"Asia/Kabul","branches":[{"id":"","name":"","summary":{"invoices":2,"netSales":7000}},
        {"id":"$A","name":"الف","summary":{"invoices":1,"netSales":3000}}],"totals":{"invoices":3,"netSales":10000}}""",
    ))
    f.report = ApiResult.Success(owner)
    s.days = 7
    s.load("2026-11-14")
    assertFalse(s.off)
    assertEquals(10000L, s.view!!.totals!!["netSales"])
    assertEquals(listOf(BranchKey.MAIN, A), s.view!!.rows.map { it.id })
    val staff = BranchTally.of(json("""{"enabled":true,"branches":[{"id":"$A","name":"الف","summary":{"invoices":1}}]}"""))
    f.report = ApiResult.Success(staff)
    s.load("2026-11-14")
    assertNull("کارمند جمع نمی‌گیرد", s.view!!.totals)
    f.report = ApiResult.Failure(ApiFailure.Offline())
    s.load("2026-11-14")
    assertTrue(s.failed)
    assertNotNull("آخرین گزارش می‌ماند", s.view)
    assertEquals("پیش‌فرض امروز، سپس ۷ روز", listOf(1, 7, 7, 7), f.reports)
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `صفحه‌ها ثبت‌اند و راهشان فقط پشتِ پاسخِ روشن؛ میزبانِ پرسش در ریشه و ردیف در «حسابِ من»`() {
    assertNotNull(V3Modules.table.resolve(BranchPaths.ROOT))
    assertEquals(BranchPaths.ROOT, V3Modules.table.resolve(BranchPaths.REPORT)!!.route.parent)
    val entry = BranchesModule.settings.single()
    assertEquals(BranchPaths.ROOT, entry.route)
    assertTrue(src("ui/AppRoot.kt").contains("ir.vil3ntec.tohid.ui.v3.branches.BranchHost(store)"))
    assertTrue(src("ui/v3/account/profile/ProfileScreen.kt").contains("ir.vil3ntec.tohid.ui.v3.branches.BranchProfileCard(onOpen)"))
    val pages = src("ui/v3/branches/BranchesPagesAndroid.kt")
    assertTrue("ردیف فقط با پاسخِ روشن", pages.contains("if (!BranchLive.visible) return"))
    assertTrue(src("ui/v3/branches/BranchesModule.kt").contains("shown = { BranchLive.visible }"))
    //  فازِ ۳: ساختن/ویرایشِ کالا و مدیریتِ دسته پشتِ قفلِ کاتالوگِ شعبه
    val products = src("ui/v3/products/ProductsRoutes.kt")
    listOf("BranchCatalogGate {\n        NewProductsScreen(", "BranchCatalogGate { EditProductScreen(", "if (use is CategoryUse.Manage) BranchCatalogGate {", "BranchCatalogGate { CategorySheet(store, data, toasts, CategoryUse.Bulk(ids)")
      .forEach { assertTrue(it, products.contains(it)) }
    assertTrue(src("data/ShopStore.kt").contains("?: return BranchDesk.catalogRefused(context, base, next) { _notice.tryEmit(it) }"))
  }
}
