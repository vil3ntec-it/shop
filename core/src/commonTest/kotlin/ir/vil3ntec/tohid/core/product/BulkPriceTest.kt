package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.BulkPrice.Field
import ir.vil3ntec.tohid.core.product.BulkPrice.Item
import ir.vil3ntec.tohid.core.product.BulkPrice.Op
import ir.vil3ntec.tohid.core.product.BulkPrice.Tone
import ir.vil3ntec.tohid.core.product.BulkPrice.Warn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PRD-21: ویرایشِ گروهیِ قیمت — پیش‌نمایشِ رنگی، هشدار و تأیید. */
class BulkPriceTest {

  private val tea = Item("tea", "چای", saleMinor = 10_000, purchaseMinor = 8_000)      // ۱۰۰ و ۸۰ افغانی
  private val rice = Item("rice", "برنج", saleMinor = 25_050, purchaseMinor = 15_000)  // ۲۵۰٫۵۰ و ۱۵۰
  private val free = Item("free", "هدیه", saleMinor = 0, purchaseMinor = 0)

  @Test fun roundingIsTheSingleProjectRule() {
    assertEquals(3, BulkPrice.mulDivRound(5, 1, 2))      // ۲٫۵ ⇒ ۳ (نیمه به +∞)
    assertEquals(-2, BulkPrice.mulDivRound(-5, 1, 2))    // −۲٫۵ ⇒ −۲
    assertEquals(11_000, BulkPrice.apply(10_000, Op.Percent(1_000)))           // +۱۰٪
    assertEquals(27_600, BulkPrice.apply(25_050, Op.Percent(1_000)))           // ۲۷۵٫۵۵ ⇒ ۲۷۶ افغانی
    assertEquals(27_555, BulkPrice.apply(25_050, Op.Percent(1_000), roundToMinor = 1))
    assertEquals(27_500, BulkPrice.apply(25_050, Op.Percent(1_000), roundToMinor = 500))  // به ۵ افغانی
    assertEquals(9_500, BulkPrice.apply(10_000, Op.Add(-500)))
    assertEquals(12_300, BulkPrice.apply(10_000, Op.SetTo(12_345)))           // ۱۲۳٫۴۵ ⇒ ۱۲۳
  }

  @Test fun colouredPreviewWithoutWarningsNeedsNoConfirmation() {
    val p = BulkPrice.preview(listOf(tea, rice), Field.SALE, Op.Percent(1_000))
    assertEquals(listOf(11_000L, 27_600L), p.rows.map { it.newMinor })
    assertEquals(listOf(Tone.UP, Tone.UP), p.rows.map { it.tone })
    assertEquals(2, p.changed)
    assertFalse(p.needsConfirm)
  }

  @Test fun belowCostAndBigChangesWarnAndNeedConfirmation() {
    val p = BulkPrice.preview(listOf(tea, rice), Field.SALE, Op.Percent(-2_500))   // −۲۵٪
    val teaRow = p.rows.first { it.id == "tea" }
    assertEquals(7_500, teaRow.newMinor)
    assertEquals(listOf(Warn.BELOW_COST), teaRow.warns)          // ۷۵ < بهای ۸۰
    assertEquals(Tone.DANGER, teaRow.tone)
    assertTrue(teaRow.applies)                                     // هشدار است، نه رد
    assertEquals(Tone.DOWN, p.rows.first { it.id == "rice" }.tone)
    assertTrue(p.needsConfirm)
    val big = BulkPrice.preview(listOf(tea), Field.SALE, Op.Percent(5_000))         // +۵۰٪
    assertEquals(listOf(Warn.BIG_CHANGE), big.rows.single().warns)
    assertTrue(big.needsConfirm)
  }

  @Test fun negativeIsBlockedZeroWarns() {
    val p = BulkPrice.preview(listOf(tea, free), Field.SALE, Op.Add(-20_000))
    val t = p.rows.first { it.id == "tea" }
    assertTrue(t.blocked)
    assertFalse(t.applies)
    assertEquals(2, p.blocked)                                     //  «هدیه» ِ صفر هم منفی می‌شد
    assertEquals(0, p.changed)
    val z = BulkPrice.preview(listOf(tea), Field.SALE, Op.SetTo(0)).rows.single()
    assertTrue(Warn.ZERO in z.warns && z.applies)
  }

  @Test fun unchangedRowsAreNeitherCountedNorWarned() {
    val p = BulkPrice.preview(listOf(tea, free), Field.SALE, Op.Percent(1_000))
    val f = p.rows.first { it.id == "free" }
    assertEquals(Tone.SAME, f.tone)
    assertFalse(f.changed)
    assertEquals(emptyList(), f.warns)
    assertEquals(1, p.changed)
  }

  @Test fun purchaseAboveSaleIsTheSameDanger() {
    val p = BulkPrice.preview(listOf(tea), Field.PURCHASE, Op.Add(3_000))          // خرید ۱۱۰ > فروش ۱۰۰
    val r = p.rows.single()
    assertEquals(11_000, r.newMinor)
    assertTrue(Warn.BELOW_COST in r.warns)
    assertEquals(Tone.DANGER, r.tone)
  }

  @Test fun absurdInputIsRejectedBeforeAnyPreview() {
    assertTrue(BulkPrice.valid(Op.Percent(-10_000)))
    assertFalse(BulkPrice.valid(Op.Percent(-10_001)))
    assertFalse(BulkPrice.valid(Op.Percent(1_000_001)))
    assertFalse(BulkPrice.valid(Op.SetTo(-1)))
    assertFalse(BulkPrice.valid(Op.Add(BulkPrice.MAX_MINOR + 1)))
    assertTrue(BulkPrice.valid(Op.SetTo(BulkPrice.MAX_MINOR)))
  }
}
