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
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.Str

/**
 *  «تاریخ و رقم» در تنظیمات — رقمِ صفحه و فاکتور (LOC-05)، زبانِ نامِ ماه‌ها
 *  (LOC-01)، پایانِ روزِ کاری (LOC-03) و نمادِ پولِ فاکتور (LOC-07). پیش‌فرض‌ها رفتارِ
 *  امروز است: لاتین، دری، نیمه‌شب، «افغانی».
 *
 *  برچسبِ گزینه‌ها عمداً بی رقم است: وقتی رقمِ فارسی روشن است، فونت هر
 *  «123»ی را «۱۲۳» می‌کشد و نمونهٔ «لاتین» هم فارسی دیده می‌شد.
 */
@Composable
fun LocaleSection() {
  val context = LocalContext.current
  val digits = LocaleChoice.digits
  val months = LocaleChoice.months
  val symbol = LocaleChoice.symbol
  val lang = LocaleChoice.lang
  val calendar = LocaleChoice.calendar
  val gregorian = I18n.gregorian(lang, calendar)
  SettingsSection(
    icon = Icons.Filled.Event,
    title = Str.dateAndDigits,
    //  فارسی با پیش‌فرض‌ها: «رقمِ لاتین · ماهِ دری» — همان زیرعنوانِ پیشین
    subtitle = listOfNotNull(
      if (lang == Lang.EN) null else if (digits == LocaleFormat.DIGITS_FA) StrSettings.persianDigits else StrSettings.latinDigits,
      when {
        gregorian -> StrSettings.calGregorian
        lang == Lang.FA -> if (months == LocaleText.MONTHS_PASHTO) StrSettings.pashtoMonths else StrSettings.dariMonths
        else -> StrSettings.calSolar
      },
    ).joinToString(" · "),
    tint = Shop.colors.primary,
  ) {
    Text(
      StrSettings.digitsNote,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    //  انگلیسی همیشه رقمِ لاتین دارد؛ کلیدی که این‌جا کاری نمی‌کند نشان داده نمی‌شود
    if (lang == Lang.EN) {
      Text(StrSettings.digitsEnNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    } else {
      SegmentedChoice(
        options = listOf(LocaleFormat.DIGITS_LATN to StrSettings.latinDigits, LocaleFormat.DIGITS_FA to StrSettings.persianDigits),
        selected = digits,
        onSelect = { LocaleChoice.setDigits(context, it) },
      )
    }
    Spacer(Modifier.height(14.dp))
    Text(
      StrSettings.calNote,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf(
        I18n.CAL_AUTO to StrSettings.calAuto,
        I18n.CAL_SOLAR to StrSettings.calSolar,
        I18n.CAL_GREGORIAN to StrSettings.calGregorian,
      ),
      selected = calendar,
      onSelect = { LocaleChoice.setCalendar(context, it) },
    )
    //  LOC-03: پایانِ روزِ کاری — پیش‌فرض نیمه‌شب (هیچ تاریخی جابه‌جا نمی‌شود)
    Spacer(Modifier.height(14.dp))
    ShopDayRow(ShopDayChoice.hours) { ShopDayChoice.set(context, it) }
    //  نامِ ماه فقط در فارسی انتخابی است: پشتو همیشه وری، غویی… و انگلیسی Hamal, Sawr…
    Spacer(Modifier.height(14.dp))
    Text(
      StrSettings.monthsToday(I18n.dateLong(todayIso(), lang, I18n.CAL_SOLAR, months)),
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    if (lang == Lang.FA) {
      Spacer(Modifier.height(8.dp))
      SegmentedChoice(
        options = listOf(LocaleText.MONTHS_DARI to StrSettings.monthsDari, LocaleText.MONTHS_PASHTO to StrSettings.monthsPashto),
        selected = months,
        onSelect = { LocaleChoice.setMonths(context, it) },
      )
    }
    Spacer(Modifier.height(14.dp))
    Text(
      StrSettings.symbolNote,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf(LocaleFormat.SYMBOL_WORD to Str.afghani, LocaleFormat.SYMBOL_SIGN to "؋"),
      selected = symbol,
      onSelect = { LocaleChoice.setSymbol(context, it) },
    )
  }
}
