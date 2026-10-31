package ir.vil3ntec.tohid.core.i18n

/**
 *  قیمت‌پرسی بی فروش در صندوق (POS-15، Ctrl+I).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrPriceCheck {
  val title: String get() = t("قیمت‌پرسی", "Price check", "د بیې پوښتنه")
  val hint: String get() = t("بارکد یا نامِ کالا — چیزی به فاکتور افزوده نمی‌شود", "Barcode or item name — nothing is added to the invoice", "بارکوډ یا د توکي نوم — بل ته هېڅ نه ورزیاتېږي")
  val retail: String get() = t("قیمتِ فروش", "Sale price", "د پلور بیه")
  val wholesale: String get() = t("قیمتِ عمده", "Wholesale price", "د عمده بیه")
  val cost: String get() = t("قیمتِ خرید", "Cost price", "د پېر بیه")
  fun margin(pct: String): String = t("سود: $pct٪", "Margin: $pct%", "ګټه: $pct٪")
  val stock: String get() = t("موجودی", "In stock", "موجودي")
  val lastPurchase: String get() = t("آخرین خرید", "Last purchase", "وروستی پېر")
  val noPurchase: String get() = t("خریدی ثبت نشده", "No purchase recorded", "هېڅ پېر نه دی ثبت")
  val expiry: String get() = t("نزدیک‌ترین انقضا", "Nearest expiry", "تر ټولو نږدې پای نېټه")
  val noExpiry: String get() = t("انقضا ثبت نشده", "No expiry recorded", "پای نېټه نه ده ثبت")
  fun daysLeft(days: Int): String = t("$days روز مانده", "$days days left", "$days ورځې پاتې")
  fun expired(qty: String): String = t("گذشته از انقضا: $qty", "Past expiry: $qty", "له پای نېټې تېر: $qty")
  val notFound: String get() = t("کالایی پیدا نشد", "No item found", "هېڅ توکی ونه موندل شو")
  val close: String get() = t("بستن", "Close", "بندول")

  // تنظیمات
  val section: String get() = t("قیمت‌پرسی در صندوق (Ctrl+I)", "Price check at the till (Ctrl+I)", "په صندوق کې د بیې پوښتنه (Ctrl+I)")
  val note: String get() = t(
    "روشن: Ctrl+I در صندوقِ کامپیوتر پنجرهٔ قیمت‌پرسی را باز می‌کند — قیمت‌ها، موجودی، آخرین خرید و نزدیک‌ترین انقضا، بی افزودن به فاکتور. قیمتِ خرید و سود فقط برای کسی که گزارشِ پول را می‌بیند. فقط روی همین دستگاه.",
    "On: Ctrl+I on the computer till opens a price check — prices, stock, last purchase and nearest expiry, without adding to the invoice. Cost and margin only for people who can see money reports. This device only.",
    "روښانه: Ctrl+I د کمپیوټر په صندوق کې د بیې پوښتنې کړکۍ پرانیزي — بیې، موجودي، وروستی پېر او تر ټولو نږدې پای نېټه، بل ته له ورزیاتولو پرته. د پېر بیه او ګټه یوازې د هغه چا لپاره چې د پیسو راپور ویني. یوازې همدا وسیله.",
  )
}
