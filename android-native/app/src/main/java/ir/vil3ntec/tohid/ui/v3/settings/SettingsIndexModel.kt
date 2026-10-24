package ir.vil3ntec.tohid.ui.v3.settings

import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.ThemeChoice

/** حالِ همگام‌سازی برای ردیف و صفحهٔ همگام‌سازی */
internal enum class SyncState { OFF, OK, WAITING, FAILED }

/**
 *  آنچه ردیف‌های تنظیمات نشان می‌دهند — یک بار از موتورها خوانده می‌شود
 *  (`rememberSettingsFacts`) و این‌جا فقط به نوشته تبدیل می‌شود.
 */
internal data class SettingsFacts(
  val debtLimit: Double = 0.0,
  val theme: ThemeChoice = ThemeChoice.SYSTEM,
  val lang: Lang = Lang.FA,
  val digits: String = LocaleFormat.DIGITS_LATN,
  val symbol: String = LocaleFormat.SYMBOL_WORD,
  val lockOn: Boolean = false,
  val signedIn: Boolean = false,
  /** شمارِ دستگاه‌های وارد شده؛ null = هنوز خوانده نشده */
  val sessions: Int? = null,
  val blockExpired: Boolean = false,
  val printer: String? = null,
  val cameraGranted: Boolean = false,
  val sync: SyncState = SyncState.OFF,
  val lastSyncOk: Long = 0,
  val now: Long = 0,
  val version: String = "",
)

/** ردیف‌های صفحهٔ تنظیمات: مقدارِ خاکستریِ پایانِ هر ردیف — خالص، آزمون‌پذیر */
internal object SettingsIndexModel {

  fun themeLabel(t: ThemeChoice): String = when (t) {
    ThemeChoice.LIGHT -> StrSettings.light
    ThemeChoice.DARK -> StrSettings.dark
    ThemeChoice.SYSTEM -> StrSettings.auto
  }

  fun digitsLabel(digits: String): String =
    if (digits == LocaleFormat.DIGITS_FA) StrSettings.persianDigits else StrSettings.latinDigits

  fun symbolLabel(symbol: String): String =
    if (symbol == LocaleFormat.SYMBOL_SIGN) LocaleFormat.AFN_SIGN else StrSettings.afghaniWord

  /** «۲ دقیقه پیش» به زبانِ صفحه */
  fun since(at: Long, now: Long): String {
    if (at <= 0) return StrSettings.notSyncedYet
    val minutes = ((now - at).coerceAtLeast(0) / 60_000L)
    return when {
      minutes < 1 -> StrNav.justNow
      minutes < 60 -> StrNav.minutesAgo(minutes)
      minutes < 24 * 60 -> StrNav.hoursAgo(minutes / 60)
      else -> StrNav.daysAgo(minutes / (24 * 60))
    }
  }

  fun value(key: String, f: SettingsFacts): String? = when (key) {
    "هشدارها" -> if (f.debtLimit > 0) StrSettings.debtFrom(money(f.debtLimit)) else StrSettings.off
    "ظاهر" -> themeLabel(f.theme)
    "زبان" -> f.lang.native
    "تاریخ و رقم" -> digitsLabel(f.digits) + " · " + symbolLabel(f.symbol)
    "قفل برنامه" -> null
    "دستگاه‌های وارد شده" -> if (!f.signedIn) StrSettings.notSignedIn else f.sessions?.toString()
    "تغییر رمز حساب" -> if (!f.signedIn) StrSettings.notSignedIn else null
    "کالای منقضی" -> if (f.blockExpired) StrSettings.notSold else StrSettings.warnOnly
    SettingsLayout.PRINTER -> f.printer ?: StrSettings.noPrinter
    SettingsLayout.CAMERA -> if (f.cameraGranted) StrSettings.ready else StrSettings.noPermission
    "همگام‌سازی" -> if (f.sync == SyncState.OFF) StrSettings.notSignedIn else since(f.lastSyncOk, f.now)
    SettingsLayout.ABOUT_APP -> f.version
    else -> null
  }

  /** ردیفی که کلیدِ روشن/خاموشِ خودش را دارد (r5) */
  fun toggle(key: String, f: SettingsFacts): Boolean? = if (key == "قفل برنامه") f.lockOn else null

  /** از روی حالِ موتورها: بیرون از حساب خاموش؛ خطا، در انتظار، یا همه‌چیز نشسته */
  fun syncState(signedIn: Boolean, failed: Boolean, waiting: Boolean): SyncState = when {
    !signedIn -> SyncState.OFF
    failed -> SyncState.FAILED
    waiting -> SyncState.WAITING
    else -> SyncState.OK
  }
}
