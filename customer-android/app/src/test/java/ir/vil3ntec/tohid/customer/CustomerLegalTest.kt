package ir.vil3ntec.tohid.customer

import ir.vil3ntec.tohid.core.legal.Legal
import ir.vil3ntec.tohid.core.legal.LegalKind
import ir.vil3ntec.tohid.core.shopper.ShopperApi
import ir.vil3ntec.tohid.core.shopper.ShopperHttp
import ir.vil3ntec.tohid.core.shopper.ShopperKeyValue
import ir.vil3ntec.tohid.core.shopper.ShopperLang
import ir.vil3ntec.tohid.core.shopper.ShopperResponse
import ir.vil3ntec.tohid.core.shopper.ShopperText
import ir.vil3ntec.tohid.customer.ui.CustomerModel
import ir.vil3ntec.tohid.customer.ui.LegalRoute
import ir.vil3ntec.tohid.customer.ui.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «حریمِ خصوصی» و «شرایط» درونِ برنامهٔ خرید (`LegalScreen.kt`): از «تنظیمات» باز می‌شوند، «برگشت» به همان تنظیمات، هیچ
 *  درخواستی به شبکه نمی‌رود (متن درونِ برنامه است)، و هر زبانِ برنامه متنِ خودش را می‌گیرد. فقط در CI (`testDebugUnitTest`)؛
 *  خودِ متن را `core` ‹`LegalTest`› و صفحه را آزمونِ کامپیوتر می‌سنجند.
 */
class CustomerLegalTest {

  @After fun reset() {
    ShopperText.lang = ShopperLang.FA
  }

  private class MapKv : ShopperKeyValue {
    val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  private val calls = mutableListOf<String>()
  private val http = ShopperHttp { r ->
    calls += r.path
    ShopperResponse(404, """{"error":{"code":"not_found"}}""")
  }

  private fun model() = CustomerModel(
    ShopperApi(http) { ShopperText.lang.code }, MapKv(), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, { 1_000L }, debounceMs = 0,
  )

  @Test fun legalPagesOpenFromSettingsAndGoBackWithoutNetwork() {
    val m = model()
    m.go(Route.Settings)
    val before = calls.size
    for (k in LegalKind.entries) {
      m.go(LegalRoute(k))
      assertEquals(LegalRoute(k), m.route)
      assertTrue(m.back())
      assertEquals(Route.Settings, m.route)
    }
    assertEquals("صفحهٔ حقوقی درخواستی به شبکه نمی‌فرستد", before, calls.size)
  }

  @Test fun eachAppLanguageGetsItsOwnText() {
    //  دری همان فارسی از لایهٔ واژه است (عنوان می‌تواند یکی بماند)؛ سه زبانِ نوشته جدا
    val titles = listOf(ShopperLang.FA, ShopperLang.EN, ShopperLang.PS).map { Legal.doc(LegalKind.PRIVACY, it.code).title }
    assertEquals(3, titles.toSet().size)
    for (l in ShopperLang.entries) {
      val d = Legal.doc(LegalKind.TERMS, l.code)
      assertTrue(d.sections.size >= 9)
      assertFalse(d.title.contains("github", ignoreCase = true))
    }
  }
}
