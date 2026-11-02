package ir.vil3ntec.tohid.core.report

import ir.vil3ntec.tohid.core.report.SimpleAsk.Catalog
import ir.vil3ntec.tohid.core.report.SimpleAsk.Issue
import ir.vil3ntec.tohid.core.report.SimpleAsk.Named
import ir.vil3ntec.tohid.core.report.SimpleAsk.Param
import ir.vil3ntec.tohid.core.report.SimpleAsk.Period
import ir.vil3ntec.tohid.core.report.SimpleAsk.Problem
import ir.vil3ntec.tohid.core.report.SimpleAsk.Query
import ir.vil3ntec.tohid.core.report.SimpleAsk.Span
import ir.vil3ntec.tohid.core.report.SimpleAsk.Template
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RPT-16: خوانشِ قاعده‌ایِ جمله به پرسشِ آماده، بی حدس، و سنجشِ جای خالی و اجازه. */
class SimpleAskTest {

  private val catalog = Catalog(
    products = listOf(Named("p1", "برنج"), Named("p2", "روغن"), Named("p3", "روغن آفتاب")),
    categories = listOf("خوراکی", "شوینده"),
    customers = listOf(Named("c1", "احمد"), Named("c2", "کریم خان")),
    cashiers = listOf("علی", "نامعلوم"),
  )
  private val month = Period(Span.MONTH)

  /** امروز: عقرب ۱۴۰۵ */
  private fun ask(text: String, c: Catalog = catalog, jy: Int = 1405, jm: Int = 8): Query? =
    SimpleAsk.parse(text, c, jy, jm, month).query

  private fun ok(text: String): Query = assertNotNull(ask(text), text)

  @Test fun everyTemplateIsReachableFromAShortSentence() {
    assertEquals(Query(Template.SALES, Period(Span.TODAY)), ok("فروش امروز چقدر بود؟"))
    assertEquals(Query(Template.PROFIT, Period(Span.WEEK)), ok("سودِ این هفته"))
    assertEquals(Query(Template.EXPENSES, Period(Span.JYEAR, 1405)), ok("مصارفِ امسال"))
    assertEquals(Query(Template.PURCHASES, Period(Span.ALL)), ok("خرید از آغاز تا حالا"))
    assertEquals(Query(Template.CASHIER_SALES, month, cashier = "علی"), ok("فروشِ علی"))
    assertEquals(Query(Template.CATEGORY_SALES, Period(Span.JMONTH, 1405, 1), category = "خوراکی"), ok("فروش دستهٔ خوراکی در حمل"))
    assertEquals(Query(Template.PRODUCT_SALES, Period(Span.JMONTH, 1405, 7), product = "p1"), ok("فروش برنج ماه پیش"))
    assertEquals(Query(Template.PRODUCT_STOCK, product = "p1"), ok("موجودیِ برنج"))
    assertEquals(Query(Template.PRODUCT_STOCK, product = "p2"), ok("روغن چند مانده؟"))
    assertEquals(Query(Template.LOW_STOCK), ok("کالاهای کم"))
    assertEquals(Query(Template.LOW_STOCK), ok("چه چیزهایی تمام شده"))
    assertEquals(Query(Template.TOP_SELLING), ok("پرفروش‌ترین کالاها"))
    assertEquals(Query(Template.SLOW_SELLING), ok("کم‌فروش‌ترین کالاها"))
    assertEquals(Query(Template.CUSTOMER_DEBT, customer = "c1"), ok("قرضِ احمد چقدر است"))
    assertEquals(Query(Template.CUSTOMER_DEBT, customer = "c2"), ok("کریم خان چقدر بدهکار است"))
    assertEquals(Query(Template.DEBT_TOTAL), ok("جمعِ قرض‌ها"))
    assertEquals(Query(Template.SALES, Period(Span.TODAY)), ok("sales today"))
    assertEquals(Query(Template.PROFIT, Period(Span.TODAY)), ok("نن ګټه"))
  }

  @Test fun arabicLettersPersianDigitsAndZwnjReadTheSame() {
    assertEquals("علی فروش 1404", SimpleAsk.normalize("علي، فروشِ ۱۴۰۴!"))
    assertEquals(listOf("قرض", "ها"), SimpleAsk.tokens("قرض‌ها"))
    assertEquals(Query(Template.CASHIER_SALES, Period(Span.JYEAR, 1404), cashier = "علی"), ok("فروشِ علي سال ۱۴۰۴"))
    assertEquals(Query(Template.PRODUCT_STOCK, product = "p2"), ok("موجودي روغن"))
  }

  @Test fun theLongestFullNameWinsAndAPartialNameIsNotAGuess() {
    assertEquals("p3", ok("موجودی روغن آفتاب").product)
    //  «کریم» تنها نامِ کاملی نیست ⇒ واژهٔ ناشناخته، نه «کریم خان»
    val p = SimpleAsk.parse("قرض کریم", catalog, 1405, 8, month)
    assertNull(p.query)
    assertEquals(listOf("کریم"), p.unknown)
  }

