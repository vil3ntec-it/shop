package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.PayFix
import ir.vil3ntec.tohid.core.pay.ShiftRow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-40 — اصلاحِ روشِ پرداخت پس از فروش روی دفترِ دکان (`PayFixFlow`): سندِ جدای `pay_fix`، فاکتور دست نمی‌خورد،
 *  اثر در جمعِ کشو (`CashShift` و صندوقِ شیفت)، روزِ بسته رد، و دفترِ بی اصلاح همان حسابِ امروز.
 */
class PayFixFlowTest {

  private val day = "2026-11-01"
  private val open = AuditEntry(id = "o1", type = CashShift.OPEN, date = day, amount = 100.0, createdAt = 1000)
  private val cashSale = Sale(id = "s1", invoiceNumber = 7, finalTotal = 1000.0, createdAt = 1100, date = day)
  private val base = ShopData(sales = listOf(cashSale), auditLog = listOf(open))

  private fun req(from: String = "cash", to: String = "hesabpay", amount: Long = 40_000, decision: String = "allow", approvedBy: String? = null) =
    PayFixFlow.Request("s1", from, to, amount, "m1", decision, approvedBy, "مشتری با HesabPay داد", if (to == "cash") null else "HP-1")

  private fun fixed(d: ShopData, key: String = "pf1", now: Long = 1200, r: PayFixFlow.Request = req()): ShopData =
    when (val b = PayFixFlow.build(d, key, r, day, now)) {
      is PayFixFlow.Built.Ok -> d.copy(auditLog = d.auditLog + b.row)
      is PayFixFlow.Built.Bad -> error("رد شد: ${b.code}")
    }

  @Test fun `دفترِ بی اصلاح همان حسابِ امروز`() {
    assertTrue(PayFixFlow.bySale(base).isEmpty())
    assertEquals(1000.0, CashShift.saleCash(cashSale), 0.0)
    assertEquals(1100.0, CashShift.totals(base, 1000, 2000, 100.0, "o1").expected, 0.0)
    assertEquals(listOf(ShiftRow("cash", 100_000)), PayFlow.saleRows(cashSale))
  }

  @Test fun `سندِ جدا؛ فاکتور دست نمی‌خورد و جمعش همان`() {
    val d = fixed(base)
    assertEquals(base.sales, d.sales)
    val row = d.auditLog.last()
    assertEquals(PayFix.TYPE, row.type)
    assertEquals("s1", row.refId)
    assertEquals(400.0, row.amount, 0.0)
    assertEquals("cash", row.fromMethod)
    assertEquals("hesabpay", row.toMethod)
    assertEquals("HP-1", row.payRef)
    assertEquals("m1", row.by)
    assertEquals("m1", row.approvedBy)
    assertEquals("مشتری با HesabPay داد", row.reason)
    assertEquals(PayFixFlow.fixOf(row), PayFixFlow.history(d, "s1").single())
    val now = PayFixFlow.current(d, cashSale).rows
    assertEquals(listOf(PayFix.Row("cash", 60_000), PayFix.Row("hesabpay", 40_000)), now)
    assertEquals(100_000L, now.sumOf { it.amount })
  }

  @Test fun `اثر در جمعِ کشو — صندوقِ ساده و صندوقِ شیفت`() {
    val d = fixed(base)
    //  ۱۰۰ + ۱۰۰۰ − ۴۰۰ ِ رفته به HesabPay
    assertEquals(700.0, CashShift.totals(d, 1000, 2000, 100.0, "o1").expected, 0.0)
    val sale = PayFlow.ledgerEvents(d, open, 2000).single { it.type == "sale" }
    assertEquals(listOf(ShiftRow("cash", 60_000), ShiftRow("hesabpay", 40_000)), sale.rows)
    assertEquals(70_000L, (PayFlow.report(d, open, null, 2000) as PayFlow.Outcome.Ok).value.report.expected?.get("cash"))
    //  پنجره‌ای که پیش از ثبتِ اصلاح تمام شد آن را نمی‌شمارد
    assertEquals(1100.0, CashShift.totals(d, 1000, 1150, 100.0, "o1").expected, 0.0)
  }

