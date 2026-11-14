package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.LinePricing.Direction
import ir.vil3ntec.tohid.core.pos.LinePricing.Policy
import ir.vil3ntec.tohid.core.product.UnitSale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  S45 — تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ (#۲۱): با بسته/کارتنِ انتخاب‌شده که قیمتِ جدا دارد، قاعدهٔ جهت و «کاهش» ِ ردیف
 *  با **قیمتِ همان بسته** سنجیده می‌شوند، نه قیمتِ پایهٔ کالا (`LinePricing.ruleListMinor`، هر دو صندوق).
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class LinePricingPackTest {

  /** پایه ۱۰۰ افغانی؛ کارتنِ ۱۲ دانه‌ای ۹۶۰ (= ۸۰ برای هر دانه) */
  private val base = 10_000L
  private val carton = UnitSale.Unit("کارتن", 12_000, 96_000)
  private val piece = UnitSale.Unit("دانه", 1_000)
  private val downOnly = Policy(on = true, direction = Direction.DOWN)

  @Test
  fun selectedPackWithOwnPriceIsTheRuleList() {
    assertEquals(8_000, LinePricing.ruleListMinor(base, carton))
    //  بی انتخاب، واحدِ پایه یا واحدِ بی قیمتِ جدا ⇒ همان قیمتِ کالا
    assertEquals(base, LinePricing.ruleListMinor(base, null))
    assertEquals(base, LinePricing.ruleListMinor(base, piece))
    assertEquals(base, LinePricing.ruleListMinor(base, UnitSale.Unit("بسته", 6_000)))
    //  گردِ نیمه رو به بالا (همان `SaleCaps.refUnits`): ۱۰۰۰ ÷ ۱۲ = ۸۳٫۳۳ ⇒ ۸۳۳۳
    assertEquals(8_333, LinePricing.ruleListMinor(base, UnitSale.Unit("کارتن", 12_000, 100_000)))
    //  عددِ بی‌معنا قاعده را باز نمی‌کند: همان قیمتِ کالا
    assertEquals(base, LinePricing.ruleListMinor(base, UnitSale.Unit("کارتن", 0, 96_000)))
    assertEquals(base, LinePricing.ruleListMinor(base, UnitSale.Unit("کارتن", 12_000, LinePricing.MAX_EXACT)))
  }

  @Test
  fun downOnlyRaisingAPackAboveItsOwnPriceNeedsApproval() {
    val list = LinePricing.ruleListMinor(base, carton)
    //  مثالِ تصمیم: پایه ۱۰۰، بسته ۸۰، «فقط پایین» ⇒ ۹۵ برای بسته بالا رفتن است
    val e = LinePricing.edit(downOnly, false, list, list, 9_500, 12_000, false, 0)
    assertEquals(LinePricing.PRICE_UP_BLOCKED, e.problem)
    assertTrue(PriceApproval.askable(downOnly, e.problem))
    //  همان ۹۵ با قیمتِ پایه پایین آمدن بود و آزاد — رفتارِ پیشین که تصمیم بست
    assertNull(LinePricing.edit(downOnly, false, base, base, 9_500, 12_000, false, 0).problem)
    //  زیرِ قیمتِ بسته آزاد است
    assertNull(LinePricing.edit(downOnly, false, list, list, 7_500, 12_000, false, 0).problem)
  }

  @Test
  fun upOnlyAndNoReductionAreMeasuredFromThePackToo() {
    val list = LinePricing.ruleListMinor(base, carton)
    val upOnly = Policy(on = true, direction = Direction.UP)
    //  ۹۰ بالای قیمتِ بسته است ⇒ «فقط بالا» می‌پذیرد (با پایه رد می‌شد)
    assertNull(LinePricing.edit(upOnly, false, list, list, 9_000, 12_000, false, 0).problem)
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, LinePricing.edit(upOnly, false, base, base, 9_000, 12_000, false, 0).problem)
    //  قیمتِ خودِ بسته برای کسی که تخفیف برایش «ممنوع» است کاهش نیست
    val noReduce = Policy(on = true, mayReduce = false)
    assertNull(LinePricing.edit(noReduce, false, list, base, 8_000, 12_000, false, 0).problem)
    assertEquals(LinePricing.REDUCTION_DENIED, LinePricing.edit(noReduce, false, list, list, 7_900, 12_000, false, 0).problem)
  }

  @Test
  fun lineDiscountOnAPackIsTakenFromThePackPrice() {
    //  ۱۰٪ ِ کارتنِ ۹۶۰ = ۸۶۴ ⇒ ۷۲ برای هر دانه (با پایه ۹۰ می‌شد)
    val list = LinePricing.ruleListMinor(base, carton)
    val e = LinePricing.edit(Policy(on = true), false, list, list, null, 12_000, true, 1_000)
    assertEquals(96_000, e.grossMinor)
    assertEquals(9_600, e.discountMinor)
    assertEquals(72.0, e.netUnit!!, 1e-9)
  }
}
