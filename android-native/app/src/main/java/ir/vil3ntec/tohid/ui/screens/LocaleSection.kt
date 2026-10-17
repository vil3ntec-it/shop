package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
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
 *  «تاریخ و رقم» در تنظیمات — رقمِ صفحه و فاکتور (LOC-05) و زبانِ نامِ ماه‌ها
 *  (LOC-01). پیش‌فرض‌ها رفتارِ امروز است: لاتین و دری.
 *
 *  برچسبِ گزینه‌ها عمداً بی رقم است: وقتی رقمِ فارسی روشن است، فونت هر
 *  «123»ی را «۱۲۳» می‌کشد و نمونهٔ «لاتین» هم فارسی دیده می‌شد.
 */
@Composable
fun LocaleSection() {
  val context = LocalContext.current
  val digits = LocaleChoice.digits
  val months = LocaleChoice.months
  SettingsSection(
    icon = Icons.Filled.Event,
    title = "تاریخ و رقم",
    subtitle = (if (digits == LocaleFormat.DIGITS_FA) "رقمِ فارسی" else "رقمِ لاتین") +
      " · " + (if (months == LocaleText.MONTHS_PASHTO) "ماهِ پشتو" else "ماهِ دری"),
    tint = Shop.colors.primary,
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
