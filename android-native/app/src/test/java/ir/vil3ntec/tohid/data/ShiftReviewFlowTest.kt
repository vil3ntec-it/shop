package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.ShiftReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  PAY-14 — «بررسی شد» ِ مدیر روی بستنِ واقعیِ صندوقِ شیفت (`PayFlow.closeRow` با کسریِ بالای آستانه، همان S12b):
 *  ردیفِ `shift_review` با `refId` ِ همان بستن؛ بستن و Zِ آن دست نمی‌خورند؛ یک بار؛ شاگرد و خودبررسیِ مدیر نه.
 */
class ShiftReviewFlowTest {
  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }

  private fun <T> ok(o: PayFlow.Outcome<T>): T = when (o) {
    is PayFlow.Outcome.Ok -> o.value
    is PayFlow.Outcome.Bad -> throw AssertionError("انتظارِ موفقیت: ${o.problem}")
  }
  private fun <T> code(o: PayFlow.Outcome<T>): String = when (o) {
    is PayFlow.Outcome.Bad -> o.problem.code
    is PayFlow.Outcome.Ok -> throw AssertionError("انتظارِ خطا: ${o.value}")
  }

  /** S12b: کسریِ ۱۰۰ افغانی، بستنِ کورِ «سارا» ⇒ needsReview */
  private fun closed(blind: Boolean = true, by: String = "سارا"): Pair<ShopData, AuditEntry> {
    val open = AuditEntry(id = "sh12", type = CashShift.OPEN, createdAt = 1000, amount = 2000.0)
    var d = ShopData(sales = listOf(Sale(id = "a", finalTotal = 15300.0, createdAt = 1100)), auditLog = listOf(open))
    d = d.copy(auditLog = d.auditLog + ok(PayFlow.moveRow(d, open, PayFlow.Move("cash_out", "500", "petty_expense", "خرید شیر", by = by), 1200, newId)))
    val counts = mapOf("100000" to 10L, "50000" to 12L, "10000" to 7L)
    val c = ok(PayFlow.closeRow(d, open, PayFlow.Count(counts, by = by, blind = blind, reason = if (blind) "" else "شمارش"), 1300, newId))
    return d.copy(auditLog = d.auditLog + c.row) to c.row
  }

  @Test fun `مدیر بررسی را ثبت می‌کند و بستن و Z دست نمی‌خورند`() {
    val (d, close) = closed()
    assertEquals(true, ShiftReviewFlow.needsReview(close))
    assertEquals(ShiftReview.State.PENDING, ShiftReviewFlow.state(d, close))
    val row = ok(ShiftReviewFlow.row(d, close, ShiftReview.Role.MANAGER, "علی", "  ۱۰۰ در کشوی دوم پیدا شد ", "2026-10-31", 2000, newId))
    assertEquals(listOf(ShiftReview.TYPE, close.id, "علی", "۱۰۰ در کشوی دوم پیدا شد", 2000L), listOf(row.type, row.refId, row.by, row.note, row.createdAt))
    val after = d.copy(auditLog = d.auditLog + row)
    assertEquals(ShiftReview.State.REVIEWED, ShiftReviewFlow.state(after, close))
    assertEquals("علی", ShiftReviewFlow.reviewOf(after, close)?.by)
    //  ⛔ اختلاف هرگز اصلاح نمی‌شود: ردیفِ بستن همان است و Zِ آن همان اختلاف را دارد
    assertEquals(close, after.auditLog.single { it.id == close.id })
    assertEquals(-10000L, PayFlow.reportOf(after.auditLog.single { it.id == close.id }.z!!)!!.variance!!["cash"])
    assertEquals("ALREADY_REVIEWED", code(ShiftReviewFlow.row(after, close, ShiftReview.Role.OWNER, "صاحب", "دوباره", "2026-10-31", 2100, newId)))
  }

  @Test fun `شاگرد، خودبررسیِ مدیر، یادداشتِ خالی و بستنِ بی نیاز رد می‌شوند`() {
    val (d, close) = closed()
    assertEquals("ROLE_DENIED", code(ShiftReviewFlow.row(d, close, ShiftReview.Role.STAFF, "رضا", "بررسی", "2026-10-31", 2000, newId)))
    assertEquals("SELF_REVIEW", code(ShiftReviewFlow.row(d, close, ShiftReview.Role.MANAGER, "سارا", "بررسی", "2026-10-31", 2000, newId)))
    assertEquals("سارا", ok(ShiftReviewFlow.row(d, close, ShiftReview.Role.OWNER, "سارا", "بررسی", "2026-10-31", 2000, newId)).by)
    assertEquals("NOTE_REQUIRED", code(ShiftReviewFlow.row(d, close, ShiftReview.Role.MANAGER, "علی", " ", "2026-10-31", 2000, newId)))
    val open = d.auditLog.first { it.type == CashShift.OPEN }
    assertEquals("NOT_NEEDED", code(ShiftReviewFlow.row(d, open, ShiftReview.Role.OWNER, "علی", "بررسی", "2026-10-31", 2000, newId)))
    //  بستنِ قدیمیِ بی Z بررسی نمی‌خواهد
    val legacy = close.copy(id = "old", z = null)
    assertNull(ShiftReviewFlow.needsReview(legacy))
    assertEquals(ShiftReview.State.NONE, ShiftReviewFlow.state(d, legacy))
  }

  @Test fun `نقشِ ذخیره‌شده ⇒ نقشِ هسته؛ ناشناخته شاگرد است`() {
    assertEquals(ShiftReview.Role.OWNER, ShiftReviewFlow.roleOf(ShopRole.OWNER))
    assertEquals(ShiftReview.Role.MANAGER, ShiftReviewFlow.roleOf(ShopRole.MANAGER))
    assertEquals(ShiftReview.Role.STAFF, ShiftReviewFlow.roleOf("staff"))
    assertEquals(ShiftReview.Role.STAFF, ShiftReviewFlow.roleOf("courier"))
  }
}
