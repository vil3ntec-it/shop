package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.core.report.QuarterTax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نوشته‌های کادرهای وفاداری و مالیاتِ سه‌ماههٔ نسخهٔ ۳ — همان خواندنِ `LoyaltySettingsWired` و `TaxQuarterSettingsWired` */
class MoneySettingsTextTest {

  @Test fun `کادرِ وفاداری فقط رقم و تا سیزده رقم می‌گیرد`() {
    assertEquals("1200", LoyaltyRuleText.field("1,200 ؋"))
    assertEquals("۱۲۰۰", LoyaltyRuleText.field("۱۲۰۰"))
    assertEquals("1234567890123", LoyaltyRuleText.field("12345678901234567"))
    assertEquals("", LoyaltyRuleText.field("abc"))
  }

  @Test fun `قاعدهٔ وفاداری رقمِ فارسی را لاتین می‌خواند و کادرِ خالی صفر است`() {
    assertEquals(
      Loyalty.Rule(everyAfn = 100, pointAfn = 2, maxSharePct = 30, silverAfn = 50_000, goldAfn = 200_000),
      LoyaltyRuleText.rule("۱۰۰", "2", "۳۰", "50000", "۲۰۰۰۰۰"),
    )
    val empty = LoyaltyRuleText.rule("", "", "", "", "")
    assertEquals(Loyalty.Rule(0, 0, 0, 0, 0), empty)
    //  قاعدهٔ نادرست را خودِ `LoyaltyPref.setRule` رد می‌کند — همین سنجشِ هسته
    assertFalse(empty.valid)
    assertTrue(LoyaltyRuleText.rule("100", "1", "20", "50000", "200000").valid)
    assertFalse("طلایی باید بالاتر از نقره‌ای باشد", LoyaltyRuleText.rule("100", "1", "20", "50000", "50000").valid)
  }

  @Test fun `نرخِ خالی بی نرخ است و نرخِ نادرست ذخیره را می‌بندد`() {
    assertEquals(0, TaxQuarterText.rateBp(""))
    assertEquals(0, TaxQuarterText.rateBp("  "))
    assertEquals(250, TaxQuarterText.rateBp("۲٫۵"))
    assertNull(TaxQuarterText.rateBp("abc"))
    assertNull(TaxQuarterText.rateBp("-1"))
    assertEquals("", TaxQuarterText.rateField(0))
    assertEquals("2.5", TaxQuarterText.rateField(250))
    val cfg = QuarterTax.Config(startMonth = 4, rateBp = 1000, dueDay = 15, dueMonths = 1, remindDays = 10)
    assertNull(TaxQuarterText.config(4, "abc", "15", "1", "10", cfg))
  }

  @Test fun `سررسیدِ نادرست همان مقدارِ پیشین می‌ماند و بیرون از بازه به مرز می‌رود`() {
    val cfg = QuarterTax.Config(startMonth = 4, rateBp = 1000, dueDay = 20, dueMonths = 1, remindDays = 7)
    assertEquals(
      QuarterTax.Config(startMonth = 10, rateBp = 500, dueDay = 20, dueMonths = 1, remindDays = 7),
      TaxQuarterText.config(10, "5", "x", "", " ", cfg),
    )
    //  رقمِ فارسی لاتین می‌شود؛ همان `clamped()` ِ هسته: روزِ ۹۹ ⇒ ۳۱
    val far = TaxQuarterText.config(1, "", "۹۹", "۰", "۵", cfg)
    assertEquals(QuarterTax.Config(startMonth = 1, rateBp = 0, dueDay = 31, dueMonths = 0, remindDays = 5), far)
    assertEquals(QuarterTax.Config(1, 0, 99, 0, 5).clamped(), far)
    assertEquals(cfg.copy(startMonth = 12).clamped(), TaxQuarterText.config(12, "10", "20", "1", "7", cfg))
  }

  @Test fun `سقفِ ردیفِ رایگان خالی بی سقف است و نوشتهٔ نادرست نوشته نمی‌شود`() {
    assertEquals(LinePricingText.Cap.Write(null), LinePricingText.cap(""))
    assertEquals(LinePricingText.Cap.Write(null), LinePricingText.cap("  "))
    assertEquals(LinePricingText.Cap.Write(150_000), LinePricingText.cap("1500"))
    assertEquals(LinePricingText.Cap.Write(1_250), LinePricingText.cap("12.5"))
    assertEquals(LinePricingText.Cap.Write(0), LinePricingText.cap("0"))
    assertEquals(LinePricingText.Cap.Skip, LinePricingText.cap("-5"))
    assertEquals(LinePricingText.Cap.Skip, LinePricingText.cap("abc"))
    assertEquals("", LinePricingText.capField(null))
    assertEquals("1500", LinePricingText.capField(150_000))
    assertEquals("12.5", LinePricingText.capField(1_250))
  }
}
