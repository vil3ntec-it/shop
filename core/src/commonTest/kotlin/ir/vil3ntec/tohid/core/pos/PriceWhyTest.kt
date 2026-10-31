package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.PriceWhy.Kind
import ir.vil3ntec.tohid.core.pos.PriceWhy.Promo
import ir.vil3ntec.tohid.core.pos.PriceWhy.Row
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *  POS-22 — «چرا این قیمت؟». پیش از این ردیفِ سبد فقط «۳ × ۹۰ = ۲۷۰» نشان می‌داد. فروشنده نمی‌دانست
 *  قیمتِ فهرست چند بود، قیمتِ دستی چقدر جابه‌جایش کرد و کدام تخفیفِ خودکار چه‌قدر از آن کم کرد.
 */
class PriceWhyTest {

  @Test
  fun listPriceOnlyIsJustListAndTotal() {
    //  ۳ × ۱۰۰ = ۳۰۰، بی دستی و بی تخفیف ⇒ فقط دو ردیف
    assertEquals(
      listOf(Row(Kind.LIST, 10_000), Row(Kind.TOTAL, 30_000)),
      PriceWhy.explain(3_000, 10_000, null, emptyList(), 30_000),
    )
    //  قیمتِ دستیِ برابر با فهرست «دستی» نیست
    assertEquals(
      listOf(Row(Kind.LIST, 10_000), Row(Kind.TOTAL, 30_000)),
      PriceWhy.explain(3_000, 10_000, 10_000, emptyList(), 30_000),
    )
  }

  @Test
  fun manualPriceShowsItsDistanceFromTheList() {
    assertEquals(
      listOf(Row(Kind.LIST, 10_000), Row(Kind.MANUAL, 9_000, deltaMinor = -1_000), Row(Kind.TOTAL, 27_000)),
      PriceWhy.explain(3_000, 10_000, 9_000, emptyList(), 27_000),
    )
    //  گران‌تر از فهرست
    assertEquals(1_500L, PriceWhy.explain(1_000, 10_000, 11_500, emptyList(), 11_500)[1].deltaMinor)
  }

  @Test
  fun promosAreNamedAndRoundingIsNeverHidden() {
    //  ۲ × ۱۲۵ = ۲۵۰؛ «زمستانه» ۲۵ و «کد» ۱۲٫۵۰ ⇒ ۲۱۲٫۵۰؛ سبد پس از گرد کردنِ قیمتِ واحد ۲۱۳ نشان می‌دهد
    assertEquals(
      listOf(
        Row(Kind.LIST, 12_500),
        Row(Kind.BASE, 25_000),
        Row(Kind.PROMO, -2_500, "زمستانه"),
        Row(Kind.PROMO, -1_250, "کد"),
        Row(Kind.ROUNDING, 50),
        Row(Kind.TOTAL, 21_300),
      ),
      PriceWhy.explain(2_000, 12_500, null, listOf(Promo("زمستانه", 2_500), Promo("کد", 1_250), Promo("صفر", 0)), 21_300),
    )
  }

  @Test
  fun freeLineHasNoListAndUsesItsOwnPrice() {
    assertEquals(
      listOf(Row(Kind.MANUAL, 1_050, deltaMinor = null), Row(Kind.BASE, 1_100), Row(Kind.ROUNDING, -100), Row(Kind.TOTAL, 1_000)),
      PriceWhy.explain(1_000, null, 1_050, emptyList(), 1_000),
    )
  }

  @Test
  fun lineMinorIsTheTillRounding() {
    assertEquals(1_100L, PriceWhy.lineMinor(1_000, 1_050))   // ۱۰٫۵ ⇐ ۱۱
    assertEquals(1_000L, PriceWhy.lineMinor(1_000, 1_049))
    assertEquals(25_000L, PriceWhy.lineMinor(250, 100_000))   // ۰٫۲۵ کیلو × ۱۰۰۰
    assertFailsWith<IllegalArgumentException> { PriceWhy.lineMinor(PriceWhy.MAX_QTY_MILLI + 1, 1) }
  }
}
