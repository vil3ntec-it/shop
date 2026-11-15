package ir.vil3ntec.tohid.ui.v3

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  ۶۵s (هماهنگ‌کننده: «عنوانِ وسطِ نوار هرگز پوشیده نشود»): `V3HeaderActions.visibleFor` — دست‌بالا دو کار پیدا، و آن‌قدر کم
 *  که عنوان وسط جا شود؛ «…» خودش یک آیکن است؛ هیچ کاری گم نمی‌شود (بقیه در «…»). جا در `SectionHeaderUiTest` (مرزِ گره).
 */
class HeaderFitTest {

  @Test fun `جا که هست همان دو کار و «…»`() {
    assertEquals(2, V3HeaderActions.visibleFor(3, hasOverflow = false) { true })
    assertEquals(2, V3HeaderActions.visibleFor(2, hasOverflow = false) { true })
    assertEquals(1, V3HeaderActions.visibleFor(1, hasOverflow = true) { true })
    assertEquals(0, V3HeaderActions.visibleFor(0, hasOverflow = false) { true })
  }

  @Test fun `جا که نیست کار به «…» می‌رود، نه زیرِ عنوان`() {
    //  سه آیکن جا نمی‌شود، دو جا می‌شود ⇒ یک کار و «…»
    assertEquals(1, V3HeaderActions.visibleFor(3, hasOverflow = false) { icons -> icons <= 2 })
    //  دو کارِ بی «…» جا نمی‌شود ⇒ یک کار و «…» (دو آیکن) هم نه ⇒ فقط «…»
    assertEquals(0, V3HeaderActions.visibleFor(2, hasOverflow = false) { icons -> icons <= 1 })
    //  بی هیچ جا: فقط «…» (صفر کارِ پیدا) — عنوان در جای خالی کوتاه می‌شود
    assertEquals(0, V3HeaderActions.visibleFor(2, hasOverflow = true) { false })
  }

  @Test fun `کارهای رفته اولِ «…» می‌آیند و نقطه‌شان روی «…»`() {
    val a = V3Action(androidx.compose.material.icons.Icons.Default.Add, "الف") {}
    val b = V3Action(androidx.compose.material.icons.Icons.Default.Add, "ب", dot = true) {}
    val split = V3HeaderActions.split(listOf(a, b), listOf(V3MenuItem(androidx.compose.material.icons.Icons.Default.Add, "منو") {}), visible = 1)
    assertEquals(listOf("الف"), split.shown.map { it.label })
    assertEquals(listOf("ب", "منو"), split.menu.map { it.label })
    assertEquals(true, split.menuDot)
  }
}
