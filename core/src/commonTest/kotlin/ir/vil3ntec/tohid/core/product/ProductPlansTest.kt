package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.ProductKit.Part
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  PRD-10 (کیت که موجودیِ اجزا را کم می‌کند) و PRD-13 (قیمتِ زمان‌بندی‌شده). پیش از این «سبدِ رمضان»
 *  یا باید کالای جدا با موجودیِ ساختگی می‌بود، و قیمتِ هفتهٔ عید دستی عوض و دستی برگردانده می‌شد.
 */
class ProductPlansTest {

  private val names = mapOf("روغن" to "oil", "برنج" to "rice", "سبد" to "kit", "111" to "oil")

  private fun parseKit(text: String, self: String? = "basket") =
    ProductKit.parse(text, self, { names[it] }, { it == "kit" })

  @Test
  fun kitAvailabilityIsTheScarcestPartRoundedDown() {
    val parts = listOf(Part("oil", 2000), Part("rice", 5000))
    val stock = mapOf("oil" to 7000L, "rice" to 26_500L)
    //  روغن: ۷ ÷ ۲ = ۳؛ برنج: ۲۶٫۵ ÷ ۵ = ۵ ⇒ ۳ کیت
    assertEquals(3L, ProductKit.available(parts) { stock[it] ?: 0L })
    //  جزءِ منفی یا بی موجودی ⇒ ۰، نه منفی
    assertEquals(0L, ProductKit.available(parts) { if (it == "oil") -4000L else 99_000L })
    assertEquals(0L, ProductKit.available(emptyList()) { 1_000_000L })
  }

  @Test
  fun kitDemandScalesEachPart() {
    val parts = listOf(Part("oil", 2000), Part("rice", 1500))
    assertEquals(listOf("oil" to 6000L, "rice" to 4500L), ProductKit.demand(parts, 3000))
    //  نیم کیت (واحدِ اعشاری) با گردِ نیمه به بالا
    assertEquals(listOf("oil" to 1000L, "rice" to 750L), ProductKit.demand(parts, 500))
  }

  @Test
  fun kitTextReadsNameOrBarcodeWithAmountAndAddsRepeats() {
    val p = parseKit("روغن × 2\nبرنج * ۵٫۵\n111\n\n  ")
    assertEquals(listOf(Part("oil", 3000), Part("rice", 5500)), p.parts)
    assertEquals(emptyList(), p.problems)
  }

  @Test
  fun kitTextRejectsUnknownSelfNestedAndBadAmounts() {
    val p = parseKit("نیست × 1\nسبد × 1\nروغن × 0\nبرنج × -2\nروغن: ده", self = "rice")
    assertEquals(emptyList(), p.parts)
    assertEquals(
      listOf(ProductKit.Problem.UNKNOWN, ProductKit.Problem.NESTED, ProductKit.Problem.BAD_QTY, ProductKit.Problem.SELF, ProductKit.Problem.BAD_QTY),
      p.problems.map { it.second },
    )
  }

  @Test
  fun kitTextRoundTrips() {
    val parts = listOf(Part("oil", 2000), Part("rice", 5500))
    val label = mapOf("oil" to "روغن", "rice" to "برنج")
    val text = ProductKit.format(parts, { label.getValue(it) }, { (it / 1000.0).toString().removeSuffix(".0") })
    assertEquals("روغن × 2\nبرنج × 5.5", text)
    assertEquals(parts, parseKit(text).parts)
  }

  private fun day(s: String): String? = Regex("""(\d{4})/(\d{2})/(\d{2})""").matchEntire(s.trim())?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }

  @Test
  fun scheduleReadsLinesSortsAndPicksTheActiveWindow() {
    val p = PriceSchedule.parse("2026/03/20 تا 2026/03/25 = 450\n2026/03/01 تا 2026/03/10 = ۴۰۰٫۵", ::day)
    assertEquals(emptyList(), p.problems)
    assertEquals(listOf("2026-03-01", "2026-03-20"), p.windows.map { it.from })
    assertEquals(40050L, PriceSchedule.price(50000, p.windows, "2026-03-01"))
    assertEquals(40050L, PriceSchedule.price(50000, p.windows, "2026-03-10"))
    assertEquals(50000L, PriceSchedule.price(50000, p.windows, "2026-03-11"))
    assertEquals(45000L, PriceSchedule.price(50000, p.windows, "2026-03-25"))
    assertNull(PriceSchedule.active(p.windows, "2026-03-26"))
  }

  @Test
  fun scheduleRejectsBadLinesAndOverlaps() {
    val p = PriceSchedule.parse(
      "2026/03/01 تا 2026/03/10 = 400\n2026/03/10 تا 2026/03/12 = 410\n2026/04/05 تا 2026/04/01 = 1\nفردا تا پس‌فردا = 1\n2026/05/01 تا 2026/05/02 = ارزان\nبی‌مساوی",
      ::day,
    )
    assertEquals(1, p.windows.size)
    assertEquals(
      setOf(PriceSchedule.Problem.OVERLAP, PriceSchedule.Problem.END_BEFORE_START, PriceSchedule.Problem.BAD_DATE, PriceSchedule.Problem.BAD_PRICE, PriceSchedule.Problem.BAD_LINE),
      p.problems.map { it.second }.toSet(),
    )
  }

  @Test
  fun scheduleTextRoundTrips() {
    val w = listOf(PriceSchedule.Window("2026-03-01", "2026-03-10", 40050))
    val text = PriceSchedule.format(w, { it.replace('-', '/') }, { (it / 100.0).toString() })
    assertEquals("2026/03/01 تا 2026/03/10 = 400.5", text)
    assertEquals(w, PriceSchedule.parse(text, ::day).windows)
  }
}
