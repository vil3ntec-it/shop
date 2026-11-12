package ir.vil3ntec.tohid.core.i18n

/**
 *  IMG-PRO-17 — صفحه‌های «تغییرِ دسته‌ایِ عکس‌ها» و برگشتِ یک‌جا (`ui/v3/stock/ImageChangesPages.kt`؛
 *  منطق در `core/image/ImageChanges`). IMG-PRO-16 — «نسخه‌های عکس» (`ui/v3/products/PhotoVersionsPage.kt`).
 */
@Suppress("unused")
object StrImageChanges {

  val title: String get() = t("تغییرِ دسته‌ایِ عکس‌ها", "Batch photo changes", "د انځورونو ډله‌ییز بدلون")
  val loading: String get() = t("از سرور می‌آید…", "Loading from the server…", "له سرور څخه راځي…")
  val failed: String get() = t("از سرور نیامد. اینترنت را ببینید و دوباره بزنید.", "Could not load from the server. Check the internet and try again.", "له سرور څخه رانغی. انټرنېټ وګورئ او بیا هڅه وکړئ.")
  val off: String get() = t("بانکِ تصاویر برای این دکان روشن نیست.", "The image bank is not turned on for this shop.", "د انځورونو بانک د دې دوکان لپاره فعال نه دی.")
  val retry: String get() = t("دوباره", "Retry", "بیا")
  val none: String get() = t("هنوز هیچ تغییرِ دسته‌ای نیست.", "No batch changes yet.", "لا تر اوسه ډله‌ییز بدلون نشته.")
  val storefrontNote: String get() = t(
    "دسته فقط عکسی را عوض می‌کند که ویترین و بانک برای هر کالا نشان می‌دهند؛ عکسِ روی این دستگاه دست نمی‌خورد. برگشت همه را یک‌جا به پیش از دسته می‌برد.",
    "A batch only changes the photo the storefront and bank show for each product; the photo on this device is untouched. Rollback returns all of them at once.",
    "ډله یوازې هغه انځور بدلوي چې ویټرین او بانک یې د هر توکي لپاره ښيي؛ په دې دستګاه کې انځور نه بدلېږي. بېرته‌ګرځول ټول په یوځل مخکې حالت ته وړي.",
  )
  val newBatch: String get() = t("دستهٔ تازه", "New batch", "نوې ډله")
  val applied: String get() = t("اعمال‌شده", "Applied", "پلي شوې")
  val rolledBack: String get() = t("برگشته", "Rolled back", "بېرته شوې")
  fun items(n: Int): String = t("${asIs(n)} کالا", "$n products", "$n توکي")
  fun line(status: String, items: String, at: String): String = t("${asIs(status)} · ${asIs(items)} · ${asIs(at)}", "$status · $items · $at", "$status · $items · $at")

