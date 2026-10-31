package ir.vil3ntec.tohid.core.audit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RPT-09 — کدام ردیفِ فاکتورِ باز حذف شد و به نامِ چه کسی */
class LineVoidTest {

  @Test
  fun deletedRowIsTheOneMissingFromTheNewCart() {
    assertEquals(listOf("p2"), LineVoid.removed(listOf("p1", "p2", "p3"), listOf("p1", "p3")))
  }

  /** «خالی کردنِ فاکتور» = همهٔ ردیف‌ها، به ترتیبِ سبد */
  @Test
  fun clearingRemovesEveryRowInOrder() {
    assertEquals(listOf("p3", "p1"), LineVoid.removed(listOf("p3", "p1"), emptyList()))
  }

  /** افزودن، کم و زیاد کردنِ مقدار و جابه‌جاییِ ترتیب حذف نیست */
  @Test
  fun addingOrReorderingIsNotADelete() {
    assertEquals(emptyList(), LineVoid.removed(emptyList(), listOf("p1")))
    assertEquals(emptyList(), LineVoid.removed(listOf("p1"), listOf("p1", "p2")))
    assertEquals(emptyList(), LineVoid.removed(listOf("p1", "p2"), listOf("p2", "p1")))
    assertEquals(emptyList(), LineVoid.removed(listOf("p1"), listOf("p1")))
  }

  /** کلیدِ تکراری (سبدِ خراب) دو بار شمرده نمی‌شود */
  @Test
  fun duplicateKeyCountsOnce() {
    assertEquals(listOf("p1"), LineVoid.removed(listOf("p1", "p1", "p2"), listOf("p2")))
  }

  @Test
  fun userIsOnlyAnExplicitName() {
    assertEquals("احمد", LineVoid.who("  احمد "))
    assertNull(LineVoid.who(""))
    assertNull(LineVoid.who("   "))
    assertNull(LineVoid.who(null))
  }

  @Test
  fun typeIsTheReportCoreName() {
    assertEquals("line_delete", LineVoid.TYPE)
  }
}
