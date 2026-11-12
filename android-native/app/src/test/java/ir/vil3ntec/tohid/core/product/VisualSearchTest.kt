package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrVisualSearch
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.repo.VisualSearchSource
import ir.vil3ntec.tohid.ui.screens.VisualSearchController
import ir.vil3ntec.tohid.ui.screens.VisualSearchLive
import ir.vil3ntec.tohid.ui.screens.visualLocal
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  AI-11 — «جست‌وجو با عکس» ِ برنامه: خواندنِ پاسخِ `/api/shop/visual-search`، برچسبِ «مشابه»، پنهان ماندن تا سرور
 *  «روشن» نگفته (پرچمِ خاموش ⇒ ۴۰۴)، و این‌که هیچ پیشنهادی خودکار انتخاب یا به سبد افزوده نمی‌شود.
 */
class VisualSearchTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val answerJson = """{"label":"مشابه","notice":"…","threshold":{"hamming_max":10,"hist_max":100},"indexed":6,"items":[
    {"product_id":"p-base","name":"کالای سرور","distance":0,"similarity":100,"label":"مشابه"},
    {"product_id":"p-off","name":"خاموش","distance":1,"similarity":98,"label":"مشابه"},
    {"product_id":"p-base","name":"تکراری","distance":2,"similarity":97,"label":"مشابه"},
    {"product_id":"bad id!","name":"شناسهٔ بد","distance":3,"similarity":95,"label":"مشابه"},
    {"name":"بی شناسه","distance":3,"similarity":95},
    "نه شیء",
    {"product_id":"p-gone","name":"در دفتر نیست","distance":4,"similarity":140,"label":"مشابه"}
  ]}"""

  private val products = listOf(
    Product(id = "p-base", name = "برنج", salePrice = 300.0, unit = "کیلو", photo = true),
    Product(id = "p-off", name = "روغنِ کهنه", salePrice = 500.0, photo = false, active = false),
  )
  private val byId = products.associateBy { it.id }
  private val local: (String) -> VisualSearch.Local? = { visualLocal(byId[it]) }

  private class Fake(var status: ApiResult<Boolean>, var answer: ApiResult<VisualSearch.Answer>) : VisualSearchSource {
    var searches = 0
    override suspend fun status(): ApiResult<Boolean> = status
    override suspend fun search(jpeg: ByteArray): ApiResult<VisualSearch.Answer> { searches++; return answer }
  }

  private val notFound = ApiResult.Failure(ApiFailure.NotFound("یافت نشد", "not_found"))

  @Before fun fa() { I18n.source = { Lang.FA } }

  @After fun reset() {
    I18n.source = { Lang.FA }
    runBlocking { VisualSearchLive.load(Fake(notFound, notFound)) }
  }

  @Test
  fun `پاسخِ سرور خوانده می‌شود و ردیفِ بد یا تکراری کنار می‌رود`() {
    val a = VisualSearch.parse(json(answerJson))
    assertEquals(6, a.indexed)
    assertEquals(listOf("p-base", "p-off", "p-gone"), a.hits.map { it.productId })
    assertEquals("کالای سرور", a.hits[0].name)
    assertEquals(listOf(100, 98, 100), a.hits.map { it.similarity })
    assertEquals(VisualSearch.Answer(0, emptyList()), VisualSearch.parse(json("""{"items":"x","indexed":-3}""")))
    assertEquals("/shop/visual-search?limit=8", VisualSearch.searchPath())
    assertEquals("/shop/visual-search?limit=20", VisualSearch.searchPath(99))
  }

  @Test
  fun `هر پیشنهاد مشابه است با نام و قیمت و عکسِ دفترِ همین دستگاه`() {
    val a = VisualSearch.parse(json(answerJson))
    val list = VisualSearch.suggestions(a, VisualSearch.Place.LIST, local)
    assertEquals(listOf("p-base", "p-off"), list.map { it.productId })
    assertEquals(VisualSearch.Suggestion("p-base", "برنج", 300.0, "کیلو", true, 100), list[0])
    assertTrue(list.all { it.label == "مشابه" && it.label == VisualSearch.LABEL })
    //  صندوق: کالای غیرفعال در فروش نمی‌آید
    assertEquals(listOf("p-base"), VisualSearch.suggestions(a, VisualSearch.Place.TILL, local).map { it.productId })
    assertEquals("مشابه", StrVisualSearch.similar)
    assertEquals("جست‌وجو با عکس", StrVisualSearch.button)
    val all = listOf(StrVisualSearch.title, StrVisualSearch.similar, StrVisualSearch.notice, StrVisualSearch.none, StrVisualSearch.photoNote)
    assertTrue(all.none { it.contains("همان") || it.contains("یکسان") })
    I18n.source = { Lang.EN }
    assertEquals("Similar", StrVisualSearch.similar)
    I18n.source = { Lang.PS }
    assertEquals("ورته", StrVisualSearch.similar)
  }

  @Test
  fun `پرچمِ خاموش یعنی دکمهٔ پنهان و خطای گذرا همان حالِ پیشین`() = runBlocking {
    assertFalse(VisualSearch.enabled(json("{}")))
    assertFalse(VisualSearch.enabled(json("""{"enabled":"true"}""")))
    assertFalse(VisualSearch.enabled(json("""{"enabled":false}""")))
    assertTrue(VisualSearch.enabled(json("""{"enabled":true,"label":"مشابه","indexed":3}""")))
    assertFalse(VisualSearch.visibleAfter(false, null, refused = false))
    assertFalse(VisualSearch.visibleAfter(true, null, refused = true))
    assertTrue(VisualSearch.visibleAfter(true, null, refused = false))

    val src = Fake(notFound, notFound)
    VisualSearchLive.load(src)
    assertFalse("۴۰۴ ِ پرچمِ خاموش ⇒ پنهان", VisualSearchLive.visible)
    src.status = ApiResult.Success(true)
    VisualSearchLive.load(src)
    assertTrue(VisualSearchLive.visible)
    src.status = ApiResult.Failure(ApiFailure.Offline())
    VisualSearchLive.load(src)
    assertTrue("شبکه ⇒ همان حال", VisualSearchLive.visible)
    src.status = ApiResult.Failure(ApiFailure.Forbidden("اجازه ندارید", "forbidden"))
    VisualSearchLive.load(src)
    assertFalse("بی اجازه ⇒ پنهان", VisualSearchLive.visible)
    src.status = ApiResult.Success(false)
    VisualSearchLive.load(src)
    assertFalse(VisualSearchLive.visible)
    assertEquals(0, src.searches)
  }

  @Test
  fun `زدنِ پیشنهاد هرگز خودکار به سبد نمی‌افزاید`() = runBlocking {
    val src = Fake(ApiResult.Success(true), ApiResult.Success(VisualSearch.parse(json(answerJson))))
    VisualSearchLive.load(src)
    val till = VisualSearchController(src, VisualSearch.Place.TILL, local)
    till.send(byteArrayOf(1, 2, 3))
    assertEquals(VisualSearch.Phase.DONE, till.sheet.phase)
    assertEquals(listOf("p-base"), till.sheet.rows.map { it.productId })
    //  صندوق: فقط کالا در کادرِ جست‌وجو؛ برگه بسته می‌شود
    assertEquals(VisualSearch.Tap.Search("برنج"), till.tap(till.sheet.rows[0]))
    assertFalse(till.sheet.open)
    val list = VisualSearchController(src, VisualSearch.Place.LIST, local)
    list.send(byteArrayOf(1))
    assertEquals(VisualSearch.Tap.Open("p-off"), list.tap(list.sheet.rows[1]))

    //  عکس خوانده نشد ⇒ پیام، بی درخواست؛ عکسِ نسنجیدنی (۴۲۲) ⇒ پیامِ همین زبان
    val n = src.searches
    till.send(null)
    assertEquals(StrVisualSearch.unreadable, till.sheet.error)
    assertEquals(n, src.searches)
    src.answer = ApiResult.Failure(ApiFailure.Invalid("سنجیدنی نیست", "image_unmeasurable"))
    till.send(byteArrayOf(1))
    assertEquals(VisualSearch.Phase.FAILED, till.sheet.phase)
    assertEquals(StrVisualSearch.unreadable, till.sheet.error)
    //  پرچم میانِ کار خاموش شد ⇒ برگه بسته و دکمه پنهان
    src.answer = notFound
    till.send(byteArrayOf(1))
    assertFalse(till.sheet.open)
    assertFalse(VisualSearchLive.visible)

    //  صفحه‌ها: زدنِ «مشابه» فقط جست‌وجو/بازکردن است؛ فایلِ برگه به سبد و فاکتور راه ندارد
    fun code(rel: String): String =
      listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
        .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    val ui = code("ui/screens/VisualSearchUi.kt")
    for (bad in listOf("CartStore", "cartStore", "addToCart", "SalesEngine", "SalePost")) assertFalse(bad, ui.contains(bad))
    //  ⚠️ صندوقِ گوشیِ نسخهٔ ۳ دکمهٔ «جست‌وجو با عکس» ندارد؛ `ui/screens/QuickSale.kt` که داشت فقط پیش‌نمایشِ DevShot است
    //  (STATUS ِ AI-11، `claude/e-rows-28l`) — می‌ماند کامپیوتر
    assertTrue(code("ui/screens/DeskSale.kt").contains("Tap.Search) { query = t.query; refocus() }"))
    //  «باز کردن»: «محصولاتِ» گوشیِ رابطِ امروز (`Products.kt`، بی‌مرجع از نسخهٔ ۳) در «پاک‌سازی، گامِ ۳» پاک شد؛ می‌ماند کامپیوتر
    assertTrue(code("ui/screens/DeskProducts.kt").contains("Tap.Open) openId = t.productId"))
  }

  private fun code(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  @Test
  fun `نامی که با رقم آغاز می‌شود در کادرِ صندوق هرگز پیشوندِ شمار نمی‌شود`() {
    fun s(name: String) = VisualSearch.Suggestion("p-1", name, 10.0, "", false, 90)
    assertEquals(VisualSearch.Tap.Search("برنج"), VisualSearch.tap(s("برنج"), VisualSearch.Place.TILL))
    assertEquals(VisualSearch.Tap.Search("روغن 5 لیتری"), VisualSearch.tap(s("روغن 5 لیتری"), VisualSearch.Place.TILL))
    //  پیش از این «3x3 کابل» در کادرِ صندوق «سه تا از ‹3 کابل›» خوانده می‌شد و لمسِ بعدیِ کاشی سه تا می‌افزود
    assertEquals(3.0, ir.vil3ntec.tohid.data.CartQuery.parse("3x3 کابل").count, 0.0)
    for (name in listOf("3x3 کابل", "۲*۴ چوب", "١٠X پیچ", " 5 × 2 لوله", "7up")) {
      val t = VisualSearch.tap(s(name), VisualSearch.Place.TILL) as VisualSearch.Tap.Search
      val parsed = ir.vil3ntec.tohid.data.CartQuery.parse(t.query)
      assertEquals(name, 1.0, parsed.count, 0.0)
      assertEquals(name, name.trim(), parsed.term)
    }
    //  فهرستِ کالاها فقط باز می‌کند
    assertEquals(VisualSearch.Tap.Open("p-1"), VisualSearch.tap(s("3x3 کابل"), VisualSearch.Place.LIST))
  }

  @Test
  fun `صندوق و فروشِ سریع و کالاهای گوشیِ نسخهٔ ۳ جست‌وجو با عکس دارند`() {
    //  دکمه/ردیف فقط با پاسخِ «روشن» ِ سرور: `rememberVisualSearch` ⇒ null با پرچمِ خاموش یا بی حساب
    val ui = code("ui/screens/VisualSearchUi.kt")
    assertTrue(ui.contains("return if (VisualSearchLive.visible) handle else null"))
    assertTrue(ui.contains("val h = rememberVisualSearch(api, products, place) ?: return"))

    //  صندوقِ گوشی (`sale`): ردیفِ «…» ⇐ برگهٔ عکس؛ «مشابه» فقط کادرِ جست‌وجو با دستهٔ «همه»
    val route = code("ui/v3/sales/cart/SaleRoute.kt")
    assertTrue(route.contains("visual = rememberVisualSearch(rememberVisualSearchApi(), data.products, VisualSearch.Place.TILL)"))
    val sale = code("ui/v3/sales/cart/SaleScreen.kt")
    assertTrue(sale.contains("onVisualSearch = visual?.let { v -> { v.start() } }"))
    assertTrue(sale.contains("visual?.let { VisualSearchHost(it) { t -> if (t is VisualSearch.Tap.Search) { picked = null; search = t.query } } }"))
    val menu = code("ui/v3/sales/cart/SaleMenuSheet.kt")
    assertTrue(menu.contains("onVisualSearch?.let { find ->"))
    assertTrue(menu.contains("V3ListRow(StrVisualSearch.button, icon = Icons.Outlined.PhotoCamera"))

    //  فروشِ سریع (`sale/quick`، جانشینِ `ui/screens/QuickSale.kt`): دکمهٔ سربرگ
    assertTrue(code("ui/v3/sales/quick/QuickSaleRoute.kt")
      .contains("visual = rememberVisualSearch(rememberVisualSearchApi(), data.products, VisualSearch.Place.TILL)"))
    val quick = code("ui/v3/sales/quick/QuickSaleScreen.kt")
    assertTrue(quick.contains("actions = listOfNotNull(visual?.let { v -> V3Action(Icons.Outlined.PhotoCamera, StrVisualSearch.button) { v.start() } })"))
    assertTrue(quick.contains("visual?.let { VisualSearchHost(it) { t -> if (t is VisualSearch.Tap.Search) { picked = null; query = t.query } } }"))

    //  کالاها (`products`): سربرگ دو جا دارد (اسکن، شبکه/فهرست) ⇒ «…» ِ سربرگ (نه در حالتِ انتخاب)؛ «مشابه» همان بازکردنِ کالا
    val list = code("ui/v3/products/ProductsListScreen.kt")
    assertTrue(list.contains("val visual = rememberVisualSearch(rememberVisualSearchApi(), data.products, VisualSearch.Place.LIST)"))
    assertTrue(list.contains("overflow = if (selecting) emptyList() else listOfNotNull(visual?.let { v -> V3MenuItem(Icons.Outlined.PhotoCamera, StrVisualSearch.button) { v.start() } })"))
    assertTrue(list.contains("visual?.let { VisualSearchHost(it) { t -> if (t is VisualSearch.Tap.Open) nav.open(t.productId) } }"))

    //  هیچ‌کدام «مشابه» را به سبد نمی‌برد
    for (rel in listOf("ui/v3/sales/cart/SaleScreen.kt", "ui/v3/sales/quick/QuickSaleScreen.kt", "ui/v3/products/ProductsListScreen.kt")) {
      val host = code(rel).lines().filter { it.contains("VisualSearchHost(") }
      assertEquals(rel, 1, host.size)
      for (bad in listOf("actions.tap", "addOne", "tapCount", "cart.")) assertFalse("$rel ⇐ $bad", host[0].contains(bad))
    }
  }
}
