package ir.vil3ntec.tohid.core.ledger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** POS-42 — فقط افزودن به ته با همان شیءها «افزایشی» است؛ هر تغییرِ دیگر ⇒ `null` (ساختنِ کامل) */
class AppendTailTest {

  private class Row(val id: String) {
    override fun equals(other: Any?) = other is Row && other.id == id
    override fun hashCode() = id.hashCode()
  }

  private val a = Row("a")
  private val b = Row("b")
  private val c = Row("c")

  @Test fun sameListIsAnEmptyTail() {
    val l = listOf(a, b)
    assertEquals(emptyList(), AppendTail.of(l, l))
    //  فهرستِ دیگر با همان شیءها (رونوشت) ⇒ باز هم تهی
    assertEquals(emptyList(), AppendTail.of(l, l.toList()))
  }

  @Test fun appendedRowsAreTheTail() {
    val t = AppendTail.of(listOf(a), listOf(a, b, c))!!
    assertEquals(2, t.size)
    assertSame(b, t[0])
    assertSame(c, t[1])
    assertEquals(listOf(a, b), AppendTail.of(emptyList(), listOf(a, b)))
  }

  @Test fun anythingButAppendIsNull() {
    //  کوتاه‌تر (حذف)
    assertNull(AppendTail.of(listOf(a, b), listOf(a)))
    //  جابه‌جایی
    assertNull(AppendTail.of(listOf(a, b), listOf(b, a, c)))
    //  افزودن به سر
    assertNull(AppendTail.of(listOf(b), listOf(a, b)))
    //  ردیفِ پیشینِ عوض‌شده با محتوای برابر هم شیءِ تازه است ⇒ از نو (لغو/برگشت/ادغام هرگز «افزوده» دیده نمی‌شود)
    assertNull(AppendTail.of(listOf(a, b), listOf(a, Row("b"), c)))
  }
}