  // ── یک دسته ──
  val batch: String get() = t("دستهٔ تغییر", "Change batch", "د بدلون ډله")
  val before: String get() = t("پیش", "Before", "مخکې")
  val after: String get() = t("پس", "After", "وروسته")
  fun beforeAfter(before: String, after: String): String =
    t("پیش: ${asIs(before)} ⇐ پس: ${asIs(after)}", "Before: $before → After: $after", "مخکې: $before ⇐ وروسته: $after")
  val previousDefault: String get() = t("همان عکسِ همگام‌شده یا بی عکس", "The synced photo, or no photo", "همغږي شوی انځور یا بې انځوره")
  val sourceCentral: String get() = t("عکسِ مرکزی", "Central photo", "مرکزي انځور")
  val sourceShop: String get() = t("عکسِ خودِ دکان", "Shop's own photo", "د دوکان خپل انځور")
  val sourceNone: String get() = t("بی عکس", "No photo", "بې انځوره")
  fun version(v: Int): String = t("نسخهٔ ${asIs(v)}", "version $v", "نسخه $v")
  fun rolledBackLine(at: String, reason: String): String =
    if (reason.isBlank()) t("برگشت: ${asIs(at)}", "Rolled back: $at", "بېرته: $at")
    else t("برگشت: ${asIs(at)} — ${asIs(reason)}", "Rolled back: $at — $reason", "بېرته: $at — $reason")
  val rollbackAll: String get() = t("برگرداندنِ همهٔ این دسته", "Roll back this whole batch", "دا ټوله ډله بېرته کړئ")
  val rollbackMessage: String get() = t(
    "عکسِ همهٔ کالاهای این دسته دقیقاً به پیش از آن برمی‌گردد. اگر عکسِ کالایی پس از این دسته دوباره عوض شده باشد هیچ چیز برنمی‌گردد.",
    "Every product in this batch returns exactly to its photo before it. If any product was changed again afterwards, nothing is rolled back.",
    "د دې ډلې د ټولو توکو انځور سم مخکیني حالت ته ګرځي. که د کوم توکي انځور وروسته بیا بدل شوی وي، هېڅ نه ګرځي.",
  )
  val reason: String get() = t("دلیل (اختیاری)", "Reason (optional)", "لامل (اختیاري)")
  val rollbackDone: String get() = t("همهٔ عکس‌های این دسته برگشت.", "All photos in this batch were rolled back.", "د دې ډلې ټول انځورونه بېرته شول.")
  val alreadyRolledBack: String get() = t("این دسته پیش‌تر برگشته بود.", "This batch was already rolled back.", "دا ډله مخکې بېرته شوې وه.")
  fun changedSince(names: String): String = t(
    "هیچ عکسی برنگشت: عکسِ این کالاها پس از این دسته دوباره عوض شده است — اول همان تغییرِ تازه‌تر را برگردانید: ${asIs(names)}",
    "Nothing was rolled back: these products were changed again after this batch — roll back the newer change first: $names",
    "هېڅ انځور بېرته نه شو: د دې توکو انځور له دې ډلې وروسته بیا بدل شوی — لومړی نوی بدلون بېرته کړئ: $names",
  )

