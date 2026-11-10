package ir.vil3ntec.tohid.core.i18n

/**
 *  PRM-02 — «کدهای تخفیفِ نگه‌داشته»: آزادِ دستیِ صاحب/مدیر (`ui/v3/settings/sections/PromoHeldScreen.kt`؛
 *  منطق در `core/promo/PromoHeld`).
 */
@Suppress("unused")
object StrPromoHeld {

  val title: String get() = t("کدهای تخفیفِ نگه‌داشته", "Held discount codes", "ساتل شوي تخفیف کوډونه")
  val line: String get() = t(
    "ظرفیتِ کدی که لغوکنندهٔ فاکتور نتوانست آزاد کند",
    "Capacity of codes the cancelling cashier could not release",
    "د هغه کوډ ظرفیت چې د بل کوونکي لخوا خوشې نه شو",
  )
  val note: String get() = t(
    "وقتی فروشنده فاکتوری را با «درخواستِ تأیید» لغو می‌کند، اجازهٔ آزاد کردنِ کدِ سقف‌دارش را ندارد و ظرفیتِ کد گرفته می‌ماند. فاکتورهای لغوشده را این‌جا آزاد کنید تا کد دوباره به کار برود.",
    "When a cashier cancels an invoice with an approval, they may not release its capped code, so the code's capacity stays taken. Release cancelled invoices here so the code can be used again.",
    "کله چې پلورونکی بل د «تایید غوښتنې» سره لغوه کړي، د سقف لرونکي کوډ د خوشې کولو اجازه نه لري او ظرفیت نیول شوی پاتې کېږي. لغوه شوي بلونه دلته خوشې کړئ چې کوډ بیا وکارول شي.",
  )
  val off: String get() = t(
    "شمارشِ مصرفِ کدِ تخفیف برای این دکان روشن نیست.",
    "Discount-code use counting is not turned on for this shop.",
    "د دې دوکان لپاره د تخفیف کوډ د کارونې شمېرنه فعاله نه ده.",
  )
  val denied: String get() = t(
    "این کار فقط با اجازهٔ لغوِ فاکتور (صاحب یا مدیر) است.",
    "Only someone allowed to cancel invoices (owner or manager) can do this.",
    "دا کار یوازې د بل لغوه کولو اجازې سره (څښتن یا مدیر) کېږي.",
  )
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "Could not load from the server. Check the internet and try again.", "له سرور څخه رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val none: String get() = t("هیچ کدی نگه‌داشته نیست.", "No codes are held.", "هېڅ کوډ نه دی ساتل شوی.")
  val more: String get() = t(
    "فقط ۲۰۰ تای تازه‌تر نشان داده شد؛ پس از آزاد کردن، بقیه می‌آیند.",
    "Only the newest 200 are shown; the rest appear after releasing.",
    "یوازې ۲۰۰ نوي وښودل شول؛ له خوشې کولو وروسته نور راځي.",
  )

  // ── گروه‌ها ──
  val cancelled: String get() = t("فاکتورهای لغوشده", "Cancelled invoices", "لغوه شوي بلونه")
  val cancelledFooter: String get() = t(
    "آزاد کردن ظرفیتِ کد را برمی‌گرداند؛ خودِ فاکتور دست نمی‌خورد.",
    "Releasing returns the code's capacity; the invoice itself is untouched.",
    "خوشې کول د کوډ ظرفیت بېرته راګرځوي؛ بل پخپله نه بدلېږي.",
  )
  val active: String get() = t("فاکتورهای زنده", "Active invoices", "فعال بلونه")
  val activeFooter: String get() = t(
    "این فاکتورها کد را درست مصرف کرده‌اند و آزاد نمی‌شوند.",
    "These invoices used the code correctly and are not released.",
    "دې بلونو کوډ سم کارولی او نه خوشې کېږي.",
  )
  val missing: String get() = t("فاکتور روی این دستگاه نیست", "Invoice not on this device", "بل په دې دستګاه کې نشته")
  val missingFooter: String get() = t(
    "شاید هنوز از دستگاهِ دیگر نرسیده باشد. پس از همگام‌سازی، اگر لغو شده بود، این‌جا آزاد کردنی می‌شود.",
    "It may not have arrived from another device yet. After syncing, if it was cancelled, it becomes releasable here.",
    "کېدای شي لا له بلې دستګاه نه وي رارسېدلی. له همغږۍ وروسته، که لغوه شوی وي، دلته د خوشې کولو وړ کېږي.",
  )

  fun invoice(number: String): String = t("فاکتورِ ${asIs(number)}", "Invoice $number", "بل $number")
  fun codes(list: String, at: String): String = t("${asIs(list)} · ${asIs(at)}", "$list · $at", "$list · $at")
  val release: String get() = t("آزاد کن", "Release", "خوشې یې کړئ")

  // ── پاسخ ──
  val released: String get() = t("کد آزاد شد و ظرفیتش برگشت.", "The code was released and its capacity returned.", "کوډ خوشې شو او ظرفیت یې بېرته راغی.")
  val nothing: String get() = t("این فاکتور کدِ نگه‌داشته‌ای نداشت.", "This invoice had no held code.", "دې بل ساتل شوی کوډ نه درلود.")
  val releaseFailed: String get() = t("آزاد نشد. اینترنت را ببینید و دوباره بزنید.", "Not released. Check the internet and try again.", "خوشې نه شو. انټرنېټ وګورئ او بیا هڅه وکړئ.")
}
