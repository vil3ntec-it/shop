package ir.vil3ntec.tohid.ui.v3.settings.sections

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang

/**
 *  زبانِ صفحه برای انتخاب‌گرِ «زبان» — رابطِ موقت تا پایه `LocaleChoice.lang` و
 *  `ProvideLang` را بیاورد (همان #348). همان کلیدِ ذخیره (`tohid-locale` ⇐ `lang`)
 *  را می‌نویسد و `I18n.source` را عوض می‌کند، پس نوشته‌ها همان لحظه عوض می‌شوند؛
 *  وقتی پایه رسید، `lang` و `set` یک خط می‌شوند: `LocaleChoice.lang` / `setLang`.
 */
internal object AppLanguage {
  private const val PREFS = "tohid-locale"
  private const val KEY = "lang"

  private val chosen = mutableStateOf<Lang?>(null)

  /** زبانی که همین حالا روی صفحه است */
  val lang: Lang get() = chosen.value ?: I18n.lang

  fun set(context: Context, value: Lang) {
    chosen.value = value
    I18n.source = { chosen.value ?: Lang.FA }
    runCatching { context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value.code).apply() }
  }
}
