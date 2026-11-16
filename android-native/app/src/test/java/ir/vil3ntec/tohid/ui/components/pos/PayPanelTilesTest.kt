package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.pay.TillMethods
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.pay.HesabPaySettle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  e-paymethods — کارِ هر کاشیِ «روشِ پرداخت» روی پنلِ ترکیبی (`PayPanelState`)؛ حساب همه `PayFlow`/هستهٔ pay-core:
 *   - نقدِ خودکار ⇒ فروشِ نقدیِ کامل (همان «نقدی» ِ پیشین) و با تغییرِ مبلغ همراه؛
 *   - کارت/کیف پول/حواله ⇒ ردیفِ همان روش با باقیِ فاکتور، بی شمارهٔ مرجع ثبت نه، دوتایی نه؛
 *   - نسیه ⇒ باقی به حسابِ مشتری، بی مشتری «کوتاه»؛
 *   - حساب‌پی ⇒ نقدی که همه را می‌پوشاند کنار تا حساب‌پی باقی را بگیرد.
 */
class PayPanelTilesTest {

  private fun panel() = PayPanelState().apply { cashAuto = true }

  @Test fun `نقدِ خودکار همان فروشِ نقدیِ کامل است و با تخفیف همراه می‌شود`() {
    val p = panel()
    p.syncAuto(1530.0, null)
    assertEquals("1530", p.cash)
    val s = PayFlow.checkout(p.draft(1530.0, null)).valueOrNull()!!
    assertEquals("cash", PayFlow.legacyMethod(s))
    assertEquals(0L, s.change)
    //  تخفیف ⇒ مبلغ پایین آمد و نقدِ خودکار هم (نه پولِ پسِ ساختگی)
    p.syncAuto(1400.5, null)
    assertEquals("1400.5", p.cash)
    //  با مشتری هم نقد است، نه نسیهٔ ناخواسته
    val withCustomer = PayFlow.checkout(p.draft(1400.5, "c1")).valueOrNull()!!
    assertEquals(0L, withCustomer.toAccount)
    //  نوشتنِ دستی ⇒ دیگر خودکار نیست
    p.cashAuto = false
    p.cash = "2000"
    p.syncAuto(1000.0, null)
    assertEquals("2000", p.cash)
    assertEquals(100_000L, PayFlow.checkout(p.draft(1000.0, null)).valueOrNull()!!.change)
  }

  @Test fun `کارت کلِ فاکتور را از نقدِ خودکار می‌گیرد، بی شمارهٔ تأیید هم ثبت می‌شود و با آن همان ثبتِ دستی است`() {
    val p = panel()
    p.syncAuto(1080.0, null)
    val i = p.takeMethod("afpay_card", 1080.0)
    assertEquals(false, p.cashAuto)
    assertEquals("", p.cash)
    assertEquals(PayFlow.DraftRow("afpay_card", "", "1080"), p.rows[i])
    assertEquals(setOf(TillMethods.Kind.CARD), p.tileKinds())
    //  صاحب‌کار ۱۴۰۵/۰۸/۲۶: «کارت‌خوان تأیید کرد» بس است — شمارهٔ تأییدِ کارت اختیاری (پیش‌تر REF_REQUIRED)
    val bare = PayFlow.checkout(p.draft(1080.0, null)).valueOrNull()!!.payments.single { it.amount > 0 }
    assertEquals(listOf("afpay_card", "card", null, "manual"), listOf(bare.method, bare.kind, bare.ref, bare.verification))
    p.rows[i] = p.rows[i].copy(ref = "TRM-1")
    val s = PayFlow.checkout(p.draft(1080.0, null)).valueOrNull()!!
    val card = s.payments.single { it.amount > 0 }
    assertEquals(listOf("afpay_card", "card", "TRM-1", "manual"), listOf(card.method, card.kind, card.ref, card.verification))
    //  دوباره «کارت» ⇒ همان ردیف، نه ردیفِ دوم
    assertEquals(i, p.takeMethod("afpay_card", 1080.0))
    assertEquals(1, p.rows.size)
  }

  @Test fun `نقدِ بخشیِ دستی می‌ماند و روشِ دوم فقط باقی را می‌گیرد`() {
    val p = PayPanelState()
    p.cash = "500"
    p.takeMethod("e_money", 1080.0)
    assertEquals("500", p.cash)
    assertEquals("580", p.rows.single().amount)
    //  نقدی که درست همان باقی را می‌پوشاند کنار می‌رود و همان مبلغ به حواله می‌آید
    p.takeMethod("hawala", 1080.0)
    assertEquals("", p.cash)
    assertEquals(PayFlow.DraftRow("hawala", "", "500"), p.rows.last())
    assertEquals(setOf(TillMethods.Kind.WALLET, TillMethods.Kind.TRANSFER), p.tileKinds())
    //  کیف پولِ دیگر ⇒ همان ردیفِ کیف پول (دوتایی نه)
    assertEquals(0, p.takeMethod("my_money", 1080.0))
    //  ردیف‌ها همه را پوشانده‌اند ⇒ کارت بی مبلغ (صندوقدار خودش می‌نویسد)
    p.takeMethod("afpay_card", 1080.0)
    assertEquals(PayFlow.DraftRow("afpay_card", "", ""), p.rows.last())
    assertEquals(3, p.rows.size)
  }

  @Test fun `نسیه باقی را به حسابِ مشتری می‌برد؛ بی مشتری فروش کوتاه است`() {
    val p = panel()
    p.syncAuto(900.0, "c1")
    p.takeCredit()
    assertEquals("", p.cash)
    val s = PayFlow.checkout(p.draft(900.0, "c1")).valueOrNull()!!
    assertEquals(90_000L, s.toAccount)
    assertEquals("credit", PayFlow.legacyMethod(s))
    assertEquals("SHORT", (PayFlow.checkout(p.draft(900.0, null)) as PayFlow.Outcome.Bad).problem.code)
    //  کاشیِ «نقد» دوباره ⇒ همان نقدِ کامل
    p.takeCash(900.0, "c1")
    assertEquals("900", p.cash)
    assertTrue(p.cashAuto)
  }

  @Test fun `حساب‌پی — نقدی که همه را می‌پوشاند کنار، نقدِ بخشی می‌ماند و حساب‌پی باقی را می‌گیرد`() {
    val p = panel()
    p.syncAuto(1200.0, null)
    p.clearCoveringCash(1200.0)
    assertEquals("", p.cash)
    assertEquals(HesabPaySettle.Due.Amount(120_000L), HesabPaySettle.due(1200.0, p.draft(1200.0, null)))
    val q = PayPanelState()
    q.cash = "200"
    q.clearCoveringCash(1200.0)
    assertEquals("200", q.cash)
    assertEquals(HesabPaySettle.Due.Amount(100_000L), HesabPaySettle.due(1200.0, q.draft(1200.0, null)))
  }
}