  // ── دستهٔ تازه ──
  val actionLatest: String get() = t("نسخهٔ تازهٔ عکسِ مرکزی", "Newest central photo version", "د مرکزي انځور نوې نسخه")
  val actionLatestNote: String get() = t(
    "برای کالایی که عکسِ مرکزی دارد، نسخهٔ تازه‌ترِ تأییدشدهٔ همان عکس.",
    "For products with a central photo, the newest approved version of that photo.",
    "هغو توکو ته چې مرکزي انځور لري، د هماغه انځور نوې تایید شوې نسخه.",
  )
  val actionRevert: String get() = t("برگشت به عکسِ مرکزی", "Back to the central photo", "مرکزي انځور ته بېرته")
  val actionRevertNote: String get() = t(
    "برای کالایی که از عکسِ مرکزی‌اش جدا شده، همان آخرین عکسِ مرکزیِ خودش.",
    "For products that moved away from their central photo, their last central photo.",
    "هغو توکو ته چې له خپل مرکزي انځوره جلا شوي، د هماغه وروستي مرکزي انځور.",
  )
  val pick: String get() = t("کالاها", "Products", "توکي")
  val search: String get() = t("جست‌وجوی نام یا بارکد", "Search name or barcode", "نوم یا بارکوډ ولټوئ")
  fun picked(n: Int, max: Int): String = t("${asIs(n)} از حداکثر ${asIs(max)} کالا", "$n of at most $max products", "$n له تر ټولو $max توکو")
  val check: String get() = t("بررسی", "Check", "کتنه")
  val checking: String get() = t("وضعِ عکسِ کالاها از سرور می‌آید…", "Loading each product's photo state…", "د توکو د انځور حالت راځي…")
  fun willChange(n: Int): String = t("${asIs(n)} کالا عوض می‌شود", "$n products will change", "$n توکي بدلېږي")
  fun skippedCount(n: Int): String = t("${asIs(n)} کالا کنار ماند", "$n products skipped", "$n توکي پاتې شول")
  val skipNoCentral: String get() = t("عکسِ مرکزی ندارد", "Has no central photo", "مرکزي انځور نه لري")
  val skipAlready: String get() = t("همین حالا همان است", "Already so", "همدا اوس همداسې دی")
  val skipArchived: String get() = t("عکسِ مرکزی بایگانی شده", "The central photo is archived", "مرکزي انځور ارشیف شوی")
  val skipUnknown: String get() = t("وضعش از سرور نیامد", "State did not load", "حالت یې رانغی")
  val note: String get() = t("یادداشتِ دسته (اختیاری)", "Batch note (optional)", "د ډلې یادښت (اختیاري)")
  val apply: String get() = t("اعمالِ دسته", "Apply batch", "ډله پلي کړئ")
  val appliedToast: String get() = t("دسته اعمال شد؛ با «برگرداندن» همه یک‌جا برمی‌گردند.", "Batch applied; “Roll back” returns all of them at once.", "ډله پلي شوه؛ «بېرته» ټول په یوځل بېرته کوي.")
  val nothingToSend: String get() = t("هیچ کالایی برای این کار نیست.", "No product needs this.", "دې کار ته هېڅ توکی اړتیا نه لري.")
  fun needsConfirm(name: String): String = t(
    "تطبیقِ «${asIs(name)}» با عکسِ بانک قطعی نیست. مشخصاتِ کالا را با عکس بسنجید و فقط اگر درست است تأیید کنید.",
    "The match between “$name” and the bank photo is not certain. Compare the product with the photo and confirm only if it is right.",
    "د «$name» او د بانک انځور ترمنځ سمون ډاډمن نه دی. توکی له انځور سره پرتله کړئ او یوازې که سم وي تایید یې کړئ.",
  )
  val confirmThis: String get() = t("تأییدِ همین کالا و دوباره", "Confirm this product and retry", "همدا توکی تایید او بیا")
  fun removeThis(name: String): String = t("برداشتنِ «${asIs(name)}» از دسته", "Remove “$name” from the batch", "«$name» له ډلې لرې کړئ")
  fun itemFailed(name: String, message: String): String =
    t("«${asIs(name)}»: ${asIs(message)} — هیچ کالایی عوض نشد.", "“$name”: $message — no product was changed.", "«$name»: $message — هېڅ توکی بدل نه شو.")
  val keyReused: String get() = t("این دسته پیش‌تر ثبت شده بود؛ فهرست را ببینید.", "This batch was already recorded; see the list.", "دا ډله مخکې ثبت شوې وه؛ لړلیک وګورئ.")

