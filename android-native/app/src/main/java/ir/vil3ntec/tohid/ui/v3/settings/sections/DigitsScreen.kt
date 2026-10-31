package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsIndexModel
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/** مبلغِ نمونهٔ پیش‌نمایش */
private const val SAMPLE = 1250.0

private val DIGITS = listOf(LocaleFormat.DIGITS_LATN, LocaleFormat.DIGITS_FA)
private val SYMBOLS = listOf(LocaleFormat.SYMBOL_WORD, LocaleFormat.SYMBOL_SIGN)
private val CALENDARS = listOf(I18n.CAL_AUTO, I18n.CAL_SOLAR, I18n.CAL_GREGORIAN)

private fun calendarLabel(c: String): String = when (c) {
  I18n.CAL_SOLAR -> StrSettings.calSolar
  I18n.CAL_GREGORIAN -> StrSettings.calGregorian
  else -> StrSettings.calAuto
}

/**
 *  `settings/general/digits`: رقمِ صفحه و فاکتور (LOC-05)، تقویمِ نمایش، پایانِ روزِ کاری (LOC-03، همان
 *  `ShopDayChoice` ِ رابطِ امروز) و نمادِ پول (LOC-07)، با نمونهٔ زندهٔ مبلغ. چیزی که ذخیره و همگام می‌شود
 *  همیشه رقمِ لاتین است.
 */
@Composable
internal fun RouteScope.DigitsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val digits = LocaleChoice.digits
  val symbol = LocaleChoice.symbol
  SettingsPageFrame(title = SettingsLayout.labelOf("تاریخ و رقم"), onBack = onBack) {
    item(key = "sample") {
      SettingsBlock {
        val sample = LocaleFormat.formatAfghani(SAMPLE, symbol, digits)
        AnimatedContent(sample, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "digitsSample") {
          Text(it, style = V3Type.figure, color = PhoneV3.colors.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text(StrSettings.sampleCaption, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
      }
    }
    item(key = "digits") {
      SettingsBlock {
        V3Segmented(DIGITS.map(SettingsIndexModel::digitsLabel), DIGITS.indexOf(digits).coerceAtLeast(0), { LocaleChoice.setDigits(context, DIGITS[it]) })
      }
      SettingsNote(if (LocaleChoice.lang == Lang.EN) StrSettings.digitsEnNote else StrSettings.digitsNote)
    }
    item(key = "calendar") {
      SettingsBlock {
        V3Segmented(CALENDARS.map(::calendarLabel), CALENDARS.indexOf(LocaleChoice.calendar).coerceAtLeast(0), { LocaleChoice.setCalendar(context, CALENDARS[it]) })
      }
      SettingsNote(StrSettings.calNote)
    }
    //  LOC-03: پایانِ روزِ کاری — نیمه‌شب (پیش‌فرض، رفتارِ امروز) تا ۰۴:۰۰؛ روشن ⇒ تاریخی که فروشِ همین لحظه با آن ثبت می‌شود
    item(key = "dayEnd") {
      val hours = ShopDay.clamp(ShopDayChoice.hours)
      SettingsBlock {
        V3Segmented(ShopDay.CHOICES.map(ShopDay::label), ShopDay.CHOICES.indexOf(hours).coerceAtLeast(0), { ShopDayChoice.set(context, ShopDay.CHOICES[it]) })
      }
      SettingsNote(StrSettings.dayEndNote)
      if (hours != ShopDay.OFF) SettingsNote(StrSettings.dayEndNow(displayDate(todayIso())))
    }
    item(key = "symbol") {
      SettingsBlock {
        V3Segmented(SYMBOLS.map(SettingsIndexModel::symbolLabel), SYMBOLS.indexOf(symbol).coerceAtLeast(0), { LocaleChoice.setSymbol(context, SYMBOLS[it]) })
      }
      SettingsNote(StrSettings.symbolNote)
      Spacer(Modifier.height(8.dp))
    }
  }
}
