package ir.vil3ntec.tohid.core.i18n

/**
 *  TEN-04 / ۲-۹ — نوارِ «پشتیبانی در حالِ مشاهده» و برگهٔ «دسترسیِ پشتیبانی» ِ صاحبِ دکان (`SupportAccessNotice.kt`).
 *  قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrSupportAccess {
  fun watching(minutes: Any): String = t(
    "پشتیبانی همین حالا دادهٔ دکان را می‌بیند — ${asIs(minutes)} دقیقهٔ دیگر",
    "Support is viewing your shop's data now — $minutes more minutes",
    "ملاتړ همدا اوس د دوکان معلومات ګوري — $minutes نورې دقیقې",
  )
  fun who(name: Any, reason: Any, scope: Any): String = t(
    "«${asIs(name)}» برای «${asIs(reason)}» · ${asIs(scope)}",
    "\"$name\" for \"$reason\" · $scope",
    "«$name» د «$reason» لپاره · $scope",
  )
  val breakGlass: String get() = t(
    "بی رضایتِ شما و فقط از سوی مالکِ پلتفرم باز شده و سرِ وقت خودش بسته می‌شود؛ اگر خبر ندارید به پشتیبانی بنویسید.",
    "Opened without your consent, only by the platform owner, and closes by itself on time; if you don't know why, write to support.",
    "ستاسو له رضایت پرته یوازې د پلاتفورم خاوند پرانیستی او پر خپل وخت پخپله تړل کېږي؛ که خبر نه یاست ملاتړ ته ولیکئ.",
  )
  val close: String get() = t("بستنِ دسترسی", "Close access", "لاسرسی تړل")
  val closed: String get() = t("دسترسیِ پشتیبانی بسته شد", "Support access closed", "د ملاتړ لاسرسی وتړل شو")
  val request: String get() = t("پشتیبانی اجازهٔ دیدنِ دادهٔ دکان را می‌خواهد", "Support asks to view your shop's data", "ملاتړ د دوکان د معلوماتو د کتلو اجازه غواړي")
  fun requestDetail(name: Any, reason: Any, minutes: Any): String = t(
    "«${asIs(name)}» برای «${asIs(reason)}»، ${asIs(minutes)} دقیقه؛ تا نپذیرید هیچ دسترسی‌ای باز نمی‌شود.",
    "\"$name\" for \"$reason\", $minutes minutes; nothing opens until you accept.",
    "«$name» د «$reason» لپاره، $minutes دقیقې؛ تر څو ونه منئ هېڅ لاسرسی نه پرانیستل کېږي.",
  )
  val answer: String get() = t("دیدن و پاسخ", "View and answer", "کتل او ځواب")
  val accept: String get() = t("پذیرفتن", "Accept", "منل")
  val deny: String get() = t("رد", "Deny", "ردول")
  val accepted: String get() = t("دسترسیِ پشتیبانی باز شد", "Support access opened", "د ملاتړ لاسرسی پرانیستل شو")
  val denied: String get() = t("درخواستِ پشتیبانی رد شد", "Support request denied", "د ملاتړ غوښتنه رد شوه")
  val title: String get() = t("دسترسیِ پشتیبانی", "Support access", "د ملاتړ لاسرسی")
  val intro: String get() = t(
    "پشتیبانی فقط با پذیرفتنِ شما و تا زمانِ گفته‌شده دادهٔ دکان را می‌بیند؛ هر بار دیدن در سابقه می‌ماند و هر وقت بخواهید می‌بندید.",
    "Support sees your shop's data only after you accept and only for the stated time; every view is logged and you can close it any time.",
    "ملاتړ یوازې ستاسو له منلو وروسته او د ویل شوي وخت لپاره د دوکان معلومات ګوري؛ هره کتنه ثبتېږي او هر وخت یې تړلای شئ.",
  )
  val nothing: String get() = t("هیچ درخواست یا دسترسیِ بازی نیست", "No open request or access", "هېڅ پرانیستې غوښتنه یا لاسرسی نشته")
  fun uses(n: Any): String = t("${asIs(n)} بار دیده شد", "Viewed $n times", "$n ځله وکتل شو")
  val scopeReadPrivate: String get() = t("دیدنِ دادهٔ خصوصی", "view private data", "د شخصي معلوماتو کتل")
  val scopeExport: String get() = t("گرفتنِ پشتیبان", "download a backup", "د بیک اپ اخیستل")
  val scopeRestore: String get() = t("بازگرداندنِ پشتیبان", "restore a backup", "د بیک اپ بېرته راوړل")
  val scopeViewAs: String get() = t("دیدن از جای شما", "view as you", "ستاسو په ځای کتل")
}
