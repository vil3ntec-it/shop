package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PUR-11 — منطقِ خالصِ فرمِ «ثبت خرید» و برگهٔ «پرداخت» با ارزِ دیگر (همان حسابِ ثبت) */
class PurchaseFxFormTest {
  private val tea = Product(id = "tea", name = "چای", unit = "بسته", purchasePrice = 650.0)
  private val d = ShopData(products = listOf(tea), suppliers = listOf(Supplier(id = "s1", name = "دوبی")))
  private val usd70 = PurchaseFx.Rate("USD", 7_000, 1)

  private fun usdForm(): PurchaseForm {
    val f0 = PurchaseFormModel.pickProduct(PurchaseFormModel.initial("s1", "2026-11-10"), tea)
    //  کالا پیش از ارز برگزیده شد ⇒ قیمتِ افغانیِ ۶۵۰ پیش‌پُر بود؛ ارزِ تازه پاکش می‌کند
    assertEquals("650", f0.price)
    return PurchaseFormModel.setCurrency(f0, "USD", usd70).copy(quantity = "10", price = "10")
  }

  @Test fun `ارزِ تازه قیمتِ افغانی را پاک و نرخ را از آخرین نرخ پیش‌پُر می‌کند`() {
    val f0 = PurchaseFormModel.pickProduct(PurchaseFormModel.initial("s1", "2026-11-10"), tea)
    val f = PurchaseFormModel.setCurrency(f0.copy(paid = "100"), "USD", usd70)
    assertEquals("USD", f.currency)
    assertEquals("", f.price)
    assertEquals("", f.paid)
    assertEquals("70", f.rate)
    assertEquals(1L, f.per)
    //  کلدار بی نرخِ پیشین ⇒ واحدِ نرخِ پیش‌فرضِ ۱٬۰۰۰
    assertEquals(1_000L, PurchaseFormModel.setCurrency(f0, "PKR").per)
    //  کالای تازه در فاکتورِ ارزی قیمتِ افغانی نمی‌گیرد
    assertEquals("", PurchaseFormModel.pickProduct(PurchaseFormModel.setCurrency(PurchaseFormModel.initial("s1", "2026-11-10"), "USD", usd70), tea).price)
    //  برگشت به افغانی ⇒ همان فرمِ پیشین (بی نرخ)
    val back = PurchaseFormModel.setCurrency(f, "AFN")
    assertNull(back.currency); assertEquals("", back.rate); assertEquals(1L, back.per)
  }

  @Test fun `جمع، معادلِ افغانی و پرداخت همان حسابِ ثبت‌اند`() {
    val f = usdForm()
    assertEquals(10_000L, PurchaseFormModel.fxTotal(f))
    assertEquals(7_000.0, PurchaseFormModel.total(f), 0.0)
    //  «همه را دادم» ⇒ همهٔ ۱۰۰ دالر؛ بخشی ⇒ مبلغِ تایپی به دالر
    assertEquals(10_000L, PurchaseFormModel.fxPaid(f))
    assertEquals(7_000.0, PurchaseFormModel.paid(f), 0.0)
    val part = PurchaseFormModel.setPay(f, PurchasePay.PART).copy(paid = "25.5")
    assertEquals(2_550L, PurchaseFormModel.fxPaid(part))
    assertEquals(1_785.0, PurchaseFormModel.paid(part), 0.0)
    assertEquals(5_215.0, PurchaseFormModel.remaining(part), 0.0)
    val ex = PurchaseFormModel.extras(part, on = false)
    assertEquals(usd70, ex.fx)
    assertEquals(2_550L, ex.fxPaid)
    val line = PurchaseFormModel.lines(part) { "بسته" }.single()
    assertEquals(1_000L, line.fxPrice)
    //  رقمِ فارسی و «٫» هم
    assertEquals(PurchaseFx.Rate("USD", 7_025, 1), PurchaseFormModel.fxRate(f.copy(rate = "۷۰٫۲۵")))
  }

  @Test fun `نرخ یا قیمتِ ارزیِ نادرست خطای درجا دارد`() {
    val f = usdForm()
    assertNull(PurchaseFormModel.errors(f, d).rate)
    assertEquals(StrPurchaseFx.rateBad, PurchaseFormModel.errors(f.copy(rate = ""), d).rate)
    assertEquals(StrPurchaseFx.rateBad, PurchaseFormModel.errors(f.copy(rate = "0"), d).rate)
    assertTrue(PurchaseFormModel.errors(f.copy(price = "abc"), d).price != null)
    //  ریال بی اعشار
    val irr = PurchaseFormModel.setCurrency(f, "IRR").copy(rate = "25", price = "12.5", quantity = "1")
    assertTrue(PurchaseFormModel.errors(irr, d).price != null)
    assertTrue(PurchaseFormModel.errors(f.copy(rate = ""), d).any)
  }

  @Test fun `برگهٔ پرداخت — حسابِ دالری، پیش‌نمایشِ اختلافِ نرخ و تسویهٔ کامل`() {
    val acc = PurchaseFx.Account("USD", 10_000, 700_000)
    val f = LedgerFormsModel.setAccount(PayForm(date = "2026-11-12"), "USD", usd70).copy(rate = "72", amount = "40")
    assertNull(LedgerFormsModel.payError(f))
    val st = LedgerFormsModel.fxSettle(f, acc)!!
    assertEquals(4_000L, st.fx); assertEquals(280_000L, st.carried); assertEquals(8_000L, st.diff)
    //  به افغانی: ۷٬۲۰۰ افغانی به نرخِ ۷۲ ⇒ همهٔ ۱۰۰ دالر
    val afn = f.copy(inAfn = true, amount = "7200")
    assertEquals(10_000L, LedgerFormsModel.fxSettle(afn, acc)!!.fx)
    assertEquals("100", LedgerFormsModel.settleAllFx(f, acc))
    assertEquals("7200", LedgerFormsModel.settleAllFx(afn, acc))
    //  بی نرخ ⇒ خطا؛ حسابِ افغانی همان برگهٔ پیشین
    assertEquals(StrPurchaseFx.rateBad, LedgerFormsModel.payError(f.copy(rate = "")))
    val plain = LedgerFormsModel.setAccount(f, null)
    assertNull(plain.account); assertEquals("", plain.amount)
    assertEquals(null, LedgerFormsModel.payError(plain.copy(amount = "500")))
  }
}
