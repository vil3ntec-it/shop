package ir.vil3ntec.tohid.core.i18n

/**
 *  POS-18 — «قیمتِ خاصِ این مشتری»: بخشِ پروندهٔ مشتری (`CustomerPricesButton`)، نشانِ صندوق (`PriceListAtPos`)، برچسبِ
 *  قلمِ رسید و سابقه، تنظیم و سابقهٔ عملیات. ⛔ مبلغ، شمار و تاریخ از `asIs` می‌گذرند (لایهٔ دری دستشان نمی‌زند).
 */
@Suppress("unused")
object StrCustomerPrices {
  val title: String get() = t("قیمتِ خاصِ این مشتری", "Special prices for this customer", "د دې پېرودونکي ځانګړې بیې")
  fun summary(n: Int): String = if (n <= 0) t("قیمتِ خاص: ندارد", "Special prices: none", "ځانګړې بیې: نشته")
  else t("قیمتِ خاص: ${asIs(n)} کالا", "Special prices: $n items", "ځانګړې بیې: $n توکي")
  val empty: String get() = t("هنوز قیمتِ خاصی برای این مشتری نیست.", "No special prices for this customer yet.", "دې پېرودونکي ته تر اوسه ځانګړې بیه نشته.")
  val note: String get() = t(
    "قیمتِ خاص برای واحدِ کالا است و جلوی لیستِ مشتری و لیستِ فاکتور می‌نشیند. قیمتِ دستیِ ردیف، تخفیف‌ها و سقفِ تغییرِ قیمت مثلِ همیشه.",
    "A special price is per unit and comes before the customer's and the invoice's price list. Manual line prices, discounts and the price-change limit work as always.",
    "ځانګړې بیه د توکي د یوه واحد لپاره ده او د پېرودونکي او بل له لېست څخه مخکې ده. د کرښې لاسي بیه، تخفیفونه او د بیې د بدلون حد لکه تل.",
  )
  val add: String get() = t("افزودنِ قیمتِ خاص", "Add a special price", "ځانګړې بیه زیاتول")
  val edit: String get() = t("ویرایش", "Edit", "سمول")
  val remove: String get() = t("حذف", "Remove", "لرې کول")
  val save: String get() = t("ذخیره", "Save", "ساتل")
  val search: String get() = t("جست‌وجوی کالا (نام یا بارکد)", "Search goods (name or barcode)", "د توکي لټون (نوم یا بارکوډ)")
  val noMatch: String get() = t("کالایی پیدا نشد", "No goods found", "هېڅ توکی ونه موندل شو")
  val price: String get() = t("قیمتِ خاص (افغانی)", "Special price (afghani)", "ځانګړې بیه (افغانۍ)")
  val from: String get() = t("از تاریخ (اختیاری)", "From date (optional)", "له نېټې (اختیاري)")
  val to: String get() = t("تا تاریخ (اختیاری)", "To date (optional)", "تر نېټې (اختیاري)")
  fun line(price: String, retail: String): String = t(
    "${asIs(price)} افغانی (خرده ${asIs(retail)})",
    "$price afghani (retail $retail)",
    "$price افغانۍ (پرچون $retail)",
  )
  fun range(from: String, to: String): String = t("از ${asIs(from)} تا ${asIs(to)}", "from $from to $to", "له $from تر $to")
  val expired: String get() = t("گذشته", "expired", "تېره شوې")
  val notYet: String get() = t("هنوز نه", "not yet", "تر اوسه نه")
  val saved: String get() = t("قیمتِ خاص ذخیره شد", "Special price saved", "ځانګړې بیه وساتل شوه")
  val removed: String get() = t("قیمتِ خاص برداشته شد", "Special price removed", "ځانګړې بیه لرې شوه")
  val notSaved: String get() = t("قیمتِ خاص ذخیره نشد", "The special price was not saved", "ځانګړې بیه ونه ساتل شوه")
  val notAllowed: String get() = t(
    "گذاشتنِ قیمتِ خاص برای نقشِ شما مجاز نیست (همان اجازهٔ تغییرِ قیمت).",
    "Your role may not set special prices (the same permission as changing prices).",
    "ستاسو رول ځانګړې بیه نه شي ټاکلی (د بیې د بدلون همغه اجازه).",
  )
  val pickProduct: String get() = t("یک کالا برگزینید", "Pick an item", "یو توکی وټاکئ")
  val badPrice: String get() = t("قیمت باید بیش از صفر باشد", "The price must be above zero", "بیه باید له صفر څخه زیاته وي")
  val badDate: String get() = t("تاریخ خوانده نشد", "The date could not be read", "نېټه ونه لوستل شوه")
  val badRange: String get() = t("«تا تاریخ» پیش از «از تاریخ» است", "The end date is before the start date", "پای نېټه له پیل نېټې مخکې ده")
  val duplicate: String get() = t("این کالا قیمتِ خاص دارد؛ همان را ویرایش کنید", "This item already has a special price; edit that one", "دې توکي ځانګړې بیه لري؛ هماغه سمه کړئ")
  fun full(max: Int): String = t("بیشتر از ${asIs(max)} قیمتِ خاص برای یک مشتری نمی‌شود", "A customer can have at most $max special prices", "یو پېرودونکی تر $max زیاتې ځانګړې بیې نه شي لرلی")
  /** برچسبِ قلمِ رسید و سابقه */
  val sourceLabel: String get() = t("قیمتِ خاص", "Special price", "ځانګړې بیه")
  /** نشانِ صندوق پس از عوض شدنِ مشتری */
  fun repriced(changed: Int, kept: Int): String = if (kept > 0) t(
    "قیمتِ ${asIs(changed)} ردیف با این مشتری عوض شد؛ ${asIs(kept)} ردیفِ دستی دست نخورد",
    "$changed lines repriced for this customer; $kept manual lines kept",
    "د $changed کرښو بیه د دې پېرودونکي لپاره بدله شوه؛ $kept لاسي کرښې پاتې شوې",
  ) else t(
    "قیمتِ ${asIs(changed)} ردیف با این مشتری عوض شد",
    "$changed lines repriced for this customer",
    "د $changed کرښو بیه د دې پېرودونکي لپاره بدله شوه",
  )
  fun specialLines(n: Int): String = t("${asIs(n)} ردیف با قیمتِ خاصِ مشتری", "$n lines at the customer's special price", "$n کرښې د پېرودونکي په ځانګړې بیه")
  val settingsTitle: String get() = t("قیمتِ خاصِ مشتری", "Customer special prices", "د پېرودونکي ځانګړې بیې")
  val settingsNote: String get() = t(
    "روشن ⇒ در پروندهٔ مشتری «قیمتِ خاصِ این مشتری» دیده می‌شود و صندوق آن قیمت را پیش از لیستِ مشتری و لیستِ فاکتور می‌نشاند (با «لیستِ قیمت در صندوق» ِ روشن). خاموش ⇒ همان رفتارِ پیشین.",
    "On ⇒ the customer file shows “Special prices for this customer” and the till uses that price before the customer's and the invoice's price list (with “Price list at the till” on). Off ⇒ exactly as before.",
    "روښانه ⇒ د پېرودونکي په دوسیه کې «د دې پېرودونکي ځانګړې بیې» ښکاري او صندوق هغه بیه د پېرودونکي او بل له لېست څخه مخکې ږدي (د «په صندوق کې د بیو لېست» سره). بند ⇒ لکه پخوا.",
  )
  val auditSet: String get() = t("قیمتِ خاصِ مشتری", "Customer special price", "د پېرودونکي ځانګړې بیه")
  val auditRemoved: String get() = t("حذفِ قیمتِ خاصِ مشتری", "Customer special price removed", "د پېرودونکي ځانګړې بیه لرې شوه")
}
