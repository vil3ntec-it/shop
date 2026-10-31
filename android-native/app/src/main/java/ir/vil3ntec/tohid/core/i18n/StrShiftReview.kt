package ir.vil3ntec.tohid.core.i18n

/**
 *  بررسیِ اختلافِ صندوق به دستِ مدیر (PAY-14) در «سابقهٔ صندوق» ِ صندوقِ شیفت.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pay/ShiftReview` (commonMain).
 */
@Suppress("unused")
object StrShiftReview {
  val pending: String get() = t("اختلاف — نیازمند بررسیِ مدیر", "Difference — needs manager review", "توپیر — د مدیر کتنې ته اړتیا لري")
  fun reviewed(by: Any, at: Any): String = t("بررسی شد — $by، $at", "Reviewed — $by, $at", "وکتل شو — $by، $at")
  val action: String get() = t("ثبتِ بررسی", "Record review", "کتنه ثبت کړئ")
  val title: String get() = t("بررسیِ اختلافِ صندوق", "Review register difference", "د صندوق د توپیر کتنه")
  val body: String get() = t(
    "اختلاف عوض نمی‌شود؛ فقط بررسیِ شما با نام و زمان در سابقه می‌ماند.",
    "The difference is not changed; only your review is kept in the history with your name and time.",
    "توپیر نه بدلېږي؛ یوازې ستاسو کتنه د نوم او وخت سره په تاریخچه کې پاتې کېږي.",
  )
  val note: String get() = t("چه پیدا شد / چه شد؟", "What was found / done?", "څه وموندل شول / څه وشول؟")
  val save: String get() = t("ثبت", "Save", "ثبت")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val done: String get() = t("بررسی ثبت شد", "Review recorded", "کتنه ثبت شوه")
  val notSaved: String get() = t("ذخیره نشد", "Not saved", "خوندي نه شو")
  val errNoteRequired: String get() = t("یادداشتِ بررسی لازم است (دستِ کم ۳ نویسه)", "A review note is required (at least 3 characters)", "د کتنې یادښت اړین دی (لږ تر لږه ۳ توري)")
  val errNoteTooLong: String get() = t("یادداشت بیش از ۳۰۰ نویسه است", "The note is longer than 300 characters", "یادښت له ۳۰۰ تورو اوږد دی")
  val errSelf: String get() = t("بستنِ خودتان را مدیرِ دیگر یا صاحب بررسی می‌کند", "Another manager or the owner reviews your own close", "خپل تړل بل مدیر یا خاوند کتلی شي")
  val errAlready: String get() = t("این بستن پیش‌تر بررسی شده است", "This close has already been reviewed", "دا تړل مخکې کتل شوی")
  val errRole: String get() = t("فقط صاحب یا مدیر بررسی می‌کند", "Only the owner or a manager can review", "یوازې خاوند یا مدیر کتلی شي")
  val errOther: String get() = t("این بستن بررسی نمی‌خواهد", "This close does not need a review", "دا تړل کتنې ته اړتیا نه لري")
}
