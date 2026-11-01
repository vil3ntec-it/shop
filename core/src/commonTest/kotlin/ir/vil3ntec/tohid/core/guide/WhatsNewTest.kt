package ir.vil3ntec.tohid.core.guide

import ir.vil3ntec.tohid.core.guide.WhatsNew.Kind
import ir.vil3ntec.tohid.core.guide.WhatsNew.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OPS-05 — «چه چیزی تازه است»: فقط نسخه‌های میانِ دیده‌شده و نصب‌شده؛ برداشته‌شده هرگز بی «به‌جایش» */
class WhatsNewTest {

  private val t = GuideText("متن", "text", "متن")
  private fun note(id: String, v: String, kind: Kind = Kind.NEW, instead: GuideText? = null) = Note(id, v, kind, t, instead)

  private val notes = listOf(
    note("a", "2.14.4"),
    note("b", "2.14.5"),
    note("c", "2.14.5", Kind.CHANGED),
    note("d", "2.14.5", Kind.REMOVED, instead = t),
    note("e", "2.15.0"),
    note("f", "2.16.0"),
  )

  @Test
  fun catalogIsValid() {
    assertEquals(emptyList(), WhatsNew.problems(WhatsNew.ALL))
    assertTrue(WhatsNew.ALL.isNotEmpty())
  }

  @Test
  fun buildNumberAndSuffixAreIgnored() {
    assertEquals(listOf(2, 14, 5), WhatsNew.base("2.14.5.1234-preview"))
    assertEquals(listOf(2, 14, 5), WhatsNew.base(" 2.14.5 "))
    assertEquals("2.14.5", WhatsNew.baseText("2.14.5.77"))
    assertNull(WhatsNew.base("2.14"))
    assertNull(WhatsNew.base("2.x.5"))
    assertNull(WhatsNew.base(""))
    assertNull(WhatsNew.base(null))
  }

  @Test
  fun onlyVersionsBetweenSeenAndInstalledNewestFirstRemovedFirst() {
    val p = WhatsNew.pending(notes, lastSeen = "2.14.4", current = "2.15.0.900")
    assertEquals(listOf("e", "d", "c", "b"), p.map { it.id })
    //  همان نسخهٔ پایه با ساختِ تازه‌تر ⇒ هیچ
    assertEquals(emptyList(), WhatsNew.pending(notes, "2.14.5", "2.14.5.1300"))
  }

  @Test
  fun firstRunAndDowngradeShowNothing() {
    assertEquals(emptyList(), WhatsNew.pending(notes, null, "2.16.0"))
    assertEquals(emptyList(), WhatsNew.pending(notes, "nonsense", "2.16.0"))
    assertEquals(emptyList(), WhatsNew.pending(notes, "2.16.0", "2.14.5"))
    assertEquals(emptyList(), WhatsNew.pending(notes, "2.14.4", "bad"))
  }

  @Test
  fun seenNeverGoesBack() {
    assertEquals("2.14.5", WhatsNew.seenAfter(null, "2.14.5.12"))
    assertEquals("2.15.0", WhatsNew.seenAfter("2.14.5", "2.15.0.1"))
    assertEquals("2.16.0", WhatsNew.seenAfter("2.16.0", "2.14.5"))
    assertEquals("2.14.5", WhatsNew.seenAfter("2.14.5", "bad"))
    assertNull(WhatsNew.seenAfter(null, "bad"))
  }

  @Test
  fun removedWithoutInsteadOrPartialTranslationIsNotShown() {
    val bad = listOf(
      note("x", "2.15.0", Kind.REMOVED),
      Note("y", "2.15.0", Kind.NEW, GuideText("فقط فارسی")),
      note("z", "2.15"),
      note("b", "2.15.0"),
      note("", "2.15.0"),
    )
    val problems = WhatsNew.problems(notes + bad)
    assertEquals(5, problems.size)
    assertTrue(problems.any { it.startsWith("x:") })
    assertTrue(problems.any { it.startsWith("y:") })
    assertTrue(problems.any { it.startsWith("z:") })
    assertTrue(problems.any { it.startsWith("b:") })
    //  هیچ‌کدام از این‌ها به کاربر نمی‌رسد؛ «b» ِ نخست می‌ماند
    val p = WhatsNew.pending(notes + bad, "2.14.4", "2.15.0")
    assertEquals(listOf("e", "d", "c", "b"), p.map { it.id })
  }

  @Test
  fun historyGroupsByVersionUpToInstalled() {
    val h = WhatsNew.history(notes, "2.15.0.3")
    assertEquals(listOf("2.15.0", "2.14.5", "2.14.4"), h.map { it.first })
    assertEquals(listOf("d", "c", "b"), h[1].second.map { it.id })
    assertEquals(emptyList(), WhatsNew.history(notes, "x"))
  }
}
