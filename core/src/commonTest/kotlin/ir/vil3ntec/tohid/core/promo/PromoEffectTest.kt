package ir.vil3ntec.tohid.core.promo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRM-16 — اثرِ هر تخفیف روی فروش و سود: فاکتور و قلم، تخفیفِ خودِ قاعده، قلمِ دوتخفیفه،
 *  برگشتی به نسبت با گردِ نیمه به بالا، بهای نامعلوم، نرخِ ده‌هزارم و بی سرریز.
 */
class PromoEffectTest {

  private val ramadan = PromoTag("p-ramadan", "رمضان ۱۰٪", 0L)
  private fun tag(t: PromoTag, d: Long) = t.copy(discountMinor = d)
  private val oil = PromoTag("p-oil", "روغن ۲+۱", 0L)

  @Test
  fun rowsPerPromoWithOwnDiscountAndSharedLines() {
    val r = PromoEffect.of(
      listOf(
        //  فاکتورِ ۱: برنج با رمضان، روغن با هر دو
        EffectLine("s1", 2_000L, netMinor = 18_000L, costMinor = 15_000L, promos = listOf(tag(ramadan, 2_000L))),
        EffectLine("s1", 3_000L, netMinor = 60_000L, costMinor = 54_000L, promos = listOf(tag(oil, 30_000L), tag(ramadan, 6_000L))),
        //  فاکتورِ ۲: روغن با «روغن ۲+۱»، و قلمی بی تخفیف
        EffectLine("s2", 3_000L, netMinor = 60_000L, costMinor = null, promos = listOf(tag(oil, 30_000L))),
        EffectLine("s2", 1_000L, netMinor = 5_000L, costMinor = 4_000L),
        //  مقدارِ صفر یا منفی بیرون
        EffectLine("s3", 0L, netMinor = 99_999L, promos = listOf(tag(ramadan, 1L))),
      ),
    )
    assertEquals(143_000L, r.totalRevenueMinor)
    assertEquals(138_000L, r.promoRevenueMinor, "قلمِ دوتخفیفه یک بار")
    assertEquals(68_000L, r.promoDiscountMinor)
    assertEquals(r.promoDiscountMinor, r.rows.sumOf { it.discountMinor }, "جمعِ تخفیفِ ردیف‌ها = کلِ تخفیف")
    assertTrue(r.profitApproximate, "تا D-23 تقریبی")
    assertEquals(listOf("p-oil", "p-ramadan"), r.rows.map { it.id }, "تخفیفِ بیشتر اول")

    val o = r.rows[0]
    assertEquals("روغن ۲+۱", o.name)
    assertEquals(2, o.invoices)
    assertEquals(2, o.lines)
    assertEquals(6_000L, o.qtyMilli)
    assertEquals(60_000L, o.discountMinor)
    assertEquals(120_000L, o.revenueMinor)
    assertEquals(6_000L, o.profitMinor, "فقط قلمِ بهادار: ۶۰۰۰۰ − ۵۴۰۰۰")
    assertEquals(1, o.costMissingLines)
    assertEquals(8_392L, o.revenueShareBp, "۱۲۰۰۰۰ ÷ ۱۴۳۰۰۰ = ۰٫۸۳۹۱۶… ⇒ ۸۳۹۲")
    assertEquals(3_333L, o.discountDepthBp, "۶۰۰۰۰ ÷ ۱۸۰۰۰۰")

    val m = r.rows[1]
    assertEquals(1, m.invoices)
    assertEquals(2, m.lines)
    assertEquals(8_000L, m.discountMinor)
    assertEquals(78_000L, m.revenueMinor)
    assertEquals(9_000L, m.profitMinor, "(۱۸۰۰۰ − ۱۵۰۰۰) + (۶۰۰۰۰ − ۵۴۰۰۰)")
    assertEquals(0, m.costMissingLines)
    assertEquals(930L, m.discountDepthBp, "۸۰۰۰ ÷ ۸۶۰۰۰ = ۰٫۰۹۳۰۲ ⇒ ۹۳۰")
  }

