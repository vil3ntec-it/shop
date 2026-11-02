package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.guide.GuideText
import ir.vil3ntec.tohid.core.text.DariText
import ir.vil3ntec.tohid.core.text.LocaleFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Modifier

/**
 *  LOC-08 — واژه‌های دری در نوشته‌های فارسیِ برنامه: «قرض‌دار»، «مصارف»، «شاگرد»، «دکان».
 *
 *  نوشته‌ها در جدول‌های `Str*` اند و برنامهٔ اندروید و کامپیوتر هر دو همین‌ها را نشان می‌دهند.
 *  این آزمون نامِ نقش، زبانهٔ مصارف، قرض‌دار و صاحبِ دکان را به همان واژهٔ دری قفل می‌کند و
 *  هم‌معنای ایرانی («بدهکار»، «هزینه»، «کارمند»، «فروشگاه») را در همین نوشته‌ها نمی‌پذیرد.
 *  «قرض‌دار» با نیم‌فاصله (U+200C) است، نه فاصله و نه چسبیده.
 *
 *  از ۲.۱۴.۵ «دری» زبانِ جدای انتخاب‌گر هم هست (`Lang.PRS`): همان فارسی که از لایهٔ واژهٔ
 *  `core/text/DariText` می‌گذرد. آزمون‌های پایینِ این فایل همان لایه را روی **همهٔ** جدول‌ها می‌سنجند:
 *  فارسی بایت‌به‌بایت همان می‌ماند، دری هیچ واژهٔ فهرست را جا نمی‌گذارد (جز استثنای نام‌دار)، و
 *  کلیدِ هر عبارتِ کامل هنوز متنِ فارسیِ یکی از جدول‌هاست.
 */
class DariWordsTest {

  @Before fun dari() { I18n.source = { Lang.FA } }

  @After fun reset() {
    I18n.source = { Lang.FA }
    I18n.audit = null
  }

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

  /* ------------------------- دری: لایهٔ واژه روی فارسی ------------------------- */

  /** یک نوشتهٔ جدول: متنِ فارسیِ ردیف و خروجیِ فارسی و دری */
  private class Row(val key: String, val fa: String, val outFa: String, val faOfDari: String, val outDari: String)

  /** همهٔ ویژگی‌ها و تابع‌های `I18nTables` (همان روشِ `I18nCoverageTest`) */
  private fun rows(): List<Row> {
    val out = ArrayList<Row>()
    for (table in I18nTables.all) for (m in table.javaClass.declaredMethods) {
      if (!Modifier.isPublic(m.modifiers) || Modifier.isStatic(m.modifiers) || m.isSynthetic) continue
      if (m.returnType != String::class.java) continue
      val args = m.parameterTypes.mapIndexed { i, type ->
        when (type) {
          Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7301 + i
          Long::class.javaPrimitiveType, Long::class.javaObjectType -> 7301L + i
          Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> true
          else -> (7301 + i).toString()
        }
      }.toTypedArray()
      var entry: I18n.Entry? = null
      I18n.audit = { entry = it }
      I18n.source = { Lang.FA }
      val f = m.invoke(table, *args) as String
      val fa = entry?.fa ?: continue
      I18n.source = { Lang.PRS }
      val d = m.invoke(table, *args) as String
      out += Row(table.javaClass.simpleName + "." + m.name, fa, f, entry!!.fa, d)
    }
    I18n.audit = null
    return out
  }

  /** واژهٔ فهرست که در دری می‌ماند و چرا — هر تازه‌ای یعنی تصمیمِ صاحب‌کار */
  private val dariAllowed = listOf(
    "انبارگردانی", // هنوز همتای دریِ قطعی ندارد — در PR «برای تصمیمِ صاحب‌کار»
    "فروشگاه من", // نامِ برنامه
  )

