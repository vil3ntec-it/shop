package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.stock.OnlineHolds
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.PosProductCallbacks
import ir.vil3ntec.tohid.ui.components.pos.ProductGrid
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test

/**
 *  POS-05 / INV-03 — آزمونِ صفحهٔ خودِ کاشیِ صندوق (`ProductGrid`/`ProductCard` با همان `PosProductCallbacks.stockOf` ِ `DeskSale`):
 *  «موجودی» ِ روی کارت قابل‌فروش است = موجودی − نگه‌داشته‌های همین صندوق − رزروِ فعالِ سفارش‌های اینترنتی (پاسخِ
 *  `GET /api/shop/catalog/reserved`)؛ رزروی که همهٔ مانده را برد ⇒ «ناموجود»؛ بی رزرو (پرچمِ خاموش) همان موجودیِ کامل.
 */
class PosSellableTileUiTest {

  @get:Rule val rule = createComposeRule()

  private val d = ShopData(
    products = listOf(
      Product(id = "cola", name = "نوشابه", unit = "عدد", salePrice = 60.0),
      Product(id = "rice", name = "برنج", unit = "کیلو", salePrice = 120.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "cola", units = 5.0),
      WarehouseEntry(id = "w2", productId = "rice", units = 4.0),
    ),
  )

  private val server = 1_760_000_000_000L
  private val reply = Json.parseToJsonElement(
    """{"enabled":true,"now":$server,"lines":[
      {"productId":"cola","qtyMilli":2000,"saleItemId":"o-A-1","expiresAt":null},
      {"productId":"rice","qtyMilli":4000,"saleItemId":"o-B-1","expiresAt":${server + 600_000}}]}""",
  ).jsonObject

  private fun show(online: Map<String, Double>) {
    I18n.source = { Lang.FA }
    val local = HeldStock.reserved(HeldBook(items = listOf(HeldInvoice(id = "h1", number = 1, lines = listOf(SaleDraft.Line("cola", 1.0))))))
    val held = HeldStock.withOnline(local, online)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          ProductGrid(
            items = d.products, highlight = -1, showPhotos = false,
            cb = PosProductCallbacks(stockOf = { HeldStock.sellable(ShopStore.stock(d, it), it, held) }, qtyOf = { 0.0 }, onAdd = {}),
            state = rememberLazyGridState(), minCell = 160.dp,
          )
        }
      }
    }
    rule.waitForIdle()
  }

  @Test fun tileShowsSellableAfterTillHoldsAndOnlineOrderReservations() {
    val snap = OnlineHolds.parse(reply, 10_000L)!!
    show(OnlineHolds.reserved(snap, 10_000L) { false })
    //  پنج نوشابه − یکی نگه‌داشتهٔ صندوق − دوتا سفارشِ اینترنتی ⇒ دو؛ چهار برنج همه رزرو ⇒ ناموجود
    rule.onNodeWithText(StrSale.inStockQty(qty(2.0) + " عدد"), substring = true).assertExists()
    rule.onNodeWithText(Str.outOfStock, substring = true).assertExists()
  }

  @Test fun withoutOnlineReservationTheTileKeepsTodaysNumbers() {
    show(OnlineHolds.reserved(null, 10_000L) { false })
    rule.onNodeWithText(StrSale.inStockQty(qty(4.0) + " عدد"), substring = true).assertExists()
    rule.onNodeWithText(StrSale.inStockQty(qty(4.0) + " کیلو"), substring = true).assertExists()
  }
}
