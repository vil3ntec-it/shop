package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  «زبان» در تنظیمات (گوشی نسخهٔ ۳، گروهِ «عمومی») — زبانِ نامِ ماه‌های خورشیدی
 *  (LOC-01): دری یا پشتو. تنها انتخابِ زبانی که برنامه دارد؛ پیش‌تر درونِ
 *  «تاریخ و رقم» بود و فقط جایش عوض شد. پیش‌فرض همان دری است.
 */
@Composable
fun LanguageSection() {
  val context = LocalContext.current
  val months = LocaleChoice.months
  SettingsSection(
    icon = Icons.Filled.Language,
    title = "زبان",
    subtitle = "زبانِ نامِ ماه‌ها در تاریخ‌ها و فاکتور",
    tint = Shop.colors.primary,
    value = if (months == LocaleText.MONTHS_PASHTO) "پشتو" else "دری",
  ) {
    Text(
      "نامِ ماه‌های خورشیدی: امروز " + SolarHijri.formatLong(todayIso(), months, LocaleFormat.DIGITS_LATN),
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf(LocaleText.MONTHS_DARI to "دری (حمل، ثور…)", LocaleText.MONTHS_PASHTO to "پشتو (وری، غویی…)"),
      selected = months,
      onSelect = { LocaleChoice.setMonths(context, it) },
    )
  }
}

/**
 *  «تاریخ و رقم» در تنظیمات — رقمِ صفحه و فاکتور (LOC-05) و نمادِ پولِ فاکتور
 *  (LOC-07). پیش‌فرض‌ها رفتارِ امروز است: لاتین و «افغانی». زبانِ نامِ ماه‌ها
 *  (LOC-01) در بخشِ «زبان» است (`LanguageSection`).
 *
 *  برچسبِ گزینه‌ها عمداً بی رقم است: وقتی رقمِ فارسی روشن است، فونت هر
 *  «123»ی را «۱۲۳» می‌کشد و نمونهٔ «لاتین» هم فارسی دیده می‌شد.
 */
@Composable
fun LocaleSection() {
  val context = LocalContext.current
  val digits = LocaleChoice.digits
  val symbol = LocaleChoice.symbol
  SettingsSection(
    icon = Icons.Filled.Event,
    title = "تاریخ و رقم",
    subtitle = (if (digits == LocaleFormat.DIGITS_FA) "رقمِ فارسی" else "رقمِ لاتین") +
      " · " + (if (symbol == LocaleFormat.SYMBOL_SIGN) "نمادِ ؋" else "«افغانی»"),
    tint = Shop.colors.primary,
    value = if (digits == LocaleFormat.DIGITS_FA) "رقمِ فارسی" else "رقمِ لاتین",
  ) {
    Text(
      "رقم روی صفحه و فاکتورِ چاپی. چیزی که ذخیره و همگام می‌شود همیشه رقمِ لاتین است؛ با عوض کردنِ این کلید هیچ داده‌ای عوض نمی‌شود.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf(LocaleFormat.DIGITS_LATN to "رقمِ لاتین", LocaleFormat.DIGITS_FA to "رقمِ فارسی"),
      selected = digits,
      onSelect = { LocaleChoice.setDigits(context, it) },
    )
    Spacer(Modifier.height(14.dp))
    Text(
      "نمادِ پول در فاکتور. مبلغ همیشه بی اعشار است.",
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf(LocaleFormat.SYMBOL_WORD to "افغانی", LocaleFormat.SYMBOL_SIGN to "؋"),
      selected = symbol,
      onSelect = { LocaleChoice.setSymbol(context, it) },
    )
  }
}
