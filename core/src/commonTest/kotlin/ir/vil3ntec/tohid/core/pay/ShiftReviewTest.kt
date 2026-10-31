package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.pay.ShiftReview.Result
import ir.vil3ntec.tohid.core.pay.ShiftReview.Review
import ir.vil3ntec.tohid.core.pay.ShiftReview.Role
import ir.vil3ntec.tohid.core.pay.ShiftReview.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** PAY-14 — ثبتِ «بررسی شد» ِ مدیر برای بستنِ صندوقِ «نیازمند بررسی»؛ هیچ عددی عوض نمی‌شود */
class ShiftReviewTest {
  private fun err(r: Result) = (r as Result.Err).code
  private fun ok(r: Result) = (r as Result.Ok).review
  private fun review(
    needs: Boolean? = true, closedBy: String? = "سارا", role: Role = Role.MANAGER, by: String = "علی",
    note: String = "پول در کشوی دوم پیدا شد", existing: List<Review> = emptyList(),
  ) = ShiftReview.review("r1", "c1", needs, closedBy, role, by, note, 1_000L, existing)

  @Test
  fun managerRecordsTheReviewWithTrimmedNote() {
    assertEquals(Review("r1", "c1", 1_000L, "علی", "شمارشِ دوباره: ۵۰۰ کم"), ok(review(by = " علی ", note = "  شمارشِ دوباره: ۵۰۰ کم ")))
    assertEquals("صاحب", ok(review(role = Role.OWNER, by = "صاحب")).by)
  }

  @Test
  fun onlyClosesThatNeedReviewAndOnlyOwnerOrManager() {
    assertEquals("NOT_NEEDED", err(review(needs = false)))
    assertEquals("NOT_NEEDED", err(review(needs = null)))
    assertEquals("ROLE_DENIED", err(review(role = Role.STAFF)))
    assertEquals("BY_REQUIRED", err(review(by = "  ")))
  }

  @Test
  fun managerDoesNotReviewOwnCloseButOwnerMay() {
    assertEquals("SELF_REVIEW", err(review(closedBy = "علی", by = "علی")))
    assertEquals("SELF_REVIEW", err(review(closedBy = " علی", by = "علی ")))
    assertEquals("علی", ok(review(closedBy = "علی", by = "علی", role = Role.OWNER)).by)
    assertEquals("علی", ok(review(closedBy = null)).by)
  }

  @Test
  fun noteIsRequiredAndBounded() {
    assertEquals("NOTE_REQUIRED", err(review(note = "  ")))
    assertEquals("NOTE_REQUIRED", err(review(note = "اه")))
    assertEquals("ابک", ok(review(note = "ابک")).note)
    assertEquals(300, ok(review(note = "ا".repeat(300))).note.length)
    assertEquals("NOTE_TOO_LONG", err(review(note = "ا".repeat(301))))
  }

  @Test
  fun onceOnlyAndTheSameWinnerWhateverTheArrivalOrder() {
    val a = Review("b", "c1", 50L, "علی", "یک")
    val b = Review("a", "c1", 50L, "مریم", "دو")
    val c = Review("c", "c1", 40L, "رضا", "سه")
    val other = Review("z", "c2", 1L, "علی", "دیگر")
    assertEquals("ALREADY_REVIEWED", err(review(existing = listOf(a))))
    assertEquals("علی", ok(review(existing = listOf(other))).by)
    //  هم‌زمان (۵۰): شناسهٔ کوچک‌تر؛ زودتر (۴۰) بر هر دو
    assertEquals(b, ShiftReview.reviewOf("c1", listOf(a, b)))
    assertEquals(b, ShiftReview.reviewOf("c1", listOf(b, a)))
    assertEquals(c, ShiftReview.reviewOf("c1", listOf(a, b, c, other)))
    assertNull(ShiftReview.reviewOf("c3", listOf(a, other)))
  }

  @Test
  fun stateForTheHistoryList() {
    val r = Review("r", "c1", 1L, "علی", "بررسی شد")
    assertEquals(State.NONE, ShiftReview.state(false, "c1", listOf(r)))
    assertEquals(State.NONE, ShiftReview.state(null, "c1", emptyList()))
    assertEquals(State.PENDING, ShiftReview.state(true, "c1", emptyList()))
    assertEquals(State.PENDING, ShiftReview.state(true, "c2", listOf(r)))
    assertEquals(State.REVIEWED, ShiftReview.state(true, "c1", listOf(r)))
  }
}
