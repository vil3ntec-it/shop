package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.staff.Commission.Item
import ir.vil3ntec.tohid.core.staff.Commission.Line
import ir.vil3ntec.tohid.core.staff.Commission.Plan
import ir.vil3ntec.tohid.core.staff.LineSeller.Share
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** POS-23 / STF-08 — فروشندهٔ هر ردیف و پورسانتِ هر فروشندهٔ ردیف */
class LineSellerTest {

  private val afn = Currency.AFN
  private fun m(minor: Long) = Money(minor, afn)

  @Test
  fun lineSellerDefaultsToTheInvoiceSeller() {
    assertEquals("احمد", LineSeller.of(null, " احمد "))
    assertEquals("احمد", LineSeller.of("  ", "احمد"))
    assertEquals("زهرا", LineSeller.of(" زهرا ", "احمد"))
    //  کلیدِ خاموش: فروشندهٔ ردیفِ نوشته‌شده هم دیده نمی‌شود
    assertEquals("احمد", LineSeller.of(false, "زهرا", "احمد"))
    assertEquals("زهرا", LineSeller.of(true, "زهرا", "احمد"))
  }

  @Test
  fun storedOnlyWhenOnAndDifferentFromTheInvoiceSeller() {
    assertNull(LineSeller.stored(false, "زهرا", "احمد"))
    assertNull(LineSeller.stored(true, null, "احمد"))
    assertNull(LineSeller.stored(true, "  ", "احمد"))
    assertNull(LineSeller.stored(true, " احمد", "احمد "))
    assertEquals("زهرا", LineSeller.stored(true, " زهرا ", "احمد"))
  }

  @Test
  fun onlyOwnerOrManagerAssignsSomeoneElse() {
    //  خودم و «برگشت به فروشندهٔ فاکتور» همیشه
    assertTrue(LineSeller.mayAssign("احمد", " احمد ", othersAllowed = false))
    assertTrue(LineSeller.mayAssign("احمد", null, othersAllowed = false))
    assertTrue(LineSeller.mayAssign("احمد", "", othersAllowed = false))
    //  نامِ دیگری فقط با مجوز
    assertFalse(LineSeller.mayAssign("احمد", "زهرا", othersAllowed = false))
    assertTrue(LineSeller.mayAssign("احمد", "زهرا", othersAllowed = true))
    //  گزینه‌ها: خودم اول، بقیه یکتا و به ترتیب؛ بی مجوز فقط خودم
    assertEquals(listOf("احمد", "بهار", "زهرا"), LineSeller.choices(" احمد", listOf("زهرا", "احمد", " بهار ", "", "زهرا"), true))
    assertEquals(listOf("احمد"), LineSeller.choices("احمد", listOf("زهرا", "بهار"), false))
  }

  @Test
  fun mixedSellersSplitTheInvoiceExactly() {
    //  ۳ ردیف: ۱۰۰٫۰۰ (احمد، فروشندهٔ فاکتور)، ۲۰۰٫۰۰ (زهرا)، ۱۰۰٫۰۱ (احمد)؛ تخفیفِ فاکتور ۱۰٫۰۱ ⇒ خالص ۳۹۰٫۰۰
    val items = listOf(LineSeller.Item(null, 10_000), LineSeller.Item("زهرا", 20_000), LineSeller.Item("احمد", 10_001))
    val shares = LineSeller.split("احمد", 39_000, 1_001, items)
    assertEquals(listOf("احمد", "زهرا"), shares.map { it.seller })
    assertEquals(39_000L, shares.sumOf { it.net })
    assertEquals(1_001L, shares.sumOf { it.discount })
    //  سهمِ تخفیف به نسبتِ مبلغ و «بزرگ‌ترین باقی‌مانده»: ۲۵۰ + ۵۰۱ + ۲۵۰ (مجموع ۱۰۰۱)
    assertEquals(listOf(Share("احمد", 19_501, 500), Share("زهرا", 19_499, 501)), shares)
    //  یک فروشنده (حتی وقتی همه به نامِ دیگری‌اند) ⇒ همان یک سهمِ فاکتور
    assertEquals(listOf(Share("احمد", 39_000, 1_001)), LineSeller.split("احمد", 39_000, 1_001, items.map { it.copy(seller = null) }))
    assertEquals(listOf(Share("زهرا", 500, 0)), LineSeller.split("احمد", 500, 0, listOf(LineSeller.Item("زهرا", 500))))
    assertEquals(listOf(Share("احمد", 500, 0)), LineSeller.split("احمد", 500, 0, emptyList()))
    //  پخش‌ناپذیر (ردیفِ منفی) ⇒ همه به نامِ فروشندهٔ فاکتور، نه گم
    assertEquals(listOf(Share("احمد", 500, 0)), LineSeller.split("احمد", 500, 0, listOf(LineSeller.Item("زهرا", -1), LineSeller.Item(null, 501))))
  }