  @Test fun `دری زبانِ جدای انتخاب‌گر است با خط و جهتِ فارسی`() {
    assertEquals(listOf("فارسی", "دری", "English", "پښتو"), Lang.entries.map { it.native })
    assertEquals(Lang.PRS, Lang.of("prs"))
    assertTrue(Lang.PRS.rtl)
    assertTrue(Lang.PRS.persian && Lang.FA.persian)
    assertFalse(Lang.EN.persian || Lang.PS.persian)
  }

  @Test fun `فارسی بایت‌به‌بایت همان است و دری همان فارسی از لایهٔ واژه`() {
    val all = rows()
    assertTrue("جدولی پیدا نشد", all.size > 500)
    var changed = 0
    for (r in all) {
      assertEquals(r.key, r.fa, r.outFa)
      assertEquals(r.key, DariText.of(r.faOfDari), r.outDari)
      assertTrue("${r.key} در دری خالی است", r.outDari.isNotBlank())
      if (r.outDari != r.outFa) changed++
    }
    println("dari: $changed of ${all.size} keys differ from fa")
    assertTrue("لایهٔ دری تقریباً هیچ نوشته‌ای را عوض نکرد ($changed)", changed > 100)
  }

  @Test fun `واژهٔ ایرانیِ فهرست در دری نمی‌ماند مگر استثنای نام‌دار`() {
    val left = ArrayList<String>()
    for (r in rows()) {
      var text = r.outDari
      for (a in dariAllowed) text = text.replace(a, "")
      for (w in DariText.WORDS) {
        val hit = Regex("(?<![\\p{L}\\p{M}\\u200C])" + Regex.escape(w.fa)).find(text) ?: continue
        left += "${r.key}: «${w.fa}» در «${text.substring(maxOf(0, hit.range.first - 20), minOf(text.length, hit.range.last + 20))}»"
      }
    }
    assertTrue("واژهٔ ایرانی در دری ماند:\n" + left.joinToString("\n"), left.isEmpty())
  }

  @Test fun `کلیدِ هر عبارتِ کاملِ دری متنِ فارسیِ یکی از جدول‌هاست`() {
    val fa = rows().map { it.fa }.toSet()
    for (k in DariText.PHRASES.keys) assertTrue("عبارتِ «$k» دیگر در جدول‌ها نیست", k in fa)
  }

  @Test fun `واژه‌های LOC-08 در دری هم همان‌اند`() {
    I18n.source = { Lang.PRS }
    assertEquals("شاگرد", StrNav.roleStaff)
    assertEquals("صاحب دکان", StrNav.roleOwner)
    assertEquals("مصارف", StrMoney.tabExpenses)
    assertEquals("همهٔ مصارف", StrMoney.allExpenses)
    for (s in listOf(StrMoney.newDebtor, StrMoney.editDebtor, StrNav.receiptsSub)) {
      assertTrue("«قرض‌دار» در «$s»", s.contains("قرض‌دار"))
      noIranian(s)
    }
  }

  @Test fun `پول، رقم، ماه، رنگِ پیام و راهنما در دری همان فارسی است`() {
    val fa = LocaleFormat.DIGITS_FA
    assertEquals(I18n.currencyWord(Lang.FA), I18n.currencyWord(Lang.PRS))
    assertEquals(I18n.money(1250.0, Lang.FA, fa), I18n.money(1250.0, Lang.PRS, fa))
    assertEquals(fa, I18n.screenDigits(Lang.PRS, fa))
    assertEquals(I18n.solarMonths(Lang.FA, "fa"), I18n.solarMonths(Lang.PRS, "fa"))
    assertEquals(I18n.gregorianMonths(Lang.FA), I18n.gregorianMonths(Lang.PRS))
    assertFalse(I18n.gregorian(Lang.PRS, I18n.CAL_AUTO))
    assertEquals(I18n.Tone.ERROR, I18n.toneOf("ذخیره نشد", Lang.PRS))
    assertEquals("مبایل را وصل کنید", GuideText("گوشی را وصل کنید").of("prs"))
    assertEquals("گوشی را وصل کنید", GuideText("گوشی را وصل کنید").of("fa"))
  }
}
