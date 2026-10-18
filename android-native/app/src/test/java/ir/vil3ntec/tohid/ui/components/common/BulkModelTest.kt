package ir.vil3ntec.tohid.ui.components.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** انتخاب و نتیجهٔ کارِ گروهی — v3 §۱۹.۵ بندهای ۴ و ۱۰ */
class BulkModelTest {

  @Test
  fun `انتخابِ تکی`() {
    assertEquals(setOf("a"), Selection.toggle(emptySet(), "a"))
    assertEquals(emptySet<String>(), Selection.toggle(setOf("a"), "a"))
  }

  @Test
  fun `چک‌باکسِ سرستون فقط ردیف‌های دیده‌شده را عوض می‌کند`() {
    val hidden = setOf("z")                       //  انتخاب‌شده ولی بیرونِ فیلتر
    val visible = listOf("a", "b", "c")
    val all = Selection.toggleAll(hidden, visible)
    assertEquals(setOf("z", "a", "b", "c"), all)
    assertTrue(Selection.allSelected(all, visible))
    //  دوباره ⇒ دیده‌شده‌ها بیرون می‌روند، «z» می‌ماند
    assertEquals(setOf("z"), Selection.toggleAll(all, visible))
    //  نیمه‌انتخاب ⇒ همه انتخاب می‌شوند
    assertEquals(setOf("a", "b", "c"), Selection.toggleAll(setOf("a"), visible))
    assertTrue(Selection.someSelected(setOf("a"), visible))
    assertFalse(Selection.allSelected(emptySet(), emptyList()))
    assertEquals(1, Selection.hiddenCount(all, visible))
  }

  @Test
  fun `شناسه‌های رفته از دفتر کنار می‌روند`() {
    val sel = setOf("a", "b", "gone")
    assertEquals(setOf("a", "b"), Selection.prune(sel, setOf("a", "b", "c")))
    //  چیزی نرفته ⇒ همان شیء
    val same = setOf("a")
    assertTrue(Selection.prune(same, setOf("a", "b")) === same)
  }

  @Test
  fun `دلیل و خلاصهٔ نتیجه`() {
    assertFalse(BulkModel.reasonValid("  "))
    assertFalse(BulkModel.reasonValid("ab"))
    assertTrue(BulkModel.reasonValid("خراب"))
    val results = listOf(
      BulkRowResult("a", "الف", ok = true, note = "حذف شد"),
      BulkRowResult("b", "ب", ok = false, note = "پیدا نشد"),
      BulkRowResult("c", "ج", ok = true, note = "حذف شد"),
    )
    assertEquals(2 to 1, BulkModel.summary(results))
    assertEquals("b", BulkModel.ordered(results).first().id)
  }
}
