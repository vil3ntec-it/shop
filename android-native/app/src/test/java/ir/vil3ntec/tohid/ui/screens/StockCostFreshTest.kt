package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReportPages
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-15 — زبانهٔ «بهای میانگین» ِ گزارشِ تازه: فقط با پول، بی بازه، جدولِ فقط‌خواندنیِ کالا/مقدار/بهای یک واحد/ارزش و
 *  جمعِ کالاهای مثبت؛ کالای منفی بیرون از جمع با یادداشت.
 */
class StockCostFreshTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val base = listOf(ReportBoard.TabDef("sales", "فروش", true))

  //  برنج: ۱۰ × ۱۰۰ و ۷ × ۱۳۳ ⇒ فروشِ ۵ ⇒ ۱۲ عدد به ۱۳۶۳٫۰۶؛ کبریت: فروش پیش از هر ورود ⇒ −۱ و بی بها
  private val d = ShopData(
    products = listOf(Product("r", name = "برنج", purchasePrice = 133.0), Product("m", name = "کبریت")),
    warehouseEntries = listOf(
      WarehouseEntry("w1", productId = "r", units = 10.0, price = 100.0, createdAt = 1L),
      WarehouseEntry("w2", productId = "r", units = 7.0, price = 133.0, createdAt = 2L),
    ),
    sales = listOf(Sale("s1", total = 800.0, finalTotal = 800.0, date = "2026-11-01", createdAt = 3L)),
    saleItems = listOf(
      SaleItem("i1", saleId = "s1", productId = "r", quantity = 5.0, unitPrice = 150.0, purchasePrice = 133.0, totalPrice = 750.0),
      SaleItem("i2", saleId = "s1", productId = "m", quantity = 1.0, unitPrice = 50.0, totalPrice = 50.0),
    ),
  )

  @Test
  fun tabOnlyForMoneyWithoutARange() {
    assertEquals(base, StockCostFresh.tabs(base, money = false))
    val on = StockCostFresh.tabs(base, money = true)
    assertEquals(StockCostFresh.TAB_ID, on.last().id)
    assertEquals(StrReportPages.stockCostTab, on.last().label)
    assertFalse(on.last().ranged)
  }

  @Test
  fun readOnlyTableWithPositiveTotalAndNegativeNote() {
    val c = ReportBridge.stockCostOf(d)
    val t = StockCostFresh.tables(c).single()
    assertEquals(listOf("name", "qty", "unitCost", "value", "approx"), t.columns.map { it.key })
    assertEquals(listOf("text", "qty", "money", "money", "flag"), t.columns.map { it.kind })
    //  ۱۹۳۱۰۰ − round(۱۹۳۱۰۰ × ۵ ÷ ۱۷) = ۱۳۶۳۰۶ برای ۱۲ عدد ⇒ یک واحد round(۱۱۳۵۸٫۸۳) = ۱۱۳۵۹
    assertEquals(
      listOf(
        listOf(JsonPrimitive("کبریت"), JsonPrimitive(-1_000L), JsonNull, JsonPrimitive(0L), JsonPrimitive(true)),
        listOf(JsonPrimitive("برنج"), JsonPrimitive(12_000L), JsonPrimitive(11_359L), JsonPrimitive(136_306L), JsonPrimitive(false)),
      ),
      t.rows,
    )
    assertEquals(JsonPrimitive(136_306L), t.total!![3])
    assertEquals(JsonPrimitive(false), t.total!![4])
    assertTrue(StockCostFresh.note(c).endsWith(StrReportPages.negativeStock(1)))
    assertEquals(StrReportPages.stockCostNote, StockCostFresh.note(ReportBridge.stockCostOf(d, listOf("r"))))
    assertEquals(emptyList<Any>(), StockCostFresh.tables(ReportBridge.stockCostOf(ShopData())))
  }
}
