package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  دکمهٔ «+» ِ کادرِ بالای خانه فقط همان دو عملیاتِ امروز را صدا می‌زند: «ورود/خروجِ نقد» ِ صندوق (`PayFlow.moveRow` +
 *  `DrawerNotes.move`) و «ورود/خروج» ِ گاوصندوق (`SafeBook.deposit`/`withdraw`) — با همان سنجش‌ها، و دو ضربه یک ثبت.
 */
class HeroMoneyTest {
  private val today = "2026-11-03"
  private var n = 0
  private val ids = { "01MONEY${(++n).toString().padStart(19, '0')}" }

  /** صندوقِ باز با ۱۰۰۰ افغانی */
  private fun open(): ShopData = (CashShift.open(ShopData(), 1000.0, "صاحب", today, 100, ids) as CashShift.Result.Ok).data

  @Test
  fun `دلیل‌ها همان دلیل‌های برگهٔ صندوق و پنجرهٔ گاوصندوق‌اند`() {
    assertEquals(ShiftCore.MOVE_REASONS["cash_in"], HeroMoney.reasons(HomeHero.Page.TILL, HeroMoney.Dir.ADD))
    assertEquals(ShiftCore.MOVE_REASONS["cash_out"], HeroMoney.reasons(HomeHero.Page.TILL, HeroMoney.Dir.TAKE))
    assertEquals(SafeCore.REASONS[SafeCore.IN], HeroMoney.reasons(HomeHero.Page.SAFE, HeroMoney.Dir.ADD))
    assertEquals(SafeCore.REASONS[SafeCore.OUT], HeroMoney.reasons(HomeHero.Page.SAFE, HeroMoney.Dir.TAKE))
    assertEquals("cash_in", HeroMoney.tillType(HeroMoney.Dir.ADD))
    assertEquals("cash_out", HeroMoney.tillType(HeroMoney.Dir.TAKE))
  }

  @Test
  fun `مبلغ باید بیش از صفر و درست باشد`() {
    assertEquals(HeroMoney.Amount.Bad(HeroMoney.AMOUNT_REQUIRED), HeroMoney.amount(" "))
    for (bad in listOf("0", "0.00", "abc", "-5", "1.234", "99999999999")) {
      assertEquals(bad, HeroMoney.Amount.Bad(HeroMoney.INVALID_AMOUNT), HeroMoney.amount(bad))
    }
    assertEquals(HeroMoney.Amount.Ok(125_000), HeroMoney.amount("1,250"))
    assertEquals(HeroMoney.Amount.Ok(12_500), HeroMoney.amount("۱۲۵"))
    assertEquals(HeroMoney.Amount.Ok(1_250), HeroMoney.amount("12.5"))
  }

  @Test
  fun `صندوق — همان ردیفِ برگهٔ «ورود و خروجِ نقد»`() {
    val d = open()
    val shift = CashShift.current(d)!!
    val row = (HeroMoney.tillRow(d, HeroMoney.Dir.ADD, 5_000, "deposit", "", "احمد", 200, today, "K1") as PayFlow.Outcome.Ok).value
    //  همان کاری که `DrawerMoveRoute` می‌کند: `moveRow` و سپس تاریخ و یادداشتِ `DrawerNotes.move`
    val same = (PayFlow.moveRow(d, shift.open, PayFlow.Move("cash_in", "50", "deposit", "", by = "احمد"), 200) { "K1" } as PayFlow.Outcome.Ok).value
    assertEquals(same.copy(date = today, notes = DrawerNotes.move("cash_in", same.reason, same.amount, same.note, "احمد")), row)
    assertEquals("cash_in", row.type)
    assertEquals(50.0, row.amount, 0.0)
    assertEquals(shift.open.id, row.refId)
    //  پس از ثبت، نقدِ موردِ انتظار بالا می‌رود (همان شمارشِ صندوق)
    assertEquals(1050.0, CashShift.totalsOf(d.copy(auditLog = d.auditLog + row), shift, 300).expected, 1e-9)
  }

