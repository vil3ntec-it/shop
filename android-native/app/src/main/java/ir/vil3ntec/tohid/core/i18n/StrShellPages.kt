package ir.vil3ntec.tohid.core.i18n

/**
 *  جزءهای مشترکِ قدیمی که نسخهٔ ۳ هنوز صدا می‌زند: کادر و دکمه‌های پایه (`ui/screens/Tohid.kt`)، فهرستِ موردهای نوارِ پایین
 *  (`NavCatalog`)، قرصِ «تمدیدِ اشتراک» (`PhoneNavBar`)، صفحه‌های قدیمیِ گوشی که فقط پیش‌نمایشِ `DevShot` نشانشان می‌دهد
 *  (`Profile`، `Settings`، `VoiceSearch`) و گامِ کدِ شماره (`PhoneClaimStep`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند.
 *  فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrShellPages {
  // ── `Tohid.kt` ──
  val choosePlaceholder: String get() = t("— انتخاب کنید —", "— choose —", "— وټاکئ —")
  val tryAgain: String get() = t("تلاش دوباره", "Try again", "بیا هڅه")
  val loading: String get() = t("در حال بارگذاری", "Loading", "راځي")

  // ── `NavLayout.kt` ──

  // ── `PhoneNavBar.kt` ──
  val renewSubscription: String get() = t("تمدیدِ اشتراک", "Renew subscription", "ګډون نوي کول")
  val endsToday: String get() = t("امروز تمام می‌شود", "Ends today", "نن پای ته رسي")
  fun daysLeft(days: Any?): String = t("${asIs(days)} روز مانده", "$days days left", "$days ورځې پاتې")

  // ── `Profile.kt`، `Settings.kt`، `VoiceSearch.kt` (فقط پیش‌نمایشِ گوشیِ `DevShot`؛ نسخهٔ ۳ `ui/v3/account` و `ui/v3/settings` را دارد) ──
  val accountPhoto: String get() = t("عکسِ حساب", "Account photo", "د حساب انځور")
  val onThisPhoneOnly: String get() = td(
    "فقط روی همین گوشی می‌ماند",
    "Stays on this phone only", "یوازې په همدې تلیفون پاتې کېږي",
    "Stays on this computer only", "یوازې په همدې کمپیوټر پاتې کېږي",
  )
  val themeHint: String get() = td(
    "روشن، تیره، یا خودکار (مثل گوشی)",
    "Light, dark, or automatic (like the phone)", "روښانه، تیاره، یا خپلکاره (لکه تلیفون)",
    "Light, dark, or automatic (like the system)", "روښانه، تیاره، یا خپلکاره (لکه سیستم)",
  )
  val syncSignInFirst: String get() = t("برای همگام‌سازی اول وارد حساب شوید", "Sign in first to sync", "د همغږۍ لپاره لومړی حساب ته ننوځئ")
  fun syncBody(last: Any?): String = td(
    "دفترِ دکان روی سرور پشتیبان می‌گیرد و روی گوشیِ دیگر هم می‌آید. آخرین همگام‌سازی: ${asIs(last)}",
    "The shop ledger is backed up on the server and appears on your other phone too. Last sync: $last",
    "د دوکان دفتر په سرور بیک اپ کېږي او په بل تلیفون هم راځي. وروستۍ همغږي: $last",
    "The shop ledger is backed up on the server and appears on your other device too. Last sync: $last",
    "د دوکان دفتر په سرور بیک اپ کېږي او په بله وسیله هم راځي. وروستۍ همغږي: $last",
  )
  val syncNever: String get() = t("هنوز انجام نشده", "Not done yet", "لا نه ده شوې")
  val voiceSearch: String get() = t("جستجوی صوتی", "Voice search", "غږیز لټون")

  // ── `PhoneClaimStep.kt` (AUTH-08: گامِ کدِ شمارهٔ تازه؛ در نسخهٔ ۳ صدازننده ندارد) و همان کادر در `Profile.kt` ──
  val mobileOnce: String get() = t("شماره موبایل — یک بار ثبت می‌شود", "Mobile number — can be set only once", "د موبایل شمېره — یوازې یو ځل ثبتېږي")
  val smsCodeLabel: String get() = t("کدی که به همین شماره پیامک شد", "The code texted to this number", "هغه کوډ چې همدې شمېرې ته واستول شو")
  val smsCodeSent: String get() = t(
    "کدی به همین شماره پیامک شد؛ آن را بنویسید و «ذخیره» را بزنید",
    "A code was texted to this number; enter it and tap “Save”",
    "همدې شمېرې ته کوډ واستول شو؛ هغه ولیکئ او «خوندي کول» ووهئ",
  )
}
