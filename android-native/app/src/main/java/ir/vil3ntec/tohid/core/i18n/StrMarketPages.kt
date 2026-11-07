package ir.vil3ntec.tohid.core.i18n

/**
 *  متنِ قاعده‌های تخفیف (`PromoText.kt`: نوع، شیوهٔ ترکیب، خطای فرم، مقدار، دامنه، زمان و وضعیت)، پیوند و QR ِ بازاریابی
 *  (`StorefrontCampaigns.kt`) و پرونده و ادغامِ مشتری (`CrmProfileUi.kt`) در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند. این
 *  نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۴). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا
 *  همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. نامِ دو پیوندِ ثابتِ QR که روی سرور ذخیره می‌شود داده است و در صفحه
 *  «// داده:» دارد. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrMarketPages {
  // ── `PromoText.kt` ──

  // نوع و شیوهٔ ترکیب
  val kindAmount: String get() = t("مبلغ از هر واحد", "Amount off each unit", "له هر واحد څخه مبلغ")
  val kindPrice: String get() = t("قیمتِ ویژه", "Special price", "ځانګړې بیه")
  val kindTiers: String get() = t("پله‌ای بر تعداد", "Tiered by quantity", "د شمېر له مخې پوړیز")
  val kindBxgy: String get() = t("بخر X بگیر Y", "Buy X get Y", "X واخلئ Y ترلاسه کړئ")
  val kindBundle: String get() = t("بسته با قیمتِ ثابت", "Bundle at a fixed price", "په ټاکلې بیه بسته")
  val kindThreshold: String get() = t("آستانهٔ مبلغِ خرید", "Purchase amount threshold", "د پېرود د مبلغ پوله")
  val kindExpiry: String get() = t("تخفیفِ نزدیکِ انقضا", "Near-expiry discount", "د پای نېټې ته نږدې تخفیف")
  val stackBest: String get() = t("بهترین قیمت", "Best price", "غوره بیه")
  val stackCombine: String get() = t("ترکیبی", "Combined", "ګډ")
  val hintBest: String get() = t(
    "از میانِ «بهترین قیمت»ها فقط پرسودترین برای مشتری",
    "Among “best price” rules, only the most favourable one for the customer",
    "د «غوره بیه» قاعدو له منځه یوازې هغه چې پېرودونکي ته تر ټولو ګټوره وي",
  )
  val hintCombine: String get() = t("روی بقیه هم اعمال می‌شود", "Also applies on top of the others", "پر نورو هم پلي کېږي")
  val hintExclusive: String get() = t("اگر اعمال شود، هیچ تخفیفِ دیگری نه", "If applied, no other discount", "که پلي شي، بل هېڅ تخفیف نه")

  // خطای فرم (همان پیام‌های سرور)
  val errId: String get() = t("شناسه لازم است", "An ID is required", "پېژند اړین دی")
  val errName: String get() = t("نام لازم است", "A name is required", "نوم اړین دی")
  val errKind: String get() = t("نوعِ تخفیف را انتخاب کنید", "Choose the discount type", "د تخفیف ډول وټاکئ")
  val errStack: String get() = t("شیوهٔ ترکیب نادرست است", "The stacking mode is invalid", "د یوځای کېدو طریقه ناسمه ده")
  val errPriority: String get() = t(
    "اولویت باید بینِ ‎-۱۰۰۰ و ۱۰۰۰ باشد",
    "Priority must be between -1000 and 1000",
    "لومړیتوب باید د ‎-۱۰۰۰ او ۱۰۰۰ ترمنځ وي",
  )
  val errCode: String get() = t(
    "کد باید ۳ تا ۳۲ حرف یا رقمِ لاتین باشد",
    "The code must be 3 to 32 Latin letters or digits",
    "کوډ باید له ۳ تر ۳۲ لاتین توري یا عددونه وي",
  )
  val errUses: String get() = t("سقفِ مصرف نادرست است", "The usage limit is invalid", "د کارونې حد ناسم دی")
  val errChannel: String get() = t("کانال نادرست است", "The channel is invalid", "چینل ناسم دی")
  val errWindow: String get() = t(
    "بازهٔ زمانی نادرست است (از ≤ تا، ساعتِ شروع ≠ پایان)",
    "The time window is invalid (from ≤ to, start hour ≠ end)",
    "د وخت موده ناسمه ده (له ≤ تر، د پیل ساعت ≠ پای)",
  )
  val errBp: String get() = t(
    "درصد باید بیش از ۰ و حداکثر ۱۰۰ باشد",
    "The percent must be above 0 and at most 100",
    "سلنه باید له ۰ ډېره او تر ټولو ډېره ۱۰۰ وي",
  )
  val errAmount: String get() = t(
    "مبلغ باید مثبت و افغانیِ کامل باشد",
    "The amount must be positive and in whole afghani",
    "مبلغ باید مثبت او بشپړې افغانۍ وي",
  )
  val errPrices: String get() = t("کالا و قیمتِ ویژه را بدهید", "Give the product and the special price", "توکی او ځانګړې بیه ورکړئ")
  val errTiers: String get() = t(
    "پله‌ها باید صعودی باشند — هم آستانه، هم تخفیف؛ قیمتِ ثابتِ هر پله کمتر از پلهٔ پیش",
    "Tiers must go up — both threshold and discount; each tier's fixed price lower than the previous tier's",
    "پوړونه باید پورته لاړ شي — هم پوله، هم تخفیف؛ د هر پوړ ټاکلې بیه له مخکیني پوړ ټیټه",
  )
  val errBxgy: String get() = t(
    "«بخر» و «بگیر» دست‌کم ۱، تخفیفِ بخشِ «بگیر» بینِ ۱ تا ۱۰۰٪",
    "“Buy” and “Get” at least 1, and the discount on the “Get” part between 1 and 100%",
    "«واخلئ» او «ترلاسه کړئ» لږ تر لږه ۱، د «ترلاسه کړئ» برخې تخفیف د ۱ او ۱۰۰٪ ترمنځ",
  )
  val errBundle: String get() = t(
    "بسته دست‌کم ۲ قلم و قیمتِ افغانیِ کامل",
    "A bundle needs at least 2 items and a price in whole afghani",
    "بسته لږ تر لږه ۲ توکي او په بشپړو افغانیو بیه",
  )
  val errExpiry: String get() = t(
    "روزِ پیش از انقضا باید ۱ تا ۳۶۵ باشد",
    "Days before expiry must be 1 to 365",
    "د پای نېټې مخکې ورځې باید له ۱ تر ۳۶۵ وي",
  )

  // مقدار، دامنه و زمان
  fun amountEach(a: Any?): String = t("${asIs(a)} ؋ از هر واحد", "$a ؋ off each unit", "$a ؋ له هر واحد څخه")
  fun buyGet(buy: Any?, get: Any?): String = t("بخر ${asIs(buy)} بگیر ${asIs(get)}", "Buy $buy get $get", "$buy واخلئ $get ترلاسه کړئ")
  val freeWord: String get() = t(" رایگان", " free", " وړیا")
  fun withPctOff(p: Any?): String = t(" با ${asIs(p)} تخفیف", " with $p off", " له $p تخفیف سره")
  fun bundleFor(n: Any?, a: Any?): String = t("${asIs(n)} تا ${asIs(a)} ؋", "$n for $a ؋", "$n په $a ؋")
  fun fromAmount(a: Any?): String = t("از ${asIs(a)} ⇐ ", "from $a ⇐ ", "له $a ⇐ ")
  fun nearExpiryText(p: Any?, d: Any?): String = t(
    "${asIs(p)} تا ${asIs(d)} روز پیش از انقضا",
    "$p off $d ${pl(d ?: 0, "day", "days")} before expiry",
    "$p د پای نېټې $d ورځې مخکې",
  )
  val categoryPrefix: String get() = t("دستهٔ ", "Category ", "ډله ")
  val brandPrefix: String get() = t("برندِ ", "Brand ", "برانډ ")
  val supplierPrefix: String get() = t("تأمین‌کنندهٔ ", "Supplier ", "عرضه‌کوونکی ")
  fun exceptOf(what: Any?): String = t(" (جز ${asIs(what)})", " (except $what)", " (پرته له $what)")
  fun exceptBrand(b: Any?): String = t(" (جز برندِ ${asIs(b)})", " (except brand $b)", " (پرته له برانډ $b)")
  fun exceptCategory(c: Any?): String = t(" (جز دستهٔ ${asIs(c)})", " (except category $c)", " (پرته له ډلې $c)")
  fun exceptSupplier(s: Any?): String = t(" (جز تأمین‌کنندهٔ ${asIs(s)})", " (except supplier $s)", " (پرته له عرضه‌کوونکي $s)")
  val deletedMark: String get() = t("(حذف‌شده)", "(deleted)", "(ړنګ شوی)")
  val always: String get() = t("همیشه", "Always", "تل")

  // وضعیت و زبانه
  val ended: String get() = t("تمام‌شده", "Ended", "پای ته رسېدلی")
  val scheduled: String get() = t("زمان‌بندی‌شده", "Scheduled", "مهالوېشل شوی")

  // ── `StorefrontCampaigns.kt` ──

  // پیوندها و QR
  val linksNotOpened: String get() = t("پیوندها باز نشد", "The links didn't open", "لینکونه خلاص نه شول")
  val linksOff: String get() = t("پیوند و QR هنوز روشن نیست", "Links and QR aren't on yet", "لینک او QR لا روښانه نه دي")
  val membersOnly: String get() = t("فقط اعضای دکان", "Shop members only", "یوازې د دوکان غړي")
  val linksNeedRead: String get() = t(
    "دیدنِ پیوندها اجازهٔ خواندنِ دادهٔ دکان را می‌خواهد.",
    "Seeing the links needs permission to read the shop's data.",
    "د لینکونو لیدل د دوکان د معلوماتو د لوستلو اجازه غواړي.",
  )
  fun linksIntro(d: Any?): String = t(
    "هر پیوند نشان می‌دهد مشتری از کجا آمده. شمارها مالِ ${asIs(d)} روزِ اخیرند و هیچ نام یا شماره‌ای ندارند.",
    "Each link shows where a customer came from. The counts cover the last $d ${pl(d ?: 0, "day", "days")} and carry no name or number.",
    "هر لینک ښيي چې پېرودونکی له کومه راغلی. شمېرې د وروستیو $d ورځو دي او هېڅ نوم یا شمېره نه لري.",
  )
  val noLinksYet: String get() = t("هنوز پیوندی نیست", "No links yet", "لا لینک نشته")
  val makeFirstLink: String get() = t(
    "با «QR ِ دیوار» یا فرمِ بالا نخستین پیوند را بسازید.",
    "Make the first link with “Wall QR” or the form above.",
    "لومړی لینک د «د دېوال QR» یا پورتني فورم سره جوړ کړئ.",
  )
  val ownerMakesLinks: String get() = t(
    "صاحب دکان یا مدیر پیوند می‌سازد.",
    "The shop owner or a manager makes links.",
    "د دوکان څښتن یا مدیر لینک جوړوي.",
  )
  val archivedDone: String get() = t("بایگانی شد", "Archived", "آرشیف شو")
  val restoredDone: String get() = t("برگشت", "Restored", "بېرته راغی")
  val linkUrlUnread: String get() = t("نشانیِ این پیوند خوانده نشد", "This link's address couldn't be read", "د دې لینک پته ونه لوستل شوه")
  val wallQr: String get() = t("QR ِ دیوار", "Wall QR", "د دېوال QR")
  val receiptQr: String get() = t("QR ِ رسید", "Receipt QR", "د رسید QR")
  val newLink: String get() = t("پیوندِ تازه", "New link", "نوی لینک")
  val linkNameField: String get() = t(
    "نام (مثلاً «اینستاگرام» یا «تراکتِ محله»)",
    "Name (e.g. “Instagram” or “neighbourhood flyer”)",
    "نوم (لکه «انسټاګرام» یا «د سیمې اعلان»)",
  )
  val makeLink: String get() = t("ساختنِ پیوند", "Make link", "لینک جوړول")
  val archivedMark: String get() = t(" · بایگانی‌شده", " · archived", " · آرشیف شوی")
  val qrAndLink: String get() = t("QR و پیوند", "QR and link", "QR او لینک")
  fun linkStats(v: Any?, o: Any?, d: Any?, a: Any?): String = t(
    "بازدید ${asIs(v)} · سفارش ${asIs(o)} · تحویل‌شده ${asIs(d)} (${asIs(a)} افغانی)",
    "Visits $v · orders $o · delivered $d ($a afghani)",
    "لیدنې $v · فرمایشونه $o · سپارل شوي $d ($a افغانۍ)",
  )
  val archiveLink: String get() = t(
    "بایگانی (QR ِ چاپ‌شده هنوز به ویترین می‌برد)",
    "Archive (a printed QR still leads to the storefront)",
    "آرشیف (چاپ شوی QR لا هم هټۍ ته بیايي)",
  )
  val linkCopied: String get() = t("پیوند کپی شد", "Link copied", "لینک کاپي شو")
  val notPrinted: String get() = t("چاپ نشد", "Not printed", "چاپ نه شو")
  val printOnReceipt: String get() = t("چاپ روی رسید", "Print on receipt", "پر رسید چاپول")
  val sheetNotMade: String get() = t("برگه ساخته نشد", "The sheet wasn't made", "پاڼه جوړه نه شوه")

  // ── `CrmProfileUi.kt` ──

  // پرونده و ادغامِ مشتری
  val tagsField: String get() = t("برچسب‌ها (با «،» جدا؛ اختیاری)", "Tags (separated by “،”; optional)", "ټګونه (په «،» جلا؛ اختیاري)")
  fun mergeDupesN(n: Any?): String = t("ادغامِ مشتریانِ تکراری (${asIs(n)})", "Merge duplicate customers ($n)", "تکراري پېرودونکي یوځای کول ($n)")
  fun profileOf(name: Any?): String = t("پروندهٔ «${asIs(name)}»", "“$name” profile", "د «$name» دوسیه")
  val mergeDupes: String get() = t("ادغامِ مشتریانِ تکراری", "Merge duplicate customers", "تکراري پېرودونکي یوځای کول")
  fun mergeAsk(drop: Any?, keep: Any?): String = t(
    "«${asIs(drop)}» در «${asIs(keep)}» ادغام شود؟",
    "Merge “$drop” into “$keep”?",
    "«$drop» په «$keep» کې یوځای شي؟",
  )
  fun mergeMoves(tx: Any?, sales: Any?, keep: Any?, bal: Any?): String = t(
    "${asIs(tx)} ردیفِ حساب و ${asIs(sales)} فاکتور به «${asIs(keep)}» می‌رود و ماندهٔ او ${asIs(bal)} ؋ می‌شود. ",
    "$tx account ${pl(tx ?: 0, "row", "rows")} and $sales ${pl(sales ?: 0, "invoice", "invoices")} go to “$keep” and their balance becomes $bal ؋. ",
    "$tx د حساب قطارونه او $sales بلونه «$keep» ته ځي او پاتې یې $bal ؋ کېږي. ",
  )
  fun mergeKeepsRows(drop: Any?): String = t(
    "هیچ ردیفی پاک نمی‌شود؛ «${asIs(drop)}» فقط از فهرست‌ها بیرون می‌رود.",
    "No row is deleted; “$drop” is only removed from the lists.",
    "هېڅ قطار نه ړنګېږي؛ «$drop» یوازې له لړلیکونو وځي.",
  )
  val noDupes: String get() = t("مشتریِ تکراری پیدا نشد.", "No duplicate customer found.", "تکراري پېرودونکی ونه موندل شو.")
  val mergeIntoKept: String get() = t("ادغام در ماندگار", "Merge into kept", "په ساتل شوي کې یوځای کول")
}
