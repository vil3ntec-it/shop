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

  private fun methods(table: Any) = table.javaClass.declaredMethods.filter {
    Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.returnType == String::class.java
  }

  /** آرگومانِ نگهبان (همان روشِ `I18nCoverageTest`)؛ [text] برای جای خالیِ متنی */
  private fun argsFor(m: java.lang.reflect.Method, text: (Int) -> String = { (7301 + it).toString() }): Array<Any> =
    m.parameterTypes.mapIndexed { i, type ->
      when (type) {
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> 7301 + i
        Long::class.javaPrimitiveType, Long::class.javaObjectType -> 7301L + i
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> true
        else -> text(i)
      }
    }.toTypedArray()

  /** همهٔ ویژگی‌ها و تابع‌های `I18nTables` */
  private fun rows(): List<Row> {
    val out = ArrayList<Row>()
    for (table in I18nTables.all) for (m in methods(table)) {
      val args = argsFor(m)
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
    //  ۱۴۰۵/۰۸/۱۳: واژهٔ دیگری که فقط با واژهٔ فهرست آغاز می‌شود و درست است فارسی بماند
    "شده", // «ثبت شده»، «حذف شده»… صفتِ مفعولی؛ فقط «… شد» ⇐ «… گردید»
    "بدهید", "بدهیم", // فعلِ «دادن»، نه «بدهی»
    "تازه‌تر", "تازه‌سازی", "تازه‌اند", // برتر و «به‌روز کردن»، نه «جدید»
  ) + DariText.KEEP // «تازه کردن»، «تازه می‌شود»… = به‌روز کردن (فهرستِ نام‌دارِ خودِ لایه)

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
    val all = rows()
    //  کلیدِ با `{}` همان متنِ دری‌شدهٔ ردیف است که هر مقدارِ asIs در آن `{}` شده (`DariText.shape`)
    val fa = all.map { it.fa }.toSet() + all.map { DariText.shape(it.faOfDari) }.toSet()
    for (k in DariText.PHRASES.keys) assertTrue("عبارتِ «$k» دیگر در جدول‌ها نیست", k in fa)
    //  و هر جملهٔ کامل واقعاً در خروجیِ دری می‌نشیند (کلیدِ مرده یا جای خالیِ بی asIs نه)
    val used = all.filter { DariText.PHRASES.containsKey(it.fa) || DariText.PHRASES.containsKey(DariText.shape(it.faOfDari)) }
    for (r in used) assertTrue("${r.key}: جملهٔ کامل ننشست: «${r.outDari}»", r.outDari != r.outFa)
  }

  @Test fun `جملهٔ کاملِ دری عبارتِ جدولِ کامپیوتر را ندارد (کلید روی کامپیوتر هم همان است)`() {
    val desk = DesktopPhrases.load().map { it.first }
    for (k in DariText.PHRASES.keys) for (p in desk) assertFalse("«$k» عبارتِ کامپیوتریِ «$p» را دارد", k.contains(p))
    //  و هیچ متنِ فارسیِ جدول `{}` ِ خام ندارد (وگرنه با جای خالیِ PHRASES یکی گرفته می‌شد)
    for (r in rows()) assertFalse("${r.key}: «{}» در متنِ فارسی", r.fa.contains("{}"))
  }

  /* ------------- فارسی بایت‌به‌بایت همان (نگهبان) و نوشته‌هایی که از دور‌زدنِ لایه بیرون آمدند ------------- */

  @Test fun `فارسیِ نوشته‌های جابه‌جاشده همان متنِ پیشینِ صفحه است و دری عوض می‌شود`() {
    I18n.source = { Lang.FA }
    //  پیک (`ui/courier`) و تنظیماتِ نسخهٔ ۳: همان جمله‌های خامِ پیشین، کلمه‌به‌کلمه
    assertEquals("سرم شلوغ است", StrCourierApp.rsBusy)
    assertEquals("راه افتادم", StrCourierApp.acMoving)
    assertEquals("اینترنت نیست — کارها در صفِ گوشی می‌مانند", StrCourierApp.offlineQueued)
    assertEquals("1,200 افغانی", StrCourierApp.afn("1,200"))
    assertEquals("دکان سفارشِ #12 را پس خواسته است", StrCourierApp.orderRecalled("12"))
    assertEquals("موتورِ تازه روشن (آزمایشی)", StrSettingsPages.stockModeOn)
    assertEquals("چند کادر درست نیست؛ پیامِ زیرِ هر کادر را ببینید", StrSettingsPages.fixFields)
    assertEquals("اینترنت وصل نیست", ir.vil3ntec.tohid.core.net.ApiFailure.Offline().userMessage)
    assertEquals("پاسخ سرور خوانده نشد (x)", ir.vil3ntec.tohid.core.net.ApiFailure.InvalidResponse("x").userMessage)
    I18n.source = { Lang.PRS }
    assertEquals("مصروف هستم", StrCourierApp.rsBusy)
    assertEquals("روان شدم", StrCourierApp.acMoving)
    assertEquals("انترنت نیست — کارها در صفِ مبایل می‌مانند", StrCourierApp.offlineQueued)
    assertEquals("1,200 افغانی", StrCourierApp.afn("1,200"))
    assertEquals("موتورِ جدید روشن (آزمایشی)", StrSettingsPages.stockModeOn)
    assertEquals("انترنت وصل نیست", ir.vil3ntec.tohid.core.net.ApiFailure.Offline().userMessage)
    //  جزئیاتِ خطا داده است: «گوشی» ِ درونش دری نمی‌شود
    assertEquals("پاسخ سرور خوانده نشد (گوشی)", ir.vil3ntec.tohid.core.net.ApiFailure.InvalidResponse("گوشی").userMessage)
    //  انگلیسی و پشتوی پیامِ محلیِ شبکه همان فارسیِ پیشین (هنوز ترجمه ندارد؛ `SyncModel.classify` آن را می‌خواند)
    I18n.source = { Lang.EN }
    assertEquals("اینترنت وصل نیست", ir.vil3ntec.tohid.core.net.ApiFailure.Offline().userMessage)
    assertEquals("I'm busy", StrCourierApp.rsBusy)
  }

  @Test fun `همگام‌سازی پیامِ «انترنت» ِ دری را هم بی‌اینترنت می‌شناسد`() {
    assertEquals(
      ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.Problem.OFFLINE,
      ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.classify("انترنت وصل نیست"),
    )
    assertEquals(
      ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.classify("نشست شما تمام شد؛ دوباره وارد شوید"),
      ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel.classify(DariText.of("نشست شما تمام شد؛ دوباره وارد شوید")),
    )
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
    //  «… گردید» ِ دری سبز است؛ «برگردید» نه؛ فارسی همان قاعدهٔ پیشین
    assertEquals(I18n.Tone.SUCCESS, I18n.toneOf("ثبت گردید", Lang.PRS))
    assertEquals(I18n.Tone.SUCCESS, I18n.toneOf(DariText.of("فروش لغو شد"), Lang.PRS))
    assertEquals(I18n.Tone.INFO, I18n.toneOf("به صفحهٔ قبل برگردید", Lang.PRS))
    assertEquals(I18n.Tone.INFO, I18n.toneOf("ثبت گردید", Lang.FA))
    assertEquals(I18n.Tone.ERROR, I18n.toneOf(DariText.of("ثبت نشد، دوباره امتحان کنید"), Lang.PRS))
    assertEquals("مبایل را وصل کنید", GuideText("گوشی را وصل کنید").of("prs"))
    assertEquals("گوشی را وصل کنید", GuideText("گوشی را وصل کنید").of("fa"))
  }

  /* ------------------- دادهٔ کاربر در دری دست نمی‌خورد ------------------- */

  @Test fun `نامِ کالا و مشتری درونِ پیامِ دری بایت‌به‌بایت همان است`() {
    I18n.source = { Lang.PRS }
    val product = "گوشی سامسونگ انبار"
    assertEquals("حرکات گدام — $product", StrStock.movementsTitle(product))
    val customer = "فروشگاه کریم"
    assertEquals("$customer — 500 ؋ قرض‌داریم", StrMoney.owedLine(customer, "500"))
    //  فارسی همان پیشین
    I18n.source = { Lang.FA }
    assertEquals("حرکات انبار — $product", StrStock.movementsTitle(product))
    assertEquals("$customer — 500 ؋ بدهکاریم", StrMoney.owedLine(customer, "500"))
  }

  @Test fun `هر جای خالیِ متنی در همهٔ جدول‌ها در دری همان‌طور که داده شده دیده می‌شود`() {
    //  داده‌ای پر از واژه‌های فهرست: اگر جای خالیِ تازه‌ای بی asIs بیاید، این‌جا «مبایل/گدام» می‌شود
    val data = { i: Int -> "گوشی انبار فروشگاه کارمند ${7301 + i}" }
    val bad = ArrayList<String>()
    var seen = 0
    for (table in I18nTables.all) for (m in methods(table)) {
      if (m.parameterCount == 0) continue
      val args = argsFor(m, data)
      I18n.source = { Lang.FA }
      //  تابعی که جای خالی‌اش را عدد می‌خواند (متنِ نگهبان را نمی‌پذیرد) این‌جا سنجیده نمی‌شود
      val fa = try { m.invoke(table, *args) as String } catch (e: java.lang.reflect.InvocationTargetException) { continue }
      I18n.source = { Lang.PRS }
      val dari = m.invoke(table, *args) as String
      for (a in args.filterIsInstance<String>()) {
        if (!fa.contains(a)) continue
        seen++
        if (!dari.contains(a)) bad += "${table.javaClass.simpleName}.${m.name}: «$dari»"
      }
      assertFalse("${m.name}: نشانه در خروجی", dari.any { it == DariText.OPEN || it == DariText.CLOSE })
    }
    println("dari: $seen text placeholders checked")
    assertTrue("جای خالیِ متنی پیدا نشد", seen > 300)
    assertTrue("دادهٔ کاربر در دری عوض شد (asIs جا افتاده):\n" + bad.joinToString("\n"), bad.isEmpty())
  }
}