  @Test fun unknownWordsLeaveTheQuestionUnanswered() {
    val debt = SimpleAsk.parse("قرض محمود", catalog, 1405, 8, month)
    assertNull(debt.query)
    assertEquals(listOf("محمود"), debt.unknown)
    assertEquals(listOf("شکر"), SimpleAsk.parse("فروش برنج و شکر", catalog, 1405, 8, month).unknown)
    //  سودِ یک فروشنده پرسشِ آماده‌ای نیست ⇒ نامِ او نادیده گرفته نمی‌شود
    assertEquals(listOf("علی"), SimpleAsk.parse("سود علی", catalog, 1405, 8, month).unknown)
    assertNull(ask("سلام"))
    assertNull(ask("   "))
    assertTrue(SimpleAsk.parse("امروز", catalog, 1405, 8, month).let { it.query == null && it.unknown.isEmpty() })
  }

  @Test fun anAmbiguousNameLeavesItsSlotEmpty() {
    val twins = catalog.copy(customers = listOf(Named("c1", "احمد"), Named("c9", "احمد")))
    val p = SimpleAsk.parse("قرض احمد", twins, 1405, 8, month)
    assertEquals(Query(Template.CUSTOMER_DEBT), p.query)
    assertEquals(Param.CUSTOMER, p.ambiguous)
    assertEquals(listOf(Issue(Problem.MISSING, Param.CUSTOMER)), SimpleAsk.validate(p.query!!, true, twins))
  }

  @Test fun solarMonthNamesResolveToTheRightYear() {
    //  حوت ِ بی سال در عقرب هنوز نرسیده ⇒ حوتِ پارسال
    assertEquals(Period(Span.JMONTH, 1404, 12), ok("فروش حوت").period)
    assertEquals(Period(Span.JMONTH, 1405, 12), ok("فروش حوت 1405").period)
    assertEquals(Period(Span.JMONTH, 1405, 8), ok("فروش عقرب").period)
    assertEquals(Period(Span.JMONTH, 1405, 2), ok("فروش اردیبهشت").period)
    assertEquals(Period(Span.JMONTH, 1405, 3), ok("فروش غبرګولی").period)
    assertEquals(Period(Span.JYEAR, 1404), ok("فروش پارسال").period)
    assertEquals(Period(Span.JYEAR, 1404), ok("فروش سال گذشته").period)
    //  ماهِ پیش در حمل ⇒ حوتِ سالِ پیش
    assertEquals(Period(Span.JMONTH, 1404, 12), ask("فروش ماه پیش", jm = 1)?.period)
    assertEquals(Period(Span.WEEK), ok("فروش ۷ روز").period)
    //  بی واژهٔ بازه ⇒ همان بازهٔ انتخاب‌شدهٔ صفحه
    assertEquals(month, ok("فروش").period)
    //  پرسشِ بی بازه بازه نمی‌گیرد
    assertNull(ok("قرض احمد امروز").period)
  }

  @Test fun validationNamesEachMissingOrUnknownSlot() {
    assertEquals(emptyList(), SimpleAsk.validate(Query(Template.SALES, month), true, catalog))
    assertEquals(listOf(Issue(Problem.MISSING, Param.PERIOD)), SimpleAsk.validate(Query(Template.SALES), true, catalog))
    assertEquals(
      listOf(Issue(Problem.MISSING, Param.PERIOD), Issue(Problem.MISSING, Param.PRODUCT)),
      SimpleAsk.validate(Query(Template.PRODUCT_SALES), true, catalog),
    )
    assertEquals(listOf(Issue(Problem.UNKNOWN, Param.PRODUCT)), SimpleAsk.validate(Query(Template.PRODUCT_STOCK, product = "p404"), true, catalog))
    assertEquals(listOf(Issue(Problem.UNKNOWN, Param.CATEGORY)), SimpleAsk.validate(Query(Template.CATEGORY_SALES, month, category = "ابزار"), true, catalog))
    assertEquals(listOf(Issue(Problem.UNKNOWN, Param.CASHIER)), SimpleAsk.validate(Query(Template.CASHIER_SALES, month, cashier = "رضا"), true, catalog))
    assertEquals(listOf(Issue(Problem.BAD_PERIOD, Param.PERIOD)), SimpleAsk.validate(Query(Template.SALES, Period(Span.JMONTH, 1405, 13)), true, catalog))
    assertEquals(listOf(Issue(Problem.BAD_PERIOD, Param.PERIOD)), SimpleAsk.validate(Query(Template.SALES, Period(Span.JYEAR, 0)), true, catalog))
    assertTrue(SimpleAsk.validPeriod(Period(Span.JMONTH, 1403, 12)))
  }

  @Test fun moneyQuestionsAreClosedToRolesWithoutReportsMoney() {
    val moneyIds = setOf("sales", "profit", "expenses", "purchases", "cashier_sales", "category_sales", "product_sales")
    assertEquals(moneyIds, Template.entries.filter { it.money }.map { it.id }.toSet())
    assertEquals(Template.entries.filter { !it.money }, SimpleAsk.templates(canSeeMoney = false))
    assertEquals(Template.entries.toList(), SimpleAsk.templates(canSeeMoney = true))
    //  اجازه پیش از جای خالی: پرسشِ ممنوع چیزی از نام‌ها نمی‌گوید
    assertEquals(listOf(Issue(Problem.NOT_ALLOWED)), SimpleAsk.validate(Query(Template.PRODUCT_SALES, product = "p404"), false, catalog))
    assertEquals(emptyList(), SimpleAsk.validate(Query(Template.CUSTOMER_DEBT, customer = "c1"), false, catalog))
    for (t in Template.entries) assertEquals(t, Template.of(t.id))
    assertNull(Template.of("ai"))
  }
}
