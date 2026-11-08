package ir.vil3ntec.tohid.core.i18n

/**
 *  پیام‌های لایهٔ همگام‌سازی (`sync/`) — کدِ آفلاینِ اشتراک، فرستادنِ عکس به کامپیوتر، تعارض‌ها، چراغِ همگام‌سازی و هشدارِ برگشتِ سرور
 *  از پشتیبان (LOC-08 دری، دورِ ۷). فارسی بایت‌به‌بایت همان پیشین؛ هر مقدار با `asIs`. ⚠️ پیامی که در `lastError` ِ همگام‌سازی ذخیره
 *  می‌شود و `SyncModel.classify` آن را می‌خواند (نشانه‌های «سرور»، «ژورنال»، «سایه») داده است و در خودِ پرونده با «// داده: …» مانده؛
 *  کاربر جملهٔ همان دسته را از `StrSettings` می‌بیند و این متن فقط «جزئیات برای پشتیبانی» است.
 */
@Suppress("unused")
object StrSync {

  // ── تعارض‌ها و نامِ دستگاه (`AutoSync`، `LocationPing`) ──
  fun staleN(n: Any?): String = t(
    "${asIs(n)} مورد چون نسخهٔ تازه‌تری روی سرور بود",
    "$n because a newer version was on the server",
    "$n ځکه چې په سرور کې نوې نسخه وه",
  )
  fun deniedN(n: Any): String = t(
    "${asIs(n)} مورد چون اجازهٔ حذفش را نداشتید",
    "$n because you weren't allowed to delete ${pl(n, "it", "them")}",
    "$n ځکه چې د ړنګولو اجازه مو نه درلوده",
  )
  fun otherN(n: Any?): String = t("${asIs(n)} مورد دیگر", "$n other", "$n نور")
  fun rejectedNote(n: Any, parts: Any?): String = t(
    "${asIs(n)} تغییر اعمال نشد: ${asIs(parts)}. نسخهٔ سرور جایش نشست.",
    "$n ${pl(n, "change was", "changes were")} not applied: $parts. The server's version took ${pl(n, "its", "their")} place.",
    "$n بدلونونه پلي نه شول: $parts. د سرور نسخه یې ځای ونیو.",
  )

  // ── کدِ آفلاینِ اشتراک (`OfflineCode`) ──
  val planForever: String get() = t("اشتراکِ دائمی (کدِ آفلاین)", "Permanent subscription (offline code)", "تلپاتې ګډون (افلاین کوډ)")
  val planOffline: String get() = t("اشتراک (کدِ آفلاین)", "Subscription (offline code)", "ګډون (افلاین کوډ)")
  val codeFormat: String get() = t(
    "کد کامل یا درست نیست — همهٔ ۱۳۸ نویسه را بچسبانید",
    "The code isn't complete or valid — paste all 138 characters",
    "کوډ بشپړ یا سم نه دی — ټول ۱۳۸ توري ولګوئ",
  )
  val codeSignature: String get() = t(
    "این کد از سرورِ «فروشگاه من» نیست یا دست خورده است",
    "This code isn't from the My Shop server or has been tampered with",
    "دا کوډ د زما دوکان له سرور نه دی یا لاس پرې وهل شوی",
  )
  val codeComputer: String get() = t(
    "این کد برای کامپیوترِ دیگری ساخته شده است",
    "This code was made for another computer",
    "دا کوډ د بل کمپیوټر لپاره جوړ شوی",
  )
  val codeAccount: String get() = t(
    "این کد برای حسابِ دیگری ساخته شده است",
    "This code was made for another account",
    "دا کوډ د بل حساب لپاره جوړ شوی",
  )
  val codeExpired: String get() = t("مهلتِ این کد تمام شده است", "This code has expired", "د دې کوډ موده پای ته رسېدلې")
  val codeUsed: String get() = t(
    "این کد یک بار در این برنامه استفاده شده است",
    "This code was already used once in this app",
    "دا کوډ یو ځل په دې پروګرام کې کارول شوی",
  )
  val codeNoKey: String get() = t(
    "کلیدِ مجوز در این نسخه نیست؛ یک بار با اینترنت وارد شوید",
    "The licence key isn't in this version; sign in once with the internet",
    "د جواز کیلي په دې نسخه کې نشته؛ یو ځل له انټرنېټ سره ننوځئ",
  )
  val codeRejected: String get() = t("کد پذیرفته نشد", "The code wasn't accepted", "کوډ ونه منل شو")

  // ── عکس از گوشی به کامپیوتر (`PhoneScanner`) ──
  val enterPcCode: String get() = t(
    "اول در «دستگاه‌های جانبی» کدِ کامپیوتر را بزنید",
    "First enter the computer's code in «Accessories»",
    "لومړی په «جانبي وسایلو» کې د کمپیوټر کوډ ولیکئ",
  )
  val signInToSend: String get() = t("برای فرستادن باید وارد حساب باشید", "You must be signed in to send", "د لېږلو لپاره باید حساب ته ننوتي اوسئ")
  fun notSent(why: Any?): String = t("نرفت: ${asIs(why)}", "Didn't go: $why", "ونه لېږل شو: $why")
  val internetQ: String get() = t("اینترنت؟", "Internet?", "انټرنېټ؟")

  // ── چراغ و هشدارِ همگام‌سازی (`SyncStateV1`، `SyncV1Engine`، `SyncV3Engine`) ──
  val dotGreen: String get() = t("همگام با سرور", "In sync with the server", "له سرور سره همغږی")
  val dotYellow: String get() = t("در صف ارسال", "Waiting to send", "د لېږلو په لیکه کې")
  val dotGrey: String get() = t("آفلاین", "Offline", "افلاین")
  val dotRed: String get() = t("خطای همگام‌سازی", "Sync error", "د همغږۍ تېروتنه")
  val restoreNotice: String get() = t(
    "سرور از پشتیبان برگشت و این دستگاه همهٔ کارهای پس از پشتیبان را نگه نداشته بود؛ فروش‌ها و ردیف‌های تازهٔ همین دستگاه فرستاده شدند، ولی ویرایش‌های پیش از آن را یک بار با دفتر بسنجید.",
    "The server went back to a backup and this device hadn't kept all the work done after that backup; this device's new sales and rows were sent, but check the edits before that against the ledger once.",
    "سرور بک‌اپ ته ستون شو او دې وسیلې له بک‌اپ وروسته ټول کارونه نه وو ساتلي؛ د همدې وسیلې نوي پلورونه او کرښې ولېږل شوې، خو له هغې مخکې سمونونه یو ځل له دفتر سره پرتله کړئ.",
  )

  // DATA-SAFETY: دفترِ کنارِ تغییرهای ردشده (`OpQueue.drop`)
  val rejectedBookNotWritten: String get() = t(
    "فهرستِ تغییرهای ردشده روی حافظه نوشته نشد؛ همان تغییرها در صف می‌مانند",
    "The list of rejected changes wasn't written to storage; those changes stay in the queue",
    "د رد شویو بدلونونو لېست په حافظه کې ونه لیکل شو؛ هماغه بدلونونه په کتار کې پاتې کېږي",
  )
}
