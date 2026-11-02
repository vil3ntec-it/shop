package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.ui.screens.DeskPayCell
import ir.vil3ntec.tohid.ui.v3.sales.history.PayStatusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-07 در فاکتورهای کامپیوتر (`DeskInvoices`): ستونِ «وضعیت» و بخشِ «وضعیتِ پرداخت» همان منطقِ برگهٔ گوشی‌اند —
 *  ثبتِ دستی «تأییدشده»، اصلاح فقط با صاحب/مدیر، ردیفِ درگاه و فاکتورِ لغوشده بی گذار، دفترِ بی سند ⇒ همان ستونِ امروز.
 */
class DeskPayStatusTest {

  private val day = "2026-11-02"
  private val sale = Sale(
    id = "s1", invoiceNumber = 12, finalTotal = 2000.0, createdAt = 1100, date = day, paymentMethod = "mixed",
    payments = listOf(
      SalePayment("cash", "cash", 50_000, "confirmed"),
      SalePayment("afpay_card", "card", 150_000, "pending", "TRM-9", "manual"),
    ),
  )
  private val base = ShopData(sales = listOf(sale))

  private fun moved(d: ShopData, to: String): ShopData =
    when (val b = PayStatusFlow.build(d, "ps-$to", PayStatusFlow.Request("s1", 1, to, "مدیر", sell = false, manage = true), day, 1200)) {
      is PayStatusFlow.Built.Ok -> d.copy(auditLog = d.auditLog + b.row)
      is PayStatusFlow.Built.Bad -> error("رد شد: ${b.code}")
    }

  @Test fun `ثبتِ دستی روی کامپیوتر تأییدشده است و فقط صاحب و مدیر گذار دارند`() {
    val owner = PayStatusFlow.desk(base, sale, sell = true, manage = true).single()
    assertEquals(1, owner.view.index)
    assertEquals(PayStatus.CONFIRMED, owner.view.status)
    assertTrue(owner.view.manual)
    assertEquals(PayStatus.allowed(PayStatus.CONFIRMED, true, true), owner.targets)
    assertTrue(PayStatus.NEEDS_REVIEW in owner.targets)
    //  صندوقدار: «تأییدشده» گامی ندارد (همان «کاری برای شما نیست» ِ گوشی)
    assertTrue(PayStatusFlow.desk(base, sale, sell = true, manage = false).single().targets.isEmpty())
  }

  @Test fun `ستونِ وضعیت بی سند همان «ثبت‌شده» و با سند نامِ وضعیت`() {
    assertNull(PayStatusFlow.chip(base, sale))
    assertEquals("ثبت", DeskPayCell.text(false, null, "ثبت", "لغو"))
    assertEquals("لغو", DeskPayCell.text(true, PayStatus.NEEDS_REVIEW, "ثبت", "لغو"))
    val d = moved(base, PayStatus.NEEDS_REVIEW)
    val chip = PayStatusFlow.chip(d, sale)
    assertEquals(PayStatus.NEEDS_REVIEW, chip)
    assertEquals(PayStatusText.name(PayStatus.NEEDS_REVIEW), DeskPayCell.text(false, chip, "ثبت", "لغو"))
    //  حلِ بررسی از همان بخش
    val row = PayStatusFlow.desk(d, sale, sell = false, manage = true).single()
    assertEquals(PayStatus.NEEDS_REVIEW, row.view.status)
    assertTrue(PayStatus.CONFIRMED in row.targets)
  }

  @Test fun `فاکتورِ لغوشده، ردیفِ درگاه و فاکتورِ تمام‌نقد بی گذارند`() {
    val cancelled = sale.copy(status = "cancelled")
    assertTrue(PayStatusFlow.desk(base.copy(sales = listOf(cancelled)), cancelled, true, true).single().targets.isEmpty())
    val gw = sale.copy(payments = listOf(SalePayment("hesabpay", "wallet", 150_000, "confirmed", "HP-1", "gateway")))
    PayStatusFlow.desk(base.copy(sales = listOf(gw)), gw, true, true).forEach { assertTrue(it.targets.isEmpty()) }
    val cash = sale.copy(payments = listOf(SalePayment("cash", "cash", 200_000, "confirmed")))
    assertTrue(PayStatusFlow.desk(base.copy(sales = listOf(cash)), cash, true, true).isEmpty())
  }
}
