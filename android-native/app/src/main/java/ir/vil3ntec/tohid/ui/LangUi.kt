package ir.vil3ntec.tohid.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  زبانِ صفحه در ترکیب (Compose).
 *
 *  منبعِ اصلی `LocaleChoice.lang` است؛ این فقط آن را برای صفحه‌ها در دسترس
 *  می‌گذارد تا تصمیمِ چیدمان (مثلاً فلشِ «برگشت») از آن خوانده شود.
 *  نوشته‌ها (`Str*`) مستقیم همان `LocaleChoice.lang` را می‌خوانند.
 */
val LocalLang = compositionLocalOf { Lang.FA }

/** فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست */
fun directionOf(lang: Lang): LayoutDirection = if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

/**
 *  ریشهٔ هر پنجره: زبان و جهتِ همان زبان. جای `LayoutDirection.Rtl` ِ ثابتِ
 *  پیشین نشسته است؛ با فارسی (پیش‌فرض) دقیقاً همان راست‌به‌چپ است.
 */
@Composable
fun ProvideLang(content: @Composable () -> Unit) {
  val lang = LocaleChoice.lang
  CompositionLocalProvider(
    LocalLang provides lang,
    LocalLayoutDirection provides directionOf(lang),
    content = content,
  )
}

/** فارسی / English / پښتو — هر گزینه به خطِ خودش، تا کسی که زبان را اشتباه زده راهِ برگشت را بشناسد */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
  val context = LocalContext.current
  SegmentedChoice(
    options = Lang.entries.map { it.code to it.native },
    selected = LocaleChoice.lang.code,
    onSelect = { LocaleChoice.setLang(context, Lang.of(it)) },
    modifier = modifier,
  )
}

/** تنظیمات ← «زبان»: اولین بخش، بی بازکردنِ دوبارهٔ برنامه */
@Composable
fun LanguageSection() {
  SettingsSection(
    icon = Icons.Filled.Language,
    title = StrSettings.language,
    subtitle = LocaleChoice.lang.native,
    tint = Shop.colors.primary,
    stateKey = "settings-language",
  ) {
    Text(
      StrSettings.languageNote,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    LanguagePicker()
  }
}
