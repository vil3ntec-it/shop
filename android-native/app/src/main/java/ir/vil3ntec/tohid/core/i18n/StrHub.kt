package ir.vil3ntec.tohid.core.i18n

/**
 *  SYN-09/S29 — «هابِ شبکهٔ دکان» در «دستگاه‌ها»: کارتِ کامپیوتر (روشن/خاموش، جفت کردن، دستگاه‌ها) و کارتِ گوشی
 *  (پیدا کردن، کد یا QR، جدا شدن). فارسیِ معیار؛ رویهٔ دری جداست (`text/DariText.kt`). قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrHub {
  //  ── کامپیوتر ──────────────────────────────────────────────────────
  val title: String get() = t("هابِ شبکهٔ دکان", "Shop network hub", "د دوکان د شبکې هب")
  val caption: String get() = t(
    "وقتی اینترنت قطع است، گوشی‌های دکان کارهایشان را از راهِ همین کامپیوتر به هم می‌رسانند؛ با برگشتِ اینترنت هر کار فقط یک بار به سرور می‌رسد.",
    "When the internet is down, the shop's phones share their work through this computer; when it is back, each change reaches the server exactly once.",
    "کله چې انټرنېټ پرې وي، د دوکان ګوشۍ خپل کارونه د همدې کمپیوټر له لارې یو بل ته رسوي؛ د انټرنېټ په راتګ هر کار یوازې یو ځل سرور ته رسي.",
  )
  val start: String get() = t("روشن کردنِ هاب", "Turn the hub on", "هب چالانول")
  val stop: String get() = t("خاموش کردنِ هاب", "Turn the hub off", "هب بندول")
  val off: String get() = t("هاب خاموش است.", "The hub is off.", "هب بند دی.")
  fun on(address: String): String = t("هاب روشن است: ${asIs(address)}", "The hub is on: $address", "هب چالان دی: $address")
  val noAddress: String get() = t("این کامپیوتر به شبکهٔ دکان وصل نیست.", "This computer is not on the shop network.", "دا کمپیوټر د دوکان شبکې ته نه دی وصل.")
  fun fingerprint(pin: String): String = t("اثرِ انگشتِ گواهی: ${asIs(pin)}", "Certificate fingerprint: $pin", "د سند د ګوتې نښه: $pin")
  val notDiscoverable: String get() = t(
    "پیدا شدنِ خودکار در شبکه در دسترس نیست؛ گوشی را با QR جفت کنید.",
    "Automatic discovery is not available; pair phones with the QR code.",
    "په شبکه کې خپلکاری موندل نشته؛ ګوشۍ د QR له لارې جوړه کړئ.",
  )
  fun pending(n: Any): String = t("${asIs(n)} دسته در انتظارِ سرور", "$n ${pl(n, "batch", "batches")} waiting for the server", "$n ډلې د سرور په تمه")
  fun forwarded(n: Any): String = t("${asIs(n)} دسته به سرور رسید", "$n ${pl(n, "batch", "batches")} delivered to the server", "$n ډلې سرور ته ورسېدې")

  val pairTitle: String get() = t("جفت کردنِ گوشی", "Pair a phone", "ګوشۍ جوړه کول")
  val newPairing: String get() = t("کد و QR ِ تازه", "New code and QR", "نوی کوډ او QR")
  val cancelPairing: String get() = t("بستنِ کد", "Close the code", "کوډ بندول")
  val pairHint: String get() = t(
    "در گوشی «دستگاه‌ها ← هابِ کامپیوترِ دکان» را باز کنید و QR را بخوانید یا این کد را بزنید. کد فقط یک بار و تا ۲ دقیقه کار می‌کند.",
    "On the phone open “Devices → Shop computer hub” and scan the QR or type this code. The code works once, for 2 minutes.",
    "په ګوشۍ کې «وسیلې ← د دوکان د کمپیوټر هب» پرانیزئ او QR ولولئ یا دا کوډ ولیکئ. کوډ یوازې یو ځل او تر ۲ دقیقو کار کوي.",
  )
  fun expiresIn(seconds: Any): String = t("${asIs(seconds)} ثانیهٔ دیگر می‌سوزد", "Expires in $seconds s", "$seconds ثانیې وروسته ختمېږي")

  fun devicesTitle(n: Any): String = t("دستگاه‌های جفت‌شده (${asIs(n)})", "Paired devices ($n)", "جوړې شوې وسیلې ($n)")
  val noDevices: String get() = t("هنوز هیچ گوشی‌ای جفت نشده است.", "No phone is paired yet.", "تر اوسه هېڅ ګوشۍ نه ده جوړه شوې.")
  val deviceOnline: String get() = t("همین حالا وصل", "Connected now", "همدا اوس وصل")
  fun deviceSeen(at: String): String = t("آخرین بار: ${asIs(at)}", "Last seen: $at", "وروستی ځل: $at")
  val deviceNotSeen: String get() = t("از روشن شدنِ هاب دیده نشده", "Not seen since the hub started", "د هب له چالانېدو راهیسې نه دی لیدل شوی")
  val unpair: String get() = t("جدا کردن", "Unpair", "جلا کول")

  /** دلیلِ `HubPanel.Why` */
  fun why(code: String): String = when (code) {
    "flag_off" -> t("اول «چند سرور» را در «سرورها» روشن کنید.", "Turn on “Multiple servers” in Servers first.", "لومړی په «سرورونه» کې «څو سرورونه» چالان کړئ.")
    "no_server_id" -> t("سرورِ فعال هنوز شناسه ندارد؛ یک بار با اینترنت وصل شوید.", "The active server has no ID yet; connect once with the internet.", "فعال سرور تر اوسه پېژند نه لري؛ یو ځل له انټرنېټ سره وصل شئ.")
    "no_shop" -> t("این کامپیوتر هنوز دکان ندارد.", "This computer has no shop yet.", "دا کمپیوټر تر اوسه دوکان نه لري.")
    "no_key" -> t("کلیدِ امنِ این کامپیوتر در دسترس نیست.", "This computer's secure key is not available.", "د دې کمپیوټر خوندي کیلي نشته.")
    "port_busy" -> t("درگاهِ هاب را برنامهٔ دیگری گرفته است.", "Another program is using the hub's port.", "د هب دروازه بل پروګرام نیولې ده.")
    "not_running" -> t("اول هاب را روشن کنید.", "Turn the hub on first.", "لومړی هب چالان کړئ.")
    "role" -> t("فقط صاحب یا مدیرِ دکان می‌تواند گوشی را جفت کند.", "Only the shop owner or a manager can pair phones.", "یوازې د دوکان څښتن یا مدیر ګوشۍ جوړه کولی شي.")
    else -> t("انجام نشد (${asIs(code)})", "Not done ($code)", "ونه شو ($code)")
  }

  //  ── گوشی ──────────────────────────────────────────────────────────
  val joinTitle: String get() = t("هابِ کامپیوترِ دکان", "Shop computer hub", "د دوکان د کمپیوټر هب")
  val joinCaption: String get() = t(
    "بی اینترنت، کارهای این گوشی از راهِ کامپیوترِ دکان به گوشی‌های دیگر می‌رسد و با برگشتِ اینترنت یک بار به سرور.",
    "Without internet, this phone's work reaches the other phones through the shop computer, and the server once the internet is back.",
    "بې انټرنېټه، د دې ګوشۍ کارونه د دوکان د کمپیوټر له لارې نورو ګوشیو ته رسي او د انټرنېټ په راتګ یو ځل سرور ته.",
  )
  val codeLabel: String get() = t("کدِ شش‌رقمیِ کامپیوتر", "The computer's 6-digit code", "د کمپیوټر شپږ رقمي کوډ")
  val findAndJoin: String get() = t("پیدا کن و وصل شو", "Find and connect", "ومومه او وصل شه")
  val scanQr: String get() = t("خواندنِ QR ِ کامپیوتر", "Scan the computer's QR", "د کمپیوټر QR لوستل")
  val searching: String get() = t("در جست‌وجوی کامپیوترِ دکان…", "Looking for the shop computer…", "د دوکان کمپیوټر لټول کېږي…")
  val notFound: String get() = t("کامپیوترِ هاب در این شبکه پیدا نشد؛ QR را بخوانید.", "No hub computer found on this network; scan the QR.", "په دې شبکه کې هب کمپیوټر ونه موندل شو؛ QR ولولئ.")
  fun joined(address: String): String = t("وصل به هاب: ${asIs(address)}", "Connected to the hub: $address", "هب ته وصل: $address")
  val joinedOk: String get() = t("گوشی با کامپیوترِ دکان جفت شد.", "The phone is paired with the shop computer.", "ګوشۍ د دوکان له کمپیوټر سره جوړه شوه.")
  val leave: String get() = t("جدا شدن از هاب", "Leave the hub", "له هب څخه جلا کېدل")

  /** دلیلِ ناکامیِ جفت شدن (`HubPairClient.Result.Failed.code`) */
  fun pairFailed(code: String): String = when (code) {
    "hub_pair_wrong" -> t("کد درست نیست.", "The code is not right.", "کوډ سم نه دی.")
    "hub_pair_used" -> t("این کد پیش‌تر به کار رفته است؛ کدِ تازه بخواهید.", "This code was already used; ask for a new one.", "دا کوډ مخکې کارول شوی؛ نوی کوډ وغواړئ.")
    "hub_pair_expired", "hub_pair_none" -> t("زمانِ کد گذشته است؛ کدِ تازه بخواهید.", "The code has expired; ask for a new one.", "د کوډ وخت تېر شوی؛ نوی کوډ وغواړئ.")
    "hub_pair_locked" -> t("تلاشِ نادرستِ زیاد؛ کدِ تازه بخواهید.", "Too many wrong tries; ask for a new code.", "ډېرې ناسمې هڅې؛ نوی کوډ وغواړئ.")
    "hub_not_trusted" -> t("این کامپیوتر همان هاب نیست یا کد درست نیست؛ وصل نشد.", "This is not the right hub or the code is wrong; not connected.", "دا هماغه هب نه دی یا کوډ سم نه دی؛ وصل نه شو.")
    "hub_other_shop" -> t("این گوشی از دکانِ دیگری است.", "This phone belongs to another shop.", "دا ګوشۍ د بل دوکان ده.")
    "hub_unverified", "hub_stale" -> t("یک بار با اینترنت وارد شوید، بعد جفت کنید.", "Sign in once with the internet, then pair.", "یو ځل له انټرنېټ سره ننوځئ، بیا جوړه کړئ.")
    "hub_bad_qr" -> t("این QR مالِ هابِ دکان نیست.", "This QR is not a shop hub code.", "دا QR د دوکان د هب نه دی.")
    "hub_unreachable" -> t("به کامپیوترِ هاب نرسیدیم.", "Could not reach the hub computer.", "د هب کمپیوټر ته ونه رسېدو.")
    else -> t("وصل نشد (${asIs(code)})", "Not connected ($code)", "وصل نه شو ($code)")
  }
}
