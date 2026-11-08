package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReportPages
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.PromoEffectBook
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalePromo
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRM-16 — صفحهٔ «اثرِ تخفیف» ِ گزارشِ تازه: زبانه فقط با پول و قلمِ تخفیف‌خورده، سود با میانگینِ موزونِ مصوب، «تقریبی»
 *  با قیمتِ روی قلم یا بهای نامعلوم، ردیفِ جمع (هر قلم یک بار) و شکستِ هسته بی عددِ غلط.
 */
class PromoFreshTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val base = listOf(ReportBoard.TabDef("sales", "فروش", true))
  private val today = "2026-11-08"
  private val week = SalePromo("pr1", 1, "تخفیفِ هفته", 2_000)

  //  ۱۰ عدد به ۵۰ و ۱۰ عدد به ۷۰ ⇒ میانگین ۶۰؛ قیمتِ روی قلم (آخرین خرید) ۷۰
  private val d = ShopData(
    products = listOf(Product("p", name = "شیر", purchasePrice = 70.0, salePrice = 90.0)),
    warehouseEntries = listOf(
      WarehouseEntry("w1", productId = "p", units = 10.0, price = 50.0, date = "2026-11-01", createdAt = 1L),
      WarehouseEntry("w2", productId = "p", units = 10.0, price = 70.0, date = "2026-11-02", createdAt = 2L),
    ),
    sales = listOf(
      Sale("s1", total = 160.0, finalTotal = 160.0, date = "2026-11-05", createdAt = 3L),
      Sale("s2", total = 80.0, finalTotal = 80.0, date = "2026-11-06", createdAt = 4L),
    ),
    saleItems = listOf(
      SaleItem("i1", saleId = "s1", productId = "p", quantity = 2.0, unitPrice = 80.0, purchasePrice = 70.0, totalPrice = 160.0, promo = listOf(week)),
      SaleItem("i2", saleId = "s2", productId = "p", quantity = 1.0, unitPrice = 80.0, purchasePrice = 70.0, totalPrice = 80.0),
    ),
  )

  @Test
  fun tabOnlyForMoneyAndOnlyWithPromoLines() {
    assertTrue(PromoEffectBook.has(d))
    assertFalse(PromoEffectBook.has(d.copy(saleItems = d.saleItems.map { it.copy(promo = emptyList()) })))
    assertEquals(base, PromoFresh.tabs(base, money = false, has = true))
    assertEquals(base, PromoFresh.tabs(base, money = true, has = false))
    val on = PromoFresh.tabs(base, money = true, has = true)
    assertEquals(PromoFresh.TAB_ID, on.last().id)
    assertEquals(StrReportPages.promoTab, on.last().label)
    assertTrue("بازه دارد", on.last().ranged)
  }

  @Test
  fun approvedMovingAverageGivesFinalProfitAndTheTotalCountsEachLineOnce() {
    val v = PromoFresh.view(d, ReportBridge.ledgerOf(d), "month", today, null)
    assertEquals(CostMethod.MOVING_AVERAGE, v.method)
    val r = v.report!!
    assertFalse("میانگینِ موزونِ مصوب، همهٔ قلم‌ها بهادار", r.profitApproximate)
    val row = r.rows.single()
    //  فروش ۱۶۰، بها ۲ × ۶۰ = ۱۲۰ ⇒ سود ۴۰ (با قیمتِ روی قلم ۲۰ می‌شد)
    assertEquals(4_000L, row.profitMinor)
    val t = PromoFresh.tables(v).single()
    assertEquals(listOf("name", "invoices", "qty", "discount", "revenue", "profit", "share", "depth", "approx"), t.columns.map { it.key })
    assertEquals("money", t.columns.single { it.key == "profit" }.kind)
    assertEquals("روشِ مصوب ⇒ بی برچسبِ «تقریبی»", StrReportPages.colProfit, t.columns.single { it.key == "profit" }.label)
    assertEquals(
      listOf(JsonPrimitive("تخفیفِ هفته"), JsonPrimitive(1L), JsonPrimitive(2_000L), JsonPrimitive(2_000L), JsonPrimitive(16_000L), JsonPrimitive(4_000L), JsonPrimitive(6_667L), JsonPrimitive(1_111L), JsonPrimitive(false)),
      t.rows.single(),
    )
    //  جمع: فروشِ تخفیف‌خورده ۱۶۰ از ۲۴۰ = ۶۶٫۶۷٪؛ سود و فاکتور در جمع نیستند (قلمِ دوتخفیفه دو بار شمرده می‌شد)
    val total = t.total!!
    assertEquals(JsonPrimitive(2_000L), total[3])
    assertEquals(JsonPrimitive(16_000L), total[4])
    assertEquals(JsonNull, total[5])
    assertEquals(JsonPrimitive(6_667L), total[6])
    assertTrue(PromoFresh.note(v).startsWith(StrReportPages.promoNote))
  }

  @Test
  fun purchasePriceOnTheLineIsAlwaysApproximateAndLabelled() {
    val v = PromoFresh.view(d, ReportBridge.ledgerOf(d), "month", today, null, CostMethod.PURCHASE_PRICE)
    val r = v.report!!
    assertTrue(r.profitApproximate)
    assertEquals(2_000L, r.rows.single().profitMinor)
    val t = PromoFresh.tables(v).single()
    assertTrue(t.columns.single { it.key == "profit" }.label.contains("تقریبی"))
    assertEquals(JsonPrimitive(true), t.rows.single().last())
  }

  @Test
  fun unknownCostIsNullAndApproximateNeverAFakeNumber() {
    //  فروش پیش از هر ورود و بی قیمتِ خرید ⇒ بها نامعلوم
    val blind = d.copy(warehouseEntries = emptyList(), saleItems = d.saleItems.map { it.copy(purchasePrice = 0.0) })
    val v = PromoFresh.view(blind, ReportBridge.ledgerOf(blind), "month", today, null)
    val row = v.report!!.rows.single()
    assertNull(row.profitMinor)
    assertEquals(1, row.costMissingLines)
    assertTrue(row.profitApproximate)
    assertEquals(JsonNull, PromoFresh.tables(v).single().rows.single()[5])
  }

  @Test
  fun emptyPeriodAndCoreRefusalShowANoteNotATable() {
    val v = PromoFresh.view(d, ReportBridge.ledgerOf(d), "today", today, null)
    assertTrue(v.report!!.rows.isEmpty())
    assertEquals(emptyList<ReportTables.Table>(), PromoFresh.tables(v))
    assertEquals(StrReportPages.promoNone, PromoFresh.note(v))
    //  مقدارِ بیرون از بازه ⇒ هسته خطا می‌دهد، صفحه پیام (نه عدد)
    val huge = d.copy(saleItems = d.saleItems.map { it.copy(quantity = 2_000_000.0) })
    val bad = PromoFresh.view(huge, ReportBridge.ledgerOf(huge), "month", today, null)
    assertNull(bad.report)
    assertEquals(StrReportPages.promoFailed, PromoFresh.note(bad))
    assertEquals(emptyList<ReportTables.Table>(), PromoFresh.tables(bad))
  }
}
