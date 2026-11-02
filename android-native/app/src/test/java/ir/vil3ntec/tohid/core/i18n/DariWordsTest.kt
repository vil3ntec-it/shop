package ir.vil3ntec.tohid.core.i18n

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  LOC-08 — واژه‌های دری در نوشته‌های فارسیِ برنامه: «قرض‌دار»، «مصارف»، «شاگرد»، «دکان».
 *
 *  نوشته‌ها در جدول‌های `Str*` اند و برنامهٔ اندروید و کامپیوتر هر دو همین‌ها را نشان می‌دهند.
 *  این آزمون نامِ نقش، زبانهٔ مصارف، قرض‌دار و صاحبِ دکان را به همان واژهٔ دری قفل می‌کند و
 *  هم‌معنای ایرانی («بدهکار»، «هزینه»، «کارمند»، «فروشگاه») را در همین نوشته‌ها نمی‌پذیرد.
 *  «قرض‌دار» با نیم‌فاصله (U+200C) است، نه فاصله و نه چسبیده.
 */
class DariWordsTest {

  @Before fun dari() { I18n.source = { Lang.FA } }

  @After fun reset() { I18n.source = { Lang.FA } }

  private fun noIranian(text: String) {
    for (w in listOf("بدهکار", "هزینه", "کارمند", "فروشگاه")) {
      assertFalse("«$w» در «$text»", text.contains(w))
    }
  }

  @Test fun `نقشِ کارکنِ دکان «شاگرد» است`() {
    assertEquals("شاگرد", StrNav.roleStaff)
    noIranian(StrNav.roleStaff)
  }

  @Test fun `صاحب و مدیریت با «دکان»`() {
    assertEquals("صاحب دکان", StrNav.roleOwner)
    assertTrue(StrNav.shopManagement.contains("دکان"))
    assertTrue(StrNav.expensesSub.contains("دکان"))
    for (s in listOf(StrNav.roleOwner, StrNav.shopManagement, StrNav.expensesSub)) noIranian(s)
  }

  @Test fun `زبانه و فهرستِ «مصارف»`() {
    assertEquals("مصارف", StrMoney.tabExpenses)
    assertEquals("همهٔ مصارف", StrMoney.allExpenses)
    assertTrue(StrNav.reportsSub.contains("مصارف"))
    for (s in listOf(StrMoney.tabExpenses, StrMoney.allExpenses, StrNav.reportsSub)) noIranian(s)
  }

  @Test fun `«قرض‌دار» با نیم‌فاصله`() {
    val zwnj = "قرض‌دار"
    for (s in listOf(StrMoney.newDebtor, StrMoney.editDebtor, StrMoney.deletedDebtor, StrNav.receiptsSub)) {
      assertTrue("«قرض‌دار» در «$s»", s.contains(zwnj))
      assertFalse("فاصله به‌جای نیم‌فاصله در «$s»", s.contains("قرض دار"))
      noIranian(s)
    }
  }

  @Test fun `انگلیسی و پشتو همان واژه‌های دری را نمی‌گیرند`() {
    I18n.source = { Lang.EN }
    assertEquals("Staff member", StrNav.roleStaff)
    assertEquals("Expenses", StrMoney.tabExpenses)
    I18n.source = { Lang.PS }
    assertEquals("شاګرد", StrNav.roleStaff)
    assertEquals("لګښتونه", StrMoney.tabExpenses)
  }
}
