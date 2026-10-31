package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.PosMoreKeys.Act
import ir.vil3ntec.tohid.core.pos.PosMoreKeys.Key
import ir.vil3ntec.tohid.core.pos.PosMoreKeys.Mods
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** POS-41 — کلیدهای ماندهٔ صندوق؛ نام‌ها camelCase (کامپایلرِ iOS) */
class PosMoreKeysTest {

  private val shift = Mods(shift = true)

  @Test
  fun offChangesNothing() {
    for (k in Key.entries) for (m in listOf(Mods(), shift, Mods(primary = true), Mods(alt = true))) {
      assertNull(PosMoreKeys.screen(false, k, m))
      for (q in listOf(true, false)) for (c in listOf(true, false)) assertNull(PosMoreKeys.field(false, k, m, q, c))
    }
    assertFalse(PosMoreKeys.hints(false))
  }

  @Test
  fun shiftF5OpensInvoiceDiscountOnlyWithShiftAlone() {
    assertEquals(Act.DISCOUNT_INVOICE, PosMoreKeys.screen(true, Key.F5, shift))
    assertNull(PosMoreKeys.screen(true, Key.F5, Mods()), "F5 تنها مالِ «تخفیفِ ردیف» است (هنوز برنامه‌ریزی‌شده)")
    assertNull(PosMoreKeys.screen(true, Key.F5, Mods(shift = true, primary = true)))
    assertNull(PosMoreKeys.screen(true, Key.F5, Mods(shift = true, alt = true)))
    assertNull(PosMoreKeys.screen(true, Key.ESCAPE, shift))
    assertNull(PosMoreKeys.screen(true, Key.OTHER, shift))
  }

  @Test
  fun escapeAsksToEmptyOnlyFromAnEmptyFieldWithItems() {
    assertEquals(Act.CLEAR_CART, PosMoreKeys.field(true, Key.ESCAPE, Mods(), queryEmpty = true, cartEmpty = false))
    assertNull(PosMoreKeys.field(true, Key.ESCAPE, Mods(), queryEmpty = false, cartEmpty = false), "کادرِ نوشته‌دار: همان پاک کردنِ کادر")
    assertNull(PosMoreKeys.field(true, Key.ESCAPE, Mods(), queryEmpty = true, cartEmpty = true), "سبدِ خالی: چیزی برای پرسیدن نیست")
    assertNull(PosMoreKeys.field(true, Key.ESCAPE, shift, true, false))
    assertNull(PosMoreKeys.field(true, Key.ESCAPE, Mods(primary = true), true, false))
    assertNull(PosMoreKeys.field(true, Key.F5, Mods(), true, false))
    assertTrue(PosMoreKeys.hints(true))
  }
}
