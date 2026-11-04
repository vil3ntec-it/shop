package ir.vil3ntec.tohid.core.i18n

/**
 *  صفحه‌خوان (TalkBack، VoiceOver، Narrator) در رابطِ نسخهٔ ۳: حالتِ «باز/بسته» ِ ردیف‌ها و کادرهای تاشو، نامِ کارِ
 *  نگه‌داشتن، نامِ QR ِ پرداخت، شمارِ «نیاز به توجه» ِ تب‌ها، ضبطِ صدا با زدن و جابه‌جاییِ ردیف‌های نوار (ممیزیِ
 *  دسترس‌پذیری، WCAG 2.1 AA). فقط برای صفحه‌خوان است و روی صفحه دیده نمی‌شود. فارسی، انگلیسی و پشتو؛
 *  دری = فارسی + لایهٔ `DariText`؛ هر مقدار از `asIs` می‌گذرد.
 */
@Suppress("unused")
object StrA11y {

  /** حالتِ کادر یا ردیفِ تاشو وقتی باز است */
  val expanded: String get() = t("باز", "Expanded", "پرانیستی")

  /** حالتِ کادر یا ردیفِ تاشو وقتی بسته است */
  val collapsed: String get() = t("بسته", "Collapsed", "تړلی")

  /** نامِ کارِ «نگه‌داشتن» ِ ردیف (برگهٔ کارها) */
  val moreActions: String get() = t("کارهای بیشتر", "More actions", "نورې کړنې")

  /** کادرِ QR ِ پرداخت در صندوق */
  val payQr: String get() = t("کدِ QR ِ پرداخت", "Payment QR code", "د ورکړې QR کوډ")

  /** تبی که شمارش کارِ فوری است («تازه، ۳، نیاز به توجه») — رنگ و نقطهٔ شمار برای صفحه‌خوان نوشته می‌شوند */
  fun tabNeedsAttention(tab: Any, n: Any): String =
    t("${asIs(tab)}، ${asIs(n)}، نیاز به توجه", "$tab, $n, needs attention", "$tab، $n، پاملرنې ته اړتیا")

  /* ── دکمهٔ صدای گفت‌وگو: جایگزینِ «نگه دارید و حرف بزنید» (`VoiceFlow`) ── */

  /** کارِ صفحه‌خوان: ضبط با زدن، تا «پایان» یا «لغو» */
  val recStart: String get() = t("شروعِ ضبط", "Start recording", "ثبت پیل کړئ")

  /** کارِ صفحه‌خوان در حالِ ضبط: ایستادن و فرستادن در یک کار */
  val recFinish: String get() = t("پایانِ ضبط و فرستادن", "Stop recording and send", "ثبت بند کړئ او ولېږئ")

  /** ناحیهٔ زنده: ضبط آغاز شد */
  val recOn: String get() = t("در حالِ ضبط", "Recording", "ثبتېږي")

  /** ناحیهٔ زنده: ضبط ایستاد و پیش از فرستادن دیده می‌شود */
  val recReady: String get() = t("ضبط تمام شد، آمادهٔ فرستادن", "Recording stopped, ready to send", "ثبت بند شو، د لېږلو لپاره چمتو دی")

  /** ناحیهٔ زنده: پیامِ صوتی رفت */
  val recSent: String get() = t("پیامِ صوتی فرستاده شد", "Voice message sent", "غږیز پیغام ولېږل شو")

  /** ناحیهٔ زنده: ضبط یا صدای آماده کنار رفت */
  val recCancelled: String get() = t("ضبط لغو شد", "Recording cancelled", "ثبت لغوه شو")

  /** ناحیهٔ زنده: «پایان و فرستادن» با صدای بزرگ‌تر از سقف */
  val recTooBig: String get() = t("پیامِ صوتی بزرگ‌تر از سقف است و فرستاده نشد", "Voice message is over the limit and was not sent", "غږیز پیغام له سقف لوی دی او ونه لېږل شو")

  /** ناحیهٔ زنده: زدنِ کوتاه، چیزی ضبط نشد */
  val recEmpty: String get() = t("چیزی ضبط نشد", "Nothing was recorded", "هېڅ ثبت نه شو")

  /* ── چیدمانِ نوارِ پایین در تنظیمات: جایگزینِ کشیدنِ ردیف (`NavLayouts.step`) ── */

  /** کارِ صفحه‌خوان روی ردیفِ نوار */
  val navUp: String get() = t("یک جا بالاتر", "Move up one place", "یو ځای پورته")

  /** کارِ صفحه‌خوان روی ردیفِ نوار */
  val navDown: String get() = t("یک جا پایین‌تر", "Move down one place", "یو ځای ښکته")

  /** جای ردیف، کوتاه (سرخطِ بخش شرحِ بلند را دارد) */
  val navStart: String get() = t("دایرهٔ شروع", "Start circle", "د پیل کړۍ")

  /** جای ردیف، کوتاه */
  val navEnd: String get() = t("دایرهٔ پایان", "End circle", "د پای کړۍ")

  /** جای ردیف در قرص: «قرص، جای ۲ از ۴» */
  fun navPill(n: Any, of: Any): String = t("قرص، جای ${asIs(n)} از ${asIs(of)}", "Pill, place $n of $of", "ګولۍ، $n ځای له $of")
}
