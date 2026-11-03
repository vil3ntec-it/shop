package ir.vil3ntec.tohid.core.storefront

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** S109: نامِ ولسوالیِ «نمایش در بازارچه» همان یکدست‌سازیِ سرور (`cleanDistrict` ِ `lib/af-provinces.js`). */
class MarketRegionRulesTest {

  @Test fun `S109 ولسوالی با ی و ک ِ عربی، نیم‌فاصله و فاصلهٔ اضافه یکدست می‌شود`() {
    assertEquals("انجیل", StorefrontRules.cleanDistrict("  انجيل "))
    assertEquals("ده سبز", StorefrontRules.cleanDistrict("ده‌سبز"))
    assertEquals("کرخ", StorefrontRules.cleanDistrict("كرخ\u0007"))
    assertEquals("پل خمری", StorefrontRules.cleanDistrict("پل   \t خمری"))
    assertEquals("", StorefrontRules.cleanDistrict(" \n "))
  }

  @Test fun `S109 ولسوالیِ بلندتر از شصت نویسه پیش از فرستادن خطا دارد`() {
    assertNull(StorefrontRules.districtError("ا".repeat(60)))
    assertNotNull(StorefrontRules.districtError("ا".repeat(61)))
    assertNull(StorefrontRules.districtError("  " + "ا".repeat(60) + "  "))
  }
}
