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
}