  @Test fun `روزِ بسته (Z) رد می‌شود`() {
    val close = AuditEntry(id = "c1", type = CashShift.CLOSE, date = day, refId = "o1", amount = 1100.0, createdAt = 1150)
    val d = base.copy(auditLog = base.auditLog + close)
    assertTrue(PayFixFlow.closed(d, cashSale))
    assertEquals("DAY_CLOSED", (PayFixFlow.build(d, "pf1", req(), day, 1200) as PayFixFlow.Built.Bad).code)
    //  فروشِ پس از آخرین Z ِ همان روز، بیرون از هر صندوق، بسته نیست
    val later = cashSale.copy(id = "s2", createdAt = 1300)
    assertFalse(PayFixFlow.closed(d.copy(sales = d.sales + later), later))
  }

  @Test fun `اینترنتی، لغوشده، برگشت‌خورده و نسیه`() {
    fun code(d: ShopData) = (PayFixFlow.build(d, "pf1", req(), day, 1200) as PayFixFlow.Built.Bad).code
    assertEquals("SALE_ONLINE", code(base.copy(sales = listOf(cashSale.copy(channel = "online")))))
    assertEquals("SALE_CANCELLED", code(base.copy(sales = listOf(cashSale.copy(status = "cancelled")))))
    assertEquals("SALE_RETURNED", code(base.copy(saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", amount = 10.0, createdAt = 1150)))))
    assertEquals("SALE_REQUIRED", code(base.copy(sales = emptyList())))
    //  بخشِ «به حسابِ مشتری» ِ نسیه جابه‌جا نمی‌شود؛ پیش‌پرداختِ نقدش می‌شود
    val credit = base.copy(sales = listOf(cashSale.copy(debtGiven = 600.0, debtorId = "c1")))
    assertEquals("UNKNOWN_METHOD", (PayFixFlow.build(credit, "pf1", req(from = "account"), day, 1200) as PayFixFlow.Built.Bad).code)
    assertEquals("FIX_EXCEEDS", (PayFixFlow.build(credit, "pf1", req(amount = 40_001), day, 1200) as PayFixFlow.Built.Bad).code)
    assertTrue(PayFixFlow.build(credit, "pf1", req(amount = 40_000), day, 1200) is PayFixFlow.Built.Ok)
  }

  @Test fun `نیازمندِ تأیید و تکرارِ همان کلید`() {
    assertEquals("APPROVAL_REQUIRED", (PayFixFlow.build(base, "pf1", req(decision = "approve"), day, 1200) as PayFixFlow.Built.Bad).code)
    assertEquals("PERM_DENIED", (PayFixFlow.build(base, "pf1", req(decision = "deny"), day, 1200) as PayFixFlow.Built.Bad).code)
    val d = fixed(base, r = req(decision = "approve", approvedBy = "m2"))
    assertEquals("m2", d.auditLog.last().approvedBy)
    assertTrue(d.auditLog.last().notes.contains("تأیید: m2"))
    //  همان کلید با محتوای دیگر ⇒ ID_CONFLICT؛ اصلاحِ دوم با کلیدِ تازه فقط تا ماندهٔ نقد
    assertEquals("ID_CONFLICT", (PayFixFlow.build(d, "pf1", req(amount = 1_000), day, 1300) as PayFixFlow.Built.Bad).code)
    assertEquals("FIX_EXCEEDS", (PayFixFlow.build(d, "pf2", req(to = "afpay_card", amount = 60_001), day, 1300) as PayFixFlow.Built.Bad).code)
  }

  @Test fun `ردیفِ قدیمیِ سابقه همان JSON را می‌سازد`() {
    val json = Json { encodeDefaults = true }
    val s = json.encodeToString(AuditEntry.serializer(), open)
    assertFalse(s.contains("fromMethod"))
    assertFalse(s.contains("toMethod"))
    assertFalse(s.contains("payRef"))
  }
}