  @Test
  fun commissionPerLineSellerRoundsOncePerSellerOnTheExactTotal() {
    //  فاکتورِ احمد با یک ردیفِ زهرا؛ تخفیفِ فاکتور از هر دو به نسبت کم می‌شود و جمعِ پایه‌ها دقیقاً `finalTotal` است
    val lines = Commission.saleLines(
      "احمد", m(33_333),
      listOf(Item(null, m(11_111)), Item(null, m(11_111), seller = "زهرا"), Item("rice", m(11_112), seller = "زهرا")),
    )
    assertEquals(33_333L, lines.sumOf { it.net.minor })
    assertEquals(listOf("احمد", "زهرا", "زهرا"), lines.map { it.sellerId })
    assertEquals(listOf(null, null, "rice"), lines.map { it.category })
    //  ۲٫۵٪ برای همه و ۱۰٪ برای برنج، گرد یک بار روی جمعِ هر فروشنده به پلهٔ افغانی (نیمه رو به بالا):
    //  احمد ۱۱۱٫۱۱×۲٫۵٪ = ۲٫۷۷ ⇒ ۳؛ زهرا ۱۱۱٫۱۱×۲٫۵٪ + ۱۱۱٫۱۱×۱۰٪ = ۱۳٫۸۹ ⇒ ۱۴ (ردیف‌به‌ردیف ۳ + ۱۱ = ۱۴ هم می‌شد، ولی گرد یک بار است)
    val r = Commission.compute(lines, Plan(250, categoryBp = mapOf("rice" to 1_000)), afn)
    assertEquals(listOf(m(300), m(1_400)), r.sellers.map { it.commission })
    assertEquals(listOf("احمد", "زهرا"), r.sellers.map { it.sellerId })
    assertEquals(33_333L, r.sellers.sumOf { it.base.minor })
    val base = r.sellers.associate { it.sellerId to it.base.minor }
    assertEquals(11_111L, base.getValue("احمد"))
    assertEquals(22_222L, base.getValue("زهرا"))
  }

  @Test
  fun aReturnReducesTheOriginalLineSeller() {
    val origins = mapOf("s1" to Commission.Origin("احمد"), "s2" to Commission.Origin("احمد", cancelled = true))
    val back = Commission.returnLines(
      listOf(
        Commission.Returned("s1", m(1_000), seller = "زهرا"),      // ردیفِ زهرا ⇒ از زهرا
        Commission.Returned("s1", m(500)),                         // ردیفِ بی فروشنده ⇒ از فروشندهٔ فاکتور
        Commission.Returned("s2", m(700), seller = "زهرا"),        // فاکتورِ لغوشده ⇒ بیرون
      ),
      origins,
    )
    assertEquals(listOf(Line("زهرا", null, m(-1_000)), Line("احمد", null, m(-500))), back)
  }

  @Test
  fun withoutLineSellersCommissionLinesAreExactlyAsBefore() {
    val items = listOf(Item("rice", m(6_000)), Item(null, m(4_000)), Item("rice", m(1_000)))
    //  همان خروجیِ پیش از POS-23: ردیف‌ها به نامِ فروشندهٔ فاکتور، به تفکیکِ دسته
    assertEquals(
      listOf(Line("s1", "rice", m(6_364)), Line("s1", null, m(3_636))),
      Commission.saleLines("s1", m(10_000), items),
    )
    //  فروشندهٔ ردیفِ برابر با فاکتور همان بی فروشنده است
    assertEquals(Commission.saleLines("s1", m(10_000), items), Commission.saleLines("s1", m(10_000), items.map { it.copy(seller = " s1 ") }))
    assertEquals(listOf(Line("s1", null, m(10_000))), Commission.saleLines("s1", m(10_000), items.map { it.copy(category = null) }))
  }
}