  // ── IMG-PRO-16: نسخه‌های عکس ──
  val versionsTitle: String get() = t("نسخه‌های عکس", "Photo versions", "د انځور نسخې")
  val versionsNote: String get() = t(
    "همان نسخه‌هایی که ویترین و برنامه نشان می‌دهند: کوچک (فهرست‌ها) و بزرگ (صفحهٔ کالا)، در گوشی و صفحهٔ بزرگ. هیچ نسخه‌ای بزرگ‌تر از خودش کشیده نمی‌شود.",
    "The versions the storefront and app show: small (lists) and large (product page), on a phone and a large screen. No version is stretched beyond its own size.",
    "هغه نسخې چې ویټرین او برنامه یې ښيي: کوچنۍ (لړلیکونه) او لویه (د توکي پاڼه)، په ګوشي او لویه پرده. هېڅ نسخه له خپل اندازې لویه نه کشول کېږي.",
  )
  val noBankPhoto: String get() = t(
    "این کالا هنوز عکسِ بانکِ تصاویر ندارد؛ نسخه‌ها فقط برای عکسِ بانک ساخته می‌شوند.",
    "This product has no image-bank photo yet; versions exist only for bank photos.",
    "دا توکی لا د انځورونو بانک انځور نه لري؛ نسخې یوازې د بانک انځور لپاره جوړېږي.",
  )
  val thumbVersion: String get() = t("نسخهٔ کوچک", "Small version", "کوچنۍ نسخه")
  val thumbNote: String get() = t("به همان اندازه‌ای که در فهرستِ کالاها و ویترین دیده می‌شود.", "At the sizes it appears in product lists and the storefront.", "په هغه اندازو چې د توکو په لړلیک او ویټرین کې ښکاري.")
  val displayVersion: String get() = t("نسخهٔ بزرگ", "Large version", "لویه نسخه")
  val phoneScreen: String get() = t("گوشی", "Phone", "ګوشي")
  val largeScreen: String get() = t("صفحهٔ بزرگ", "Large screen", "لویه پرده")
  val actualPixels: String get() = t("۱:۱ (هر پیکسل یک پیکسل)", "1:1 (pixel for pixel)", "۱:۱ (پکسل په پکسل)")
  val actualPixelsNote: String get() = t("نوشته و لبه‌ها را این‌جا ببینید؛ این همان وضوحِ عکس است.", "Check text and edges here; this is the photo's real resolution.", "لیکنه او څنډې دلته وګورئ؛ دا د انځور اصلي وضاحت دی.")
  val nativeSize: String get() = t("به اندازهٔ خودِ عکس نشان داده شد؛ صفحه آن را بزرگ‌تر نمی‌کشد.", "Shown at the photo's own size; the screen does not stretch it.", "د انځور په خپله اندازه وښودل شو؛ پرده یې نه کشوي.")
  val scaledDown: String get() = t("برای این صفحه کوچک‌تر نشان داده شد.", "Shown smaller to fit this screen.", "دې پردې ته کوچنۍ وښودل شوه.")
  fun measureLine(w: Int, h: Int, kb: String): String = t("${asIs(w)}×${asIs(h)} پیکسل · ${asIs(kb)} کیلوبایت", "$w×$h px · $kb KB", "$w×$h پکسل · $kb کیلوبایټ")
  fun quality(q: Int): String = t("کیفیتِ JPEG حدودِ ${asIs(q)}", "JPEG quality about $q", "د JPEG کیفیت شاوخوا $q")
  val gradeSharp: String get() = t("تیز — فشرده‌سازیِ کم", "Sharp — light compression", "تېره — لږ فشار")
  val gradeAcceptable: String get() = t("قابلِ قبول", "Acceptable", "د منلو وړ")
  val gradeOver: String get() = t("فشرده‌سازیِ زیاد — نوشته و لبه‌ها لکه می‌گیرند", "Over-compressed — text and edges get blotchy", "ډېر فشار — لیکنه او څنډې داغي کېږي")
  val gradeLossless: String get() = t("بی‌اتلاف (PNG)", "Lossless (PNG)", "بې زیانه (PNG)")
  val gradeUnknown: String get() = t("کیفیت از سرآیند خوانده نشد", "Quality could not be read from the header", "کیفیت له سرلیک څخه ونه لوستل شو")
  val allGood: String get() = t("هر دو نسخه درست‌اند: فشرده‌سازیِ زیاد نیست و نسخهٔ کوچک با بزرگ جور است.", "Both versions are fine: not over-compressed, and the small version matches the large one.", "دواړه نسخې سمې دي: ډېر فشار نشته او کوچنۍ له لویې سره برابره ده.")
  val flagOver: String get() = t("دست‌کم یک نسخه بیش از اندازه فشرده است؛ عکس را با کیفیتِ بهتر دوباره بگذارید.", "At least one version is over-compressed; upload the photo again at better quality.", "لږ تر لږه یوه نسخه ډېره فشار شوې؛ انځور په ښه کیفیت بیا کېږدئ.")
  val flagThumb: String get() = t("نسخهٔ کوچک با نسخهٔ بزرگ جور نیست (بزرگ‌تر است یا نسبتش فرق دارد).", "The small version does not match the large one (bigger, or a different shape).", "کوچنۍ نسخه له لویې سره برابره نه ده (لویه ده یا بڼه یې توپیر لري).")
  val flagSmall: String get() = t("عکسِ منبع کوچک است؛ روی صفحهٔ بزرگ کوچک دیده می‌شود تا تار نشود.", "The source photo is small; on a large screen it stays small so it does not blur.", "سرچینه انځور کوچنی دی؛ په لویه پرده کوچنی ښکاري چې تت نه شي.")
  val flagUnreadable: String get() = t("نوع یا ابعادِ یک نسخه خوانده نشد.", "The type or size of a version could not be read.", "د یوې نسخې ډول یا اندازه ونه لوستل شوه.")
  // ── IMG-11: نسخهٔ بزرگ‌نمایی (۲۴۰۰px، ساختهٔ سرور) ──
  val zoomVersion: String get() = t("نسخهٔ بزرگ‌نمایی", "Zoom version", "د لویولو نسخه")
  val zoomNote: String get() = t(
    "همان نسخه‌ای که ویترین هنگامِ بزرگ‌نمایی نشان می‌دهد. با دو انگشت، دو بار زدن یا دکمه‌ها تا اندازهٔ واقعیِ عکس بزرگ کنید.",
    "The version the storefront shows when zooming. Pinch, double-tap or use the buttons to zoom up to the photo's real size.",
    "هغه نسخه چې ویټرین یې د لویولو پر مهال ښيي. په دوو ګوتو، دوه ځله وهلو یا تڼیو یې د انځور تر اصلي اندازې لوی کړئ.",
  )
  val zoomFits: String get() = t("نسخهٔ بزرگ‌نمایی با نسخهٔ بزرگ جور است.", "The zoom version matches the large version.", "د لویولو نسخه له لویې نسخې سره برابره ده.")
  val zoomMismatch: String get() = t(
    "نسخهٔ بزرگ‌نمایی با نسخهٔ بزرگ جور نیست (نسبتش فرق دارد یا بزرگ‌تر نیست).",
    "The zoom version does not match the large version (different shape, or not larger).",
    "د لویولو نسخه له لویې نسخې سره برابره نه ده (بڼه یې توپیر لري یا لویه نه ده).",
  )
  val zoomIn: String get() = t("بزرگ‌تر", "Zoom in", "لوی کړئ")
  val zoomOut: String get() = t("کوچک‌تر", "Zoom out", "کوچنی کړئ")
  val zoomReset: String get() = t("اندازهٔ نخست", "Original size", "لومړنۍ اندازه")
  fun zoomLevel(percent: Int): String = t("${asIs(percent)}٪", "$percent%", "$percent سلنه")
  //  کارهای صفحه‌خوان ِ قابِ بزرگ‌نمایی (همان کشیدن با دو انگشت)
  val zoomFrame: String get() = t("عکسِ بزرگ‌نمایی", "Zoom photo", "د لویولو انځور")
  val zoomShowLeft: String get() = t("دیدنِ سمتِ چپِ عکس", "Show the left of the photo", "د انځور کیڼ لوری ښودل")
  val zoomShowRight: String get() = t("دیدنِ سمتِ راستِ عکس", "Show the right of the photo", "د انځور ښي لوری ښودل")
  val zoomShowTop: String get() = t("دیدنِ بالای عکس", "Show the top of the photo", "د انځور پورتنۍ برخه ښودل")
  val zoomShowBottom: String get() = t("دیدنِ پایینِ عکس", "Show the bottom of the photo", "د انځور لاندینۍ برخه ښودل")
}
