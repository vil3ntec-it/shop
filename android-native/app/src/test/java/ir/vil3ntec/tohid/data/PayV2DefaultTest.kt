package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  e-paymethods — پیش‌فرضِ «ترکیبی و شیفت» برای دستگاهی که هرگز انتخاب نکرده (`PayV2Default`):
 *   - انتخابِ صریح (روشن یا خاموش) هرگز عوض نمی‌شود؛
 *   - بی صندوقِ باز ⇒ جابه‌جایی؛
 *   - صندوقِ سادهٔ باز که در صندوقِ شیفت **همان** عدد را می‌گیرد ⇒ جابه‌جایی، و پس از آن X و Z همان «موردِ انتظار» ِ صندوقِ
 *     ساده را می‌گویند و هیچ ردیفی از دفتر عوض یا افزوده نشده؛
 *   - صندوقِ بازی که در صندوقِ شیفت عددِ دیگری می‌گیرد (رویدادی که هسته نمی‌پذیرد) ⇒ صبر تا بسته شدنش؛ پس از بستن ⇒ جابه‌جایی.
 */
class PayV2DefaultTest {

  private var seq = 0
  private fun newId(): String = "pd${seq++}"

  private fun opened(opening: Double = 1000.0, at: Long = 100): ShopData =
    (CashShift.open(ShopData(), opening, "علی", "2026-11-16", at, ::newId) as CashShift.Result.Ok).data

  /** روزِ معمولِ صندوقِ ساده: فروشِ نقدی، نسیه با پیش‌پرداخت، رسیدِ قرض‌دار، برگشت و مصرف */
  private fun busyDay(): ShopData = opened().let { d ->
    d.copy(
      sales = listOf(
        Sale(id = "s1", finalTotal = 500.0, createdAt = 110),
        Sale(id = "s2", finalTotal = 800.0, debtGiven = 600.0, paymentMethod = "credit", createdAt = 120),
        Sale(id = "s3", finalTotal = 999.0, status = "cancelled", createdAt = 130),
      ),
      transactions = listOf(
        DebtTransaction("t1", "c", "receive", 300.0, createdAt = 140),
        DebtTransaction("t2", "c", "give", 600.0, createdAt = 120),
      ),
      saleReturns = listOf(SaleReturn(id = "r1", amount = 150.0, createdAt = 150)),
      expenses = listOf(Expense(id = "e1", amount = 70.0, createdAt = 160)),
    )
  }

  @Test fun `انتخابِ صریح، روشن یا خاموش، دست نمی‌خورد — حتی بی صندوقِ باز`() {
    assertEquals(PayV2Default.Step.KEEP, PayV2Default.step(chosen = true, d = ShopData(), now = 1))
    assertEquals(PayV2Default.Step.KEEP, PayV2Default.step(chosen = true, d = busyDay(), now = 200))
  }

  @Test fun `هرگز انتخاب نکرده و هیچ صندوقِ باز نیست ⇒ ترکیبی و شیفت`() {
    assertEquals(PayV2Default.Step.ADOPT, PayV2Default.step(false, ShopData(), 1))
    val closed = (CashShift.close(busyDay(), 1780.0, "", "علی", "2026-11-16", 210, ::newId) as CashShift.Result.Ok).data
    assertEquals(null, CashShift.current(closed))
    assertEquals(PayV2Default.Step.ADOPT, PayV2Default.step(false, closed, 300))
  }

