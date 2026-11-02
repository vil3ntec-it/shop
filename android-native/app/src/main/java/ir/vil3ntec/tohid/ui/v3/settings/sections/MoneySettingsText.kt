package ir.vil3ntec.tohid.ui.v3.settings.sections

import ir.vil3ntec.tohid.core.promo.Loyalty
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.normalizeDigits
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty
import kotlin.math.roundToLong

/*
 *  نوشته‌های کادرهای صفحه‌های پول و صندوقِ نسخهٔ ۳ — خالص و آزمون‌پذیر (`MoneySettingsTextTest`)، و درست همان
 *  خواندنِ بخش‌های رابطِ امروز تا هر دو رابط یک قاعده بنویسند.
 */

/**
 *  کادرهای قاعدهٔ وفاداری (PRM-13) — همان `LoyaltySettingsWired`: کادر فقط رقم و تا ۱۳ رقم، رقمِ فارسی لاتین می‌شود،
 *  خالی یا نادرست ⇒ ۰. درستیِ قاعده را خودِ `LoyaltyPref.setRule` می‌سنجد (نادرست ⇒ هیچ نوشته نمی‌شود).
 */
internal object LoyaltyRuleText {
  fun field(text: String): String = text.filter { it.isDigit() }.take(13)

  fun rule(every: String, point: String, share: String, silver: String, gold: String): Loyalty.Rule = Loyalty.Rule(
    everyAfn = normalizeDigits(every).toLongOrNull() ?: 0,
    pointAfn = normalizeDigits(point).toLongOrNull() ?: 0,
    maxSharePct = normalizeDigits(share).toIntOrNull() ?: 0,
    silverAfn = normalizeDigits(silver).toLongOrNull() ?: 0,
    goldAfn = normalizeDigits(gold).toLongOrNull() ?: 0,
  )
}

/**
 *  کادرهای «گزارشِ مالیاتِ سه‌ماهه» (RPT-10) — همان فرمِ `TaxQuarterSettingsWired`: نرخِ خالی ⇒ ۰ (بی نرخ)، نرخِ
 *  نادرست ⇒ null و ذخیره بسته؛ عددِ نادرستِ سررسید و یادآور ⇒ همان مقدارِ پیشین؛ و پیش از نوشتن `clamped()`.
 */
internal object TaxQuarterText {
  fun rateField(rateBp: Int): String = if (rateBp == 0) "" else QuarterTax.rateText(rateBp)

  fun rateBp(rate: String): Int? = if (rate.isBlank()) 0 else QuarterTax.parseRate(rate)

  private fun int(s: String): Int? = normalizeDigits(s).trim().toIntOrNull()

  fun config(start: Int, rate: String, dueDay: String, dueMonths: String, remind: String, cfg: QuarterTax.Config): QuarterTax.Config? {
    val bp = rateBp(rate) ?: return null
    return QuarterTax.Config(
      startMonth = start,
      rateBp = bp,
      dueDay = int(dueDay) ?: cfg.dueDay,
      dueMonths = int(dueMonths) ?: cfg.dueMonths,
      remindDays = int(remind) ?: cfg.remindDays,
    ).clamped()
  }
}

/**
 *  کادرِ «سقفِ ردیفِ رایگان» (POS-08/10/20/21) — همان `LinePricingSettings`: خالی ⇒ بی سقف (null نوشته می‌شود)،
 *  عددِ نامنفی ⇒ پول (×۱۰۰)، نوشتهٔ نادرست یا منفی ⇒ هیچ نوشته نمی‌شود و آخرین مقدارِ درست می‌ماند.
 */
internal object LinePricingText {
  sealed interface Cap {
    object Skip : Cap
    data class Write(val minor: Long?) : Cap
  }

  fun capField(minor: Long?): String = minor?.let { qty(it / 100.0) }.orEmpty()

  fun cap(text: String): Cap = when {
    text.isBlank() -> Cap.Write(null)
    else -> parseNumber(text)?.takeIf { it >= 0 && it.isFinite() }?.let { Cap.Write((it * 100).roundToLong()) } ?: Cap.Skip
  }
}
