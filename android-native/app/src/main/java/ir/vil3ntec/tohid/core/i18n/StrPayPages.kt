package ir.vil3ntec.tohid.core.i18n

/**
 *  حساب‌پیِ هر دکان (`ui/screens/HesabPayAccountSettings.kt`): وضعیتِ اتصال، رمزِ جدای حساب‌پی (گذاشتن، باز کردن، عوض کردن،
 *  بازنشانی)، حسابِ ساده (شماره/QR) و خودکار (کلید، رازِ وب‌هوک، شناسهٔ پذیرنده)، برداشتنِ حساب و پیشنهاد از اعلان. این نوشته‌ها
 *  پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۳). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله
 *  در جدولِ دیگری بود، همان دوباره به کار رفته. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrPayPages {
  // ── `HesabPayAccountSettings.kt` ──

  // وضعیت و خطاها
  val connectedSimple: String get() = t("وصل است — ساده", "Connected — simple", "وصل دی — ساده")
  val connectedAuto: String get() = t("وصل است — خودکار", "Connected — automatic", "وصل دی — اتومات")
  val pinFormat: String get() = t("رمزِ حساب‌پی باید ۶ رقم باشد.", "The HesabPay PIN must be 6 digits.", "د حساب‌پې رمز باید ۶ رقمه وي.")
  val pinWeak: String get() = t(
    "این رمز ساده است (همه یکسان یا پشتِ سرِ هم)؛ رمزِ دیگری بگذارید.",
    "This PIN is too simple (all the same or in sequence); choose another.",
    "دا رمز ساده دی (ټول یو شان یا پرله‌پسې)؛ بل رمز وټاکئ.",
  )
  val ownerOnly: String get() = t(
    "حساب‌پیِ دکان را فقط صاحبِ دکان می‌بیند و تنظیم می‌کند.",
    "Only the shop owner sees and sets up the shop's HesabPay.",
    "د دوکان حساب‌پې یوازې د دوکان خاوند ویني او تنظیموي.",
  )
  val notDoneRetry: String get() = t("انجام نشد؛ دوباره بکوشید.", "Didn't work; try again.", "ونه شو؛ بیا هڅه وکړئ.")
  val offline: String get() = t(
    "سرور در دسترس نیست؛ اینترنت را ببینید و دوباره بکوشید.",
    "The server isn't reachable; check the internet and try again.",
    "سرور ته لاسرسی نشته؛ انټرنېټ وګورئ او بیا هڅه وکړئ.",
  )

  // رمزِ حساب‌پی
  val ownerOnline: String get() = t(
    "فقط صاحبِ دکان، واردشده و با اینترنت، این بخش را می‌بیند.",
    "Only the shop owner, signed in and online, sees this section.",
    "دا برخه یوازې د دوکان خاوند ویني چې ننوتی وي او انټرنېټ ولري.",
  )
  val moneyDirect: String get() = t(
    "پولِ حساب‌پی مستقیم به حسابِ خودِ همین دکان می‌رود؛ هیچ حسابِ دیگری جایش نمی‌نشیند.",
    "HesabPay money goes straight to this shop's own account; no other account takes its place.",
    "د حساب‌پې پیسې مستقیم د همدې دوکان خپل حساب ته ځي؛ هېڅ بل حساب یې ځای نه نیسي.",
  )
  fun notMeLock(minutes: Any?): String = t(
    "پس از «این من نبودم»، بخشِ حساب‌پی تا ${asIs(minutes)} دقیقهٔ دیگر بسته است؛ پول به همان حسابِ قبلیِ دکان می‌رود. پس از آن رمزِ حساب‌پی و رمزِ برنامه را عوض کنید.",
    "After “This wasn't me”, the HesabPay section is closed for $minutes more ${pl(minutes ?: 0, "minute", "minutes")}; money goes to the shop's previous account. Then change the HesabPay PIN and the app password.",
    "له «دا زه نه وم» وروسته، د حساب‌پې برخه تر $minutes نورو دقیقو تړل شوې ده؛ پیسې د دوکان هماغه پخواني حساب ته ځي. وروسته د حساب‌پې رمز او د اپ رمز بدل کړئ.",
  )
  fun notTrusted(days: Any?): String = t(
    "این ورود هنوز برای عوض کردنِ حساب‌پی مطمئن نیست (دست‌کم ${asIs(days)} روز پس از ورود روی همین دستگاه، بی بیرون رفتن). دیدن شدنی است؛ برای تغییر از گوشی یا کمپیوترِ همیشگیِ خودتان بکوشید.",
    "This sign-in isn't trusted yet for changing HesabPay (at least $days ${pl(days ?: 0, "day", "days")} after signing in on this device, without signing out). Viewing works; to change it, use your usual phone or computer.",
    "دا ننوتنه لا د حساب‌پې د بدلولو لپاره باوري نه ده (لږ تر لږه $days ورځې پر همدې وسیله له ننوتو وروسته، بې له وتلو). لیدل کېدای شي؛ د بدلون لپاره له خپل تل والا موبایل یا کمپیوټر څخه هڅه وکړئ.",
  )
  val setPinNote: String get() = t(
    "برای این بخش یک رمزِ ۶ رقمیِ جدا بگذارید (غیر از رمزِ برنامه). بی این رمز هیچ‌کس حسابِ دکان را نمی‌بیند یا عوض نمی‌کند.",
    "Set a separate 6-digit PIN for this section (not the app password). Without it no one can see or change the shop's account.",
    "د دې برخې لپاره جلا ۶ رقمه رمز کېږدئ (د اپ له رمز پرته). بې له دې رمز هېڅوک د دوکان حساب نه ویني او نه یې بدلوي.",
  )
  fun resetHold(minutes: Any?): String = t(
    "پس از بازنشانیِ رمز، تا ${asIs(minutes)} دقیقهٔ دیگر حسابِ تازه پذیرفته نمی‌شود.",
    "After the PIN reset, a new account isn't accepted for $minutes more ${pl(minutes ?: 0, "minute", "minutes")}.",
    "د رمز له بیا جوړولو وروسته، تر $minutes نورو دقیقو نوی حساب نه منل کېږي.",
  )
  val newPinField: String get() = t("رمزِ تازهٔ حساب‌پی (۶ رقم)", "New HesabPay PIN (6 digits)", "د حساب‌پې نوی رمز (۶ رقمه)")
  val repeatPin: String get() = t("تکرارِ رمز", "Repeat PIN", "رمز بیا ولیکئ")
  val pinsDiffer: String get() = t("دو رمز یکی نیستند.", "The two PINs don't match.", "دواړه رمزونه یو شان نه دي.")
  val pinSet: String get() = t("رمزِ حساب‌پی گذاشته شد.", "HesabPay PIN set.", "د حساب‌پې رمز کېښودل شو.")
  val setPin: String get() = t("گذاشتنِ رمز", "Set PIN", "رمز ایښودل")
  fun pinLocked(minutes: Any?): String = t(
    "رمزِ حساب‌پی چند بار اشتباه زده شد؛ حدودِ ${asIs(minutes)} دقیقهٔ دیگر دوباره بکوشید.",
    "The HesabPay PIN was entered wrong several times; try again in about $minutes ${pl(minutes ?: 0, "minute", "minutes")}.",
    "د حساب‌پې رمز څو ځله غلط ووهل شو؛ شاوخوا $minutes دقیقې وروسته بیا هڅه وکړئ.",
  )
  val forgotPin: String get() = t("رمز را فراموش کرده‌ام", "I forgot the PIN", "رمز مې هېر کړی")
  val pinField: String get() = t("رمزِ حساب‌پی", "HesabPay PIN", "د حساب‌پې رمز")
  val newAccountIn24h: String get() = t(
    "حسابِ تازه ۲۴ ساعتِ دیگر فعال می‌شود؛ تا آن دم پول به همان حسابِ قبلی می‌رود.",
    "The new account becomes active in 24 hours; until then money goes to the previous account.",
    "نوی حساب ۲۴ ساعته وروسته فعالېږي؛ تر هغه پیسې هماغه پخواني حساب ته ځي.",
  )
  val accountSaved: String get() = t("حساب‌پیِ دکان ذخیره شد.", "Shop HesabPay saved.", "د دوکان حساب‌پې خوندي شو.")
  val holderNeeded: String get() = t(
    "نامِ صاحبِ حساب را بنویسید (همان که در صندوق کنارِ QR دیده می‌شود).",
    "Write the account holder's name (the one shown next to the QR at the till).",
    "د حساب د خاوند نوم ولیکئ (هغه چې په دخل کې د QR تر څنګ ښکاري).",
  )
  val accountBad: String get() = t(
    "شمارهٔ حساب‌پی درست نیست (مثلِ 0799123456).",
    "The HesabPay number isn't valid (like 0799123456).",
    "د حساب‌پې شمېره سمه نه ده (لکه 0799123456).",
  )
  val accountOrQr: String get() = t(
    "شمارهٔ حساب‌پیِ دکان یا QR ِ آن را بدهید.",
    "Give the shop's HesabPay number or its QR.",
    "د دوکان د حساب‌پې شمېره یا QR یې ورکړئ.",
  )
  val merchantNeeded: String get() = t(
    "شناسهٔ پذیرندهٔ حساب‌پیِ دکان را بنویسید.",
    "Write the shop's HesabPay merchant ID.",
    "د دوکان د حساب‌پې د منونکي پېژند ولیکئ.",
  )
  val newPinShort: String get() = t("رمزِ تازه (۶ رقم)", "New PIN (6 digits)", "نوی رمز (۶ رقمه)")
  val pinChanged: String get() = t("رمزِ حساب‌پی عوض شد.", "HesabPay PIN changed.", "د حساب‌پې رمز بدل شو.")
  val changePin: String get() = t("عوض کردنِ رمز", "Change PIN", "رمز بدلول")
  val changeHpPin: String get() = t("عوض کردنِ رمزِ حساب‌پی", "Change HesabPay PIN", "د حساب‌پې رمز بدلول")
  val resetPinTitle: String get() = t("بازنشانیِ رمزِ حساب‌پی", "Reset HesabPay PIN", "د حساب‌پې رمز بیا جوړول")
  val resetPinBody: String get() = t(
    "رمز و حسابِ حساب‌پیِ دکان هر دو پاک می‌شوند و تا ۲۴ ساعت حسابِ تازه پذیرفته نمی‌شود. به دستگاه‌های صاحبِ دکان هم خبر می‌رود.",
    "Both the shop's HesabPay PIN and account are cleared and no new account is accepted for 24 hours. The shop owner's devices are notified too.",
    "د دوکان د حساب‌پې رمز او حساب دواړه پاکېږي او تر ۲۴ ساعتو نوی حساب نه منل کېږي. د دوکان د خاوند وسیلو ته هم خبر ورکول کېږي.",
  )
  val pinReset: String get() = t("رمز و حسابِ حساب‌پی پاک شد.", "HesabPay PIN and account cleared.", "د حساب‌پې رمز او حساب پاک شول.")
  val removeAccount: String get() = t("برداشتنِ حساب‌پیِ دکان", "Remove the shop's HesabPay", "د دوکان حساب‌پې لرې کول")
  val removeAccountBody: String get() = t(
    "دکمهٔ حساب‌پی از صندوق برداشته می‌شود. رمزِ حساب‌پی می‌ماند.",
    "The HesabPay button is removed from the till. The HesabPay PIN stays.",
    "د حساب‌پې تڼۍ له دخل څخه لرې کېږي. د حساب‌پې رمز پاتې کېږي.",
  )
  val accountRemoved: String get() = t("حساب‌پیِ دکان برداشته شد.", "Shop HesabPay removed.", "د دوکان حساب‌پې لرې شو.")
  val qrUnreadable: String get() = t(
    "این QR خوانده نشد یا بلند است.",
    "This QR couldn't be read or is too long.",
    "دا QR ونه لوستل شو یا ډېر اوږد دی.",
  )
  val qrNoAccount: String get() = t(
    "در این QR شمارهٔ حساب‌پی نیست؛ شمارهٔ حساب را بنویسید.",
    "This QR has no HesabPay number; write the account number.",
    "په دې QR کې د حساب‌پې شمېره نشته؛ د حساب شمېره ولیکئ.",
  )
  val qrMismatch: String get() = t(
    "شمارهٔ درونِ این QR با شمارهٔ حساب یکی نیست.",
    "The number inside this QR doesn't match the account number.",
    "د دې QR دننه شمېره د حساب له شمېرې سره یو شان نه ده.",
  )

  // حسابِ ساده و خودکار
  val simple: String get() = t("ساده", "Simple", "ساده")
  val holderField: String get() = t(
    "نامِ صاحبِ حساب (در صندوق کنارِ QR دیده می‌شود)",
    "Account holder's name (shown next to the QR at the till)",
    "د حساب د خاوند نوم (په دخل کې د QR تر څنګ ښکاري)",
  )
  val newAccountNote: String get() = t(
    "حسابِ تازه ۲۴ ساعت پس از ذخیره فعال می‌شود و تا آن دم پول به همین حسابِ امروز می‌رود؛ به همهٔ دستگاه‌های صاحب خبر می‌رود.",
    "A new account becomes active 24 hours after saving and until then money goes to today's account; all the owner's devices are notified.",
    "نوی حساب له خوندي کولو ۲۴ ساعته وروسته فعالېږي او تر هغه پیسې همدې اوسني حساب ته ځي؛ د خاوند ټولو وسیلو ته خبر ورکول کېږي.",
  )
  val simpleNote: String get() = t(
    "شماره یا QR ِ حساب‌پیِ خودِ دکان. در صندوق همین QR با مبلغ و کدِ فاکتور نشان داده می‌شود و پول مستقیم به همین حساب می‌رود. صندوقدار فقط وقتی پول را در حساب دید «پول رسید» را می‌زند.",
    "The shop's own HesabPay number or QR. At the till this QR is shown with the amount and invoice code and money goes straight to this account. The cashier presses “Money received” only after seeing the money in the account.",
    "د دوکان د خپل حساب‌پې شمېره یا QR. په دخل کې همدا QR له مبلغ او د بل له کوډ سره ښودل کېږي او پیسې مستقیم همدې حساب ته ځي. صندوقدار یوازې هغه وخت «پیسې ورسېدې» وهي چې پیسې یې په حساب کې ولیدې.",
  )
  val accountField: String get() = t(
    "شمارهٔ حساب‌پیِ دکان (مثلِ 0799123456)",
    "Shop HesabPay number (like 0799123456)",
    "د دوکان د حساب‌پې شمېره (لکه 0799123456)",
  )
  val qrField: String get() = t("QR ِ حساب‌پیِ دکان (اختیاری)", "Shop HesabPay QR (optional)", "د دوکان د حساب‌پې QR (اختیاري)")
  val scanQr: String get() = t("اسکنِ QR ِ حساب‌پیِ دکان", "Scan the shop's HesabPay QR", "د دوکان د حساب‌پې QR سکن کړئ")
  val serverNotReady: String get() = t(
    "سرور هنوز برای حالتِ خودکار آماده نیست؛ از حالتِ ساده استفاده کنید.",
    "The server isn't ready for automatic mode yet; use simple mode.",
    "سرور لا د اتومات حالت لپاره چمتو نه دی؛ ساده حالت وکاروئ.",
  )
  val autoNotOn: String get() = t(
    "حالتِ خودکار برای این دکان هنوز روشن نشده؛ کلیدها ذخیره می‌شوند و با روشن شدن به کار می‌روند.",
    "Automatic mode isn't turned on for this shop yet; the keys are saved and used once it is.",
    "اتومات حالت لا د دې دوکان لپاره نه دی روښانه شوی؛ کلیګانې خوندي کېږي او د روښانه کېدو سره کارول کېږي.",
  )
  val autoNote: String get() = t(
    "کلید و رازِ پذیرندهٔ حساب‌پیِ خودِ دکان. پرداخت فقط وقتی خودکار تأیید می‌شود که حساب‌پی با امضای همین دکان کد و مبلغِ همان فاکتور را بفرستد. کلیدها پس از ذخیره دیگر نشان داده نمی‌شوند.",
    "The shop's own HesabPay merchant key and secret. A payment is confirmed automatically only when HesabPay sends that invoice's code and amount signed for this shop. The keys aren't shown again after saving.",
    "د دوکان د خپل حساب‌پې د منونکي کیلي او راز. تادیه یوازې هغه وخت اتومات تاییدېږي چې حساب‌پې د همدې دوکان په لاسلیک د هماغه بل کوډ او مبلغ ولېږي. کلیګانې له خوندي کولو وروسته بیا نه ښودل کېږي.",
  )
  fun currentKey(last4: Any?, merchant: Any?): String = t(
    "کلیدِ فعلی: …${asIs(last4)}${asIs(merchant)}",
    "Current key: …$last4$merchant",
    "اوسنۍ کیلي: …$last4$merchant",
  )
  fun webhookNote(url: Any?): String = t(
    "این نشانی را در داشبوردِ حساب‌پیِ دکان به‌عنوانِ وب‌هوک ثبت کنید: ${asIs(url)}",
    "Register this address as the webhook in the shop's HesabPay dashboard: $url",
    "دا پته د دوکان د حساب‌پې په ډشبورډ کې د وېب‌هوک په توګه ثبت کړئ: $url",
  )
  val apiKey: String get() = t("کلیدِ API", "API key", "API کیلي")
  val webhookSecret: String get() = t("رازِ وب‌هوک", "Webhook secret", "د وېب‌هوک راز")
  val merchantField: String get() = t(
    "شناسهٔ پذیرنده (لازم؛ از داشبوردِ حساب‌پی)",
    "Merchant ID (required; from the HesabPay dashboard)",
    "د منونکي پېژند (اړین؛ د حساب‌پې له ډشبورډ څخه)",
  )
  fun activeInHours(hours: Any?, detail: Any?): String = t(
    "حدودِ ${asIs(hours)} ساعتِ دیگر فعال می‌شود. ${asIs(detail)}",
    "Becomes active in about $hours ${pl(hours ?: 0, "hour", "hours")}. $detail",
    "شاوخوا $hours ساعته وروسته فعالېږي. $detail",
  )
  val notifSuggest: String get() = t(
    "پیشنهاد از اعلانِ حساب‌پی (فقط همین دستگاه)",
    "Suggest from the HesabPay notification (this device only)",
    "د حساب‌پې له خبرتیا وړاندیز (یوازې همدا وسیله)",
  )
  val notifNote: String get() = t(
    "وقتی اعلانِ «پول رسید» ِ برنامهٔ حساب‌پی آمد، صندوق فقط پیشنهاد می‌دهد؛ تأیید باز هم با «پول رسید» ِ صندوقدار است. پیامک خوانده نمی‌شود.",
    "When the HesabPay app's “Money received” notification arrives, the till only suggests; confirming is still the cashier's “Money received”. SMS isn't read.",
    "کله چې د حساب‌پې اپ د «پیسې ورسېدې» خبرتیا راشي، دخل یوازې وړاندیز کوي؛ تایید بیا هم د صندوقدار په «پیسې ورسېدې» دی. پیغام نه لوستل کېږي.",
  )
  val notifPermission: String get() = t(
    " اجازهٔ «دسترسی به اعلان» هنوز داده نشده.",
    " Permission for “Notification access” hasn't been given yet.",
    " د «خبرتیا ته لاسرسي» اجازه لا نه ده ورکړل شوې.",
  )
  val merchantSet: String get() = t(" · شناسهٔ پذیرنده دارد", " · has a merchant ID", " · د منونکي پېژند لري")
}