  @Test fun `صندوقِ سادهٔ باز با همان عدد ⇒ جابه‌جایی و همان موردِ انتظار در X و Z، بی هیچ ردیفِ تازه`() {
    val d = busyDay()
    val shift = CashShift.current(d)!!
    val legacy = CashShift.totalsOf(d, shift, 200).expected
    //  ۱۰۰۰ + ۵۰۰ + ۲۰۰ (پیش‌پرداختِ نسیه) + ۳۰۰ − ۱۵۰ − ۷۰
    assertEquals(1780.0, legacy, 1e-9)
    assertTrue(PayV2Default.sameUnderV2(d, shift, 200))
    val before = d.copy()
    assertEquals(PayV2Default.Step.ADOPT, PayV2Default.step(false, d, 200))
    //  قدم خالص است: دفتر همان شیء و همان ردیف‌ها
    assertEquals(before, d)
    //  پس از جابه‌جایی: X ِ صندوقِ شیفت روی همان `shift_open` همان عدد را می‌گوید، بی رویدادِ کنارگذاشته
    val x = PayFlow.report(d, shift.open, null, 200).valueOrNull()!!
    assertTrue(x.skipped.isEmpty())
    assertEquals(178_000L, x.report.expected!![PayFlow.CASH])
    //  فروشِ تازه با پنلِ ترکیبی هم در همان صندوق شمرده می‌شود
    val f = PayFlow.saleFields(PayFlow.checkout(PayFlow.Draft(250.0, "250")).valueOrNull()!!)
    val later = d.copy(sales = d.sales + Sale(id = "v2", finalTotal = 250.0, paymentMethod = f.paymentMethod, paidAmount = f.paidAmount,
      payments = f.payments, tenderedMinor = f.tenderedMinor, changeMinor = f.changeMinor, createdAt = 205))
    assertEquals(2030.0, CashShift.totalsOf(later, CashShift.current(later)!!, 206).expected, 1e-9)
    //  بستن با Z ِ صندوقِ شیفت: همان شیفت، همان موردِ انتظار، ردیفِ بستن با `refId` ِ همان بازکردن
    val z = PayFlow.closeRow(later, shift.open, PayFlow.Count(mapOf("100000" to 2L, "1000" to 3L)), 210, ::newId).valueOrNull()!!
    assertEquals(203_000L, z.report.expected!![PayFlow.CASH])
    assertEquals(0L, z.report.variance!![PayFlow.CASH])
    assertEquals(shift.open.id, z.row.refId)
    assertEquals(CashShift.CLOSE, z.row.type)
    val closed = later.copy(auditLog = later.auditLog + z.row)
    assertEquals(null, CashShift.current(closed))
    //  همان Z ِ ذخیره‌شده دوباره خوانده می‌شود (سابقه)
    assertEquals(z.report, PayFlow.report(closed, shift.open, z.row, 300).valueOrNull()!!.report)
  }

  @Test fun `صندوقِ بازی که در صندوقِ شیفت عددِ دیگری می‌گیرد ⇒ صبر، و پس از بستنش جابه‌جایی`() {
    //  مصرفِ بیش از نقدِ دخل: صندوقِ ساده موردِ انتظارِ منفی می‌گوید، هسته آن خروج را نمی‌پذیرد ⇒ دو عدد یکی نیستند
    val d = opened(100.0).copy(expenses = listOf(Expense(id = "big", amount = 500.0, createdAt = 150)))
    val shift = CashShift.current(d)!!
    assertEquals(-400.0, CashShift.totalsOf(d, shift, 200).expected, 1e-9)
    assertFalse(PayV2Default.sameUnderV2(d, shift, 200))
    assertEquals(PayV2Default.Step.WAIT, PayV2Default.step(false, d, 200))
    //  همان صندوقِ ساده بسته می‌شود (بی Z ِ هسته) ⇒ شروعِ بعدی جابه‌جا می‌کند و Z ِ پیشین دست نمی‌خورد
    val closed = (CashShift.close(d, 0.0, "", "علی", "2026-11-16", 210, ::newId) as CashShift.Result.Ok).data
    val close = closed.auditLog.last()
    assertEquals(PayV2Default.Step.ADOPT, PayV2Default.step(false, closed, 300))
    assertEquals(null, close.z)
    assertSame(close, CashShift.list(closed).single().close)
  }

  @Test fun `دو صندوقِ باز (کشوی نام‌دار) — همه باید همان عدد را بگیرند`() {
    var d = busyDay()
    d = (CashShift.openNamed(d, 50.0, "کشوی دو", "مریم", "2026-11-16", 170, ::newId) as CashShift.Result.Ok).data
    assertEquals(2, CashShift.list(d).count { it.isOpen })
    assertEquals(PayV2Default.Step.ADOPT, PayV2Default.step(false, d, 200))
    //  مصرفی که کشوی اصلی را منفی می‌کند ⇒ صبر
    val bad = d.copy(expenses = d.expenses + Expense(id = "big", amount = 5000.0, createdAt = 180))
    assertEquals(PayV2Default.Step.WAIT, PayV2Default.step(false, bad, 200))
  }
}