  @Test
  fun `صندوق — سنجش‌های امروز سرِ جایشان‌اند`() {
    fun code(r: PayFlow.Outcome<*>) = (r as PayFlow.Outcome.Bad).problem.code
    //  صندوقِ بسته
    assertEquals(HeroMoney.SHIFT_CLOSED, code(HeroMoney.tillRow(ShopData(), HeroMoney.Dir.ADD, 100, "deposit", "", "", 200, today, "K")))
    val d = open()
    //  برداشت بیش از نقدِ دخل
    assertEquals("INSUFFICIENT_CASH", code(HeroMoney.tillRow(d, HeroMoney.Dir.TAKE, 200_000, "owner_withdrawal", "", "", 200, today, "K")))
    //  «دیگر» بی یادداشت
    assertEquals("NOTE_REQUIRED", code(HeroMoney.tillRow(d, HeroMoney.Dir.ADD, 100, "other", " ", "", 200, today, "K")))
    //  دلیلِ ورود برای خروج
    assertEquals("BAD_REASON", code(HeroMoney.tillRow(d, HeroMoney.Dir.TAKE, 100, "deposit", "", "", 200, today, "K")))
    //  مبلغِ صفر
    assertEquals(HeroMoney.INVALID_AMOUNT, code(HeroMoney.tillRow(d, HeroMoney.Dir.ADD, 0, "deposit", "", "", 200, today, "K")))
    //  برداشتِ درست
    assertTrue(HeroMoney.tillRow(d, HeroMoney.Dir.TAKE, 30_000, "petty_expense", "", "", 200, today, "K") is PayFlow.Outcome.Ok)
  }

  @Test
  fun `صندوق — دو ضربه یک ثبت است`() {
    var d = open()
    val first = HeroMoney.tillWrite(d, HeroMoney.Dir.ADD, 5_000, "deposit", "", "", 200, today, "K1")
    assertEquals(1, first.rows.size)
    assertNull(first.problem)
    d = d.copy(auditLog = d.auditLog + first.rows)
    val again = HeroMoney.tillWrite(d, HeroMoney.Dir.ADD, 5_000, "deposit", "", "", 201, today, "K1")
    assertEquals(emptyList<Any>(), again.rows)
    assertTrue(again.again)
    val bad = HeroMoney.tillWrite(d, HeroMoney.Dir.TAKE, 900_000, "owner_withdrawal", "", "", 202, today, "K2")
    assertEquals("INSUFFICIENT_CASH", bad.problem?.code)
    assertEquals(emptyList<Any>(), bad.rows)
  }

  @Test
  fun `گاوصندوق — همان SafeBook با همان سنجش و همان شناسه یک بار`() {
    var d = (SafeBook.count(ShopData(), 10_000, null, "صاحب", 100, ids) as SafeBook.Result.Ok).data
    val add = HeroMoney.safeEntry(d, HeroMoney.Dir.ADD, 2_000, "deposit", " ", "صاحب", 200, "S1") as SafeBook.Result.Ok
    assertEquals(SafeCore.IN, add.entry.kind)
    assertEquals("deposit", add.entry.reason)
    assertNull(add.entry.note)
    assertEquals("صاحب", add.entry.by)
    d = add.data
    assertEquals(12_000, SafeBook.view(d).balanceMinor)
    //  همان شناسه دوباره ⇒ تکرار، دفتر همان
    val dup = HeroMoney.safeEntry(d, HeroMoney.Dir.ADD, 2_000, "deposit", "", "صاحب", 200, "S1") as SafeBook.Result.Ok
    assertTrue(dup.duplicate)
    assertEquals(d, dup.data)
    //  برداشت بیش از مانده رد
    val over = HeroMoney.safeEntry(d, HeroMoney.Dir.TAKE, 50_000, "owner_withdrawal", "", "صاحب", 300, "S2") as SafeBook.Result.Failed
    assertEquals(SafeCore.Code.INSUFFICIENT_FUNDS, over.code)
    //  «دیگر» بی یادداشت رد
    val other = HeroMoney.safeEntry(d, HeroMoney.Dir.TAKE, 100, SafeCore.OTHER, "", "صاحب", 300, "S3") as SafeBook.Result.Failed
    assertEquals(SafeCore.Code.NOTE_REQUIRED, other.code)
    val out = HeroMoney.safeEntry(d, HeroMoney.Dir.TAKE, 2_500, "petty_expense", "چای", "صاحب", 400, "S4") as SafeBook.Result.Ok
    assertEquals(SafeCore.OUT, out.entry.kind)
    assertEquals("چای", out.entry.note)
    assertEquals(9_500, SafeBook.view(out.data).balanceMinor)
  }
}