  @Test
  fun returnsReduceInProportionWithHalfUp() {
    //  ۳ واحد، ۱ برگشت: ⅔ ِ ۱۰۰۰۱ = ۶۶۶۷٫۳ ⇒ ۶۶۶۷؛ ⅔ ِ ۵۰۰ = ۳۳۳٫۳ ⇒ ۳۳۳؛ ⅔ ِ ۷۰۰۱ = ۴۶۶۷٫۳ ⇒ ۴۶۶۷
    val r = PromoEffect.of(
      listOf(EffectLine("s1", 3_000L, returnedMilli = 1_000L, netMinor = 10_001L, costMinor = 7_001L, promos = listOf(tag(ramadan, 500L)))),
    )
    val row = r.rows.single()
    assertEquals(2_000L, row.qtyMilli)
    assertEquals(6_667L, row.revenueMinor)
    assertEquals(333L, row.discountMinor)
    assertEquals(6_667L - 4_667L, row.profitMinor)
    assertEquals(6_667L, r.totalRevenueMinor)
    //  نیمهٔ دقیق رو به بالا: ½ ِ ۱۵ = ۷٫۵ ⇒ ۸
    assertEquals(8L, PromoEffect.share(15L, 1L, 2L))
    assertEquals(-8L, PromoEffect.share(-15L, 1L, 2L), "قرینه برای منفی")
    //  همه برگشت ⇒ صفر؛ برگشتیِ بیش از فروش ⇒ همه
    val all = PromoEffect.of(listOf(EffectLine("s", 2_000L, returnedMilli = 5_000L, netMinor = 900L, costMinor = 100L, promos = listOf(tag(oil, 50L)))))
    assertEquals(0L, all.rows.single().revenueMinor)
    assertEquals(0L, all.rows.single().profitMinor)
    assertNull(all.rows.single().revenueShareBp, "کلِ فروش صفر ⇒ null")
    assertNull(all.rows.single().discountDepthBp)
  }

  @Test
  fun duplicateTagsMergeAndUnknownCostIsNullNotZero() {
    val r = PromoEffect.of(
      listOf(
        EffectLine("s1", 1_000L, netMinor = 1_000L, promos = listOf(tag(oil, 100L), PromoTag("p-oil", "", 50L), PromoTag("", "بی‌نام", 7L))),
      ),
    )
    val row = r.rows.single()
    assertEquals(150L, row.discountMinor, "دو برچسبِ یک تخفیف روی یک قلم = یکی")
    assertEquals(1, row.lines)
    assertEquals("روغن ۲+۱", row.name, "نامِ خالی نامِ پیشین را پاک نمی‌کند")
    assertNull(row.profitMinor, "بی هیچ بها ⇒ null، نه صفر")
    assertEquals(1, row.costMissingLines)
    assertEquals(150L, r.promoDiscountMinor, "برچسبِ بی‌شناسه شمرده نمی‌شود")
  }

  @Test
  fun basisPointsAreExactWithoutOverflow() {
    assertEquals(3_333L, PromoEffect.bp(1L, 3L))
    assertEquals(6_667L, PromoEffect.bp(2L, 3L))
    assertEquals(5_000L, PromoEffect.bp(1L, 2L))
    assertEquals(10_000L, PromoEffect.bp(7L, 7L))
    assertEquals(0L, PromoEffect.bp(0L, 9L))
    assertNull(PromoEffect.bp(5L, 0L))
    //  مبلغِ نزدیکِ ۲^۵۳: part × 10000 سرریز می‌کرد
    val big = 9_007_199_254_740_991L
    assertEquals(5_000L, PromoEffect.bp(big / 2 + 1, big))
    assertEquals(1L, PromoEffect.bp(big / 10_000 / 2 + 1, big))
    assertFailsWith<ArithmeticException> { PromoEffect.of(listOf(EffectLine("s", PromoEffect.MAX_QTY_MILLI + 1, netMinor = 1L))) }
    assertFailsWith<ArithmeticException> {
      PromoEffect.of(listOf(EffectLine("a", 1_000L, netMinor = big, promos = listOf(tag(oil, 1L))), EffectLine("b", 1_000L, netMinor = big)))
    }
  }

  @Test
  fun emptyInput() {
    val r = PromoEffect.of(emptyList())
    assertEquals(emptyList<PromoEffectRow>(), r.rows)
    assertEquals(0L, r.totalRevenueMinor)
    assertEquals(0L, r.promoRevenueMinor)
    assertEquals(0L, r.promoDiscountMinor)
  }
}
