package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.products.NewProductsScreen
import ir.vil3ntec.tohid.ui.v3.products.ProductFormNav
import ir.vil3ntec.tohid.ui.v3.products.ProductRowDraft
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import org.junit.Assume
import org.junit.Test

/**
 *  عکسِ فرمِ جمع‌وجورِ «کالای جدید» (۱۴۰۵/۰۸/۲۴) بی پنجره ([ScreenRender]) — فارسی، روشن و تیره، گوشیِ ۳۶۰dp: فرمِ خالیِ
 *  نخست، کارتِ پرشده با «اختیاری» (دو کارتن، تولید و انقضا) کنارِ کارتِ بسته، و خطاهای درجا پس از «ذخیره». فقط با
 *  `TOHID_RENDER_OUT` (همان گزارشِ `NewScreensRenderTest`)؛ هرگز سد نیست و یافته‌ها در `render-report.json` ِ همان پوشه.
 */
class NewProductFormRenderTest {

  private val data = ShopData(productCategories = listOf("خوراکی", "روغن", "شوینده"), productUnits = listOf("عدد", "کارتن", "کیلوگرم"))

  private val filled = listOf(
    ProductRowDraft(key = 0, name = "روغن آفتاب ۵ لیتر", category = "روغن", unit = "کارتن", purchase = "2400", sale = "2650",
      barcode = "6260011223344", stock = "2", made = "14050610", expiry = "14050915"),
    ProductRowDraft(key = 1, name = "چای سبز", category = "خوراکی", unit = "عدد", purchase = "80", sale = "100", stock = "24"),
  )
  private val broken = listOf(
    ProductRowDraft(key = 0, name = "ماست", category = "خوراکی", purchase = "40", sale = "60", stock = "", made = "14050820", expiry = "1405"),
  )

  @Composable
  private fun Form(seed: List<ProductRowDraft>?, errors: Boolean) {
    val toasts = rememberV3Toasts(remember { SnackbarHostState() })
    val store = remember { RouteUiHarness.memoryStore(data) }
    CompositionLocalProvider(LocalV3Toasts provides toasts) {
      NewProductsScreen(store, data, toasts, null, ProductFormNav(back = {}, scan = {}), seed = seed, showErrors = errors) {}
    }
  }

  @Test fun renderTheNewProductForm() {
    RenderReport.skipReason()?.let { Assume.assumeTrue(it, false) }
    val results = ArrayList<ScreenRender.Result>()
    for (dark in listOf(false, true)) {
      val theme = if (dark) "dark" else "light"
      results += ScreenRender.render("new-product-empty-$theme-fa", 360, 900, en = false, dark = dark) { Form(null, false) }
      results += ScreenRender.render("new-product-filled-$theme-fa", 360, 1180, en = false, dark = dark) { Form(filled, false) }
      results += ScreenRender.render("new-product-errors-$theme-fa", 360, 1100, en = false, dark = dark) { Form(broken, true) }
    }
    RenderReport.write(results, null)
  }
}
