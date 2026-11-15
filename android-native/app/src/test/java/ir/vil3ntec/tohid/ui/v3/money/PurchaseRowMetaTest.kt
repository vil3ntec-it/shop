package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  ۶۸c — خطِ مبلغِ یک‌خطیِ ردیفِ «خریدها» (`PurchaseRowText.meta`، کالبدِ `V3DataRow`؛ صاحب‌کار: «۱,۴۵۰ ؋ • نسیه — سررسید / ۴ قوس •
 *  رسیده» در دو خطِ زرد): مبلغ و **یک** وضعیتِ کوتاهِ پرداخت؛ «رسیده» ِ عادی در فهرست نیست؛ فقط «سررسید گذشته» خطر است.
 *  جزئیات (`pay`/`receipt`) همان نوشتهٔ کامل را دارند.
 */
class PurchaseRowMetaTest {
  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private val today = "2026-11-15"
  private var n = 0
  private val d0 = ShopData(
    products = listOf(Product(id = "p0", name = "چای سبز", unit = "کیلو", purchasePrice = 10.0)),
    suppliers = listOf(Supplier(id = "s1", name = "عمده‌فروشی پامیر")),
  )

  private fun view(paid: Double, due: String? = null, qty: Double = 10.0): PurchaseViews.View {
    val d = (PurchaseBook.record(d0, "s1", listOf(PurchaseBook.Line("p0", qty, 145.0)), paid, "2026-11-14", "", today, 1, { "m${n++}" }, dueDate = due) as PurchaseBook.Result.Ok).data
    return PurchaseViews.all(d, today).single()
  }

  @Test fun `پرداخت‌شده و رسیده — فقط «پرداخت‌شده»، بی «رسیده»`() {
    val m = PurchaseRowText.of(view(1450.0), today).meta
    assertEquals(MoneyText.afn(1450.0), m.amount)
    assertEquals(StrPurchases.paid, m.status?.text)
    assertNull("«رسیده» ِ عادی در فهرست نمی‌آید", m.second)
    //  جزئیات هنوز هر دو را دارد
    assertEquals(StrPurchases.received, PurchaseRowText.of(view(1450.0), today).receipt?.text)
  }

  @Test fun `نسیه با سررسید — «سررسید …» کوتاه و آرام، نه «نسیه — سررسید …»`() {
    val m = PurchaseRowText.of(view(0.0, due = "2026-11-25"), today).meta
    assertTrue(m.status!!.text, m.status!!.text.startsWith(StrPurchases.dueOn("").trim()))
    assertTrue(!m.status!!.text.contains(StrPurchases.credit + " — "))
    assertEquals(V3RowTone.ATTENTION, m.status?.tone)
  }

  @Test fun `نیمه‌پرداخت — «۳٬۳۲۰ ؋ مانده» کوتاه`() {
    val m = PurchaseRowText.of(view(500.0), today).meta
    assertEquals(StrPurchases.leftAll(money(950.0)), m.status?.text)
  }

  @Test fun `سررسید گذشته — تنها خطرِ ردیف؛ ماندهٔ نیمه‌پرداخت کنارش آرام`() {
    val all = PurchaseRowText.of(view(0.0, due = "2026-11-10"), today).meta
    assertEquals(StrPurchases.overdueRow, all.status?.text)
    assertEquals(V3RowTone.DANGER, all.status?.tone)
    assertNull("همهٔ مبلغ مانده ⇒ ماندهٔ تکراری نه", all.second)
    val part = PurchaseRowText.of(view(450.0, due = "2026-11-10"), today).meta
    assertEquals(StrPurchases.overdueRow, part.status?.text)
    assertEquals(StrPurchases.leftAll(money(1000.0)), part.second?.text)
    assertEquals(V3RowTone.QUIET, part.second?.tone)
  }
}
