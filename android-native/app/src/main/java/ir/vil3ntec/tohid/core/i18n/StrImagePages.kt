package ir.vil3ntec.tohid.core.i18n

/**
 *  بانکِ تصاویر در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ صدا می‌زند: جست‌وجو و انتخابِ عکس از بانک (`ImageBankPicker.kt`)، منبعِ عکسِ
 *  کالا و پیشنهاد به بانکِ مرکزی (`BankPhotoSource.kt`) و ورودِ گروهیِ عکس‌ها (`ImageBulkImport.kt`). این نوشته‌ها پیش‌تر متنِ خامِ
 *  فارسی بودند و از هیچ زبانی نمی‌گذشتند (LOC-08 دری، دورِ ۴). فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری
 *  بود، همان دوباره به کار رفته. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrImagePages {
  // ── `ImageBankPicker.kt` ──

  // جست‌وجو در بانک
  val scanOrType: String get() = t(
    "بارکد را اسکن یا نامِ کالا را بنویسید",
    "Scan the barcode or type the product name",
    "بارکوډ سکن کړئ یا د توکي نوم ولیکئ",
  )
  val badBarcode: String get() = t(
    "این بارکد معتبر نیست — نتیجه‌ها با نام است. ",
    "This barcode isn't valid — results are by name. ",
    "دا بارکوډ سم نه دی — پایلې د نوم له مخې دي. ",
  )
  val nothingFound: String get() = t(
    "چیزی پیدا نشد. می‌توانید عکسِ خودتان را بگذارید.",
    "Nothing found. You can add your own photo.",
    "هېڅ ونه موندل شو. کولی شئ خپل انځور کېږدئ.",
  )
  fun resultsCount(n: Any?): String = t("${asIs(n)} نتیجه", "$n ${pl(n ?: 0, "result", "results")}", "$n پایلې")
  val moreExist: String get() = t(" (بیشتر هم هست)", " (there are more)", " (نورې هم شته)")
  fun offlineCached(ago: Any?): String = t(
    "بی اینترنت — نتیجه‌های ذخیره‌شده از ${asIs(ago)} (آخرین همگام‌سازی). ",
    "No internet — results saved $ago (last sync). ",
    "انټرنېټ نشته — پایلې چې $ago خوندي شوې (وروستۍ همغږي). ",
  )
  val bankMayChanged: String get() = t(
    "ممکن است بانک از آن زمان عوض شده باشد؛ انتخاب فقط با اینترنت. ",
    "The bank may have changed since then; choosing needs the internet. ",
    "کېدای شي بانک له هغه وخته بدل شوی وي؛ ټاکل یوازې له انټرنېټ سره. ",
  )
  val offlineNoCache: String get() = t(
    "اینترنت قطع است یا سرور در دسترس نیست — برای این جست‌وجو نتیجهٔ ذخیره‌شده‌ای هم روی این دستگاه نیست.",
    "The internet is down or the server can't be reached — there are no saved results for this search on this device either.",
    "انټرنېټ پرې دی یا سرور ته لاسرسی نشته — د دې لټون لپاره په دې وسیله خوندي پایلې هم نشته.",
  )
  fun serverError(msg: Any?): String = t("خطای سرور: ${asIs(msg)}", "Server error: $msg", "د سرور تېروتنه: $msg")
  val replyUnread: String get() = t("پاسخِ سرور خوانده نشد", "The server's reply couldn't be read", "د سرور ځواب ونه لوستل شو")
  val barcodeOrName: String get() = t("بارکد یا نامِ کالا", "Barcode or product name", "بارکوډ یا د توکي نوم")
  val scanBtn: String get() = t("اسکن", "Scan", "سکن")
  val approvedOnly: String get() = t(
    "فقط کالاهایی که عکسِ تأییدشده دارند",
    "Only products with an approved photo",
    "یوازې هغه توکي چې تایید شوی انځور لري",
  )
  val useOwnPhoto: String get() = t("عکسِ خودم را می‌گذارم", "I'll use my own photo", "خپل انځور به کېږدم")
  val moreResults: String get() = t("نتیجه‌های بیشتر", "More results", "نورې پایلې")
  val underMinute: String get() = t("کمتر از یک دقیقه پیش", "less than a minute ago", "له یوې دقیقې لږ مخکې")
  fun exactMatch(kind: Any?): String = t("تطبیقِ دقیق: ${asIs(kind)}", "Exact match: $kind", "دقیق سمون: $kind")
  fun likelyMatch(kind: Any?): String = t("محتمل: ${asIs(kind)}", "Likely: $kind", "احتمالي: $kind")
  val similarNameKind: String get() = t("نامِ مشابه", "Similar name", "ورته نوم")
  val similarBrand: String get() = t("برندِ مشابه", "Similar brand", "ورته برانډ")
  fun severalKinds(diff: Any?): String = t("چند نوع — فرق در ${asIs(diff)}", "Several kinds — differ in $diff", "څو ډولونه — توپیر په $diff کې")
  fun packOf(n: Any?): String = t("${asIs(n)} عددی", "Pack of $n", "$n دانې")

  // مشخصاتِ نتیجه
  val backToResults: String get() = t("بازگشت به نتیجه‌ها", "Back to results", "پایلو ته بېرته")
  val noApprovedPhoto: String get() = t(
    "این کالا در بانک عکسِ تأییدشده ندارد",
    "This product has no approved photo in the bank",
    "دا توکی په بانک کې تایید شوی انځور نه لري",
  )
  val flavorModel: String get() = t("طعم/مدل", "Flavour/model", "خوند/ماډل")
  val weightVolume: String get() = t("وزن/حجم", "Weight/volume", "وزن/حجم")
  val perPack: String get() = t("تعداد در بسته", "Count per pack", "په بسته کې شمېر")
  val packaging: String get() = t("بسته‌بندی", "Packaging", "بسته‌بندي")
  val photoOnly: String get() = t(
    "قیمت، موجودی، قفسه و SKUِ شما از بانک کپی نمی‌شود؛ فقط عکس.",
    "Your price, stock, shelf and SKU aren't copied from the bank; only the photo.",
    "ستاسو بیه، موجودي، الماري او SKU له بانک څخه نه کاپي کېږي؛ یوازې انځور.",
  )
  val replacesPhoto: String get() = t(
    "این کالا الان عکس دارد؛ با انتخاب، این عکس جایش را می‌گیرد.",
    "This product already has a photo; choosing replaces it with this one.",
    "دا توکی اوس انځور لري؛ په ټاکلو سره دا انځور د هغه ځای نیسي.",
  )
  val notSelectable: String get() = t(" — انتخاب نمی‌شود", " — can't be chosen", " — نه ټاکل کېږي")
  val iSawDetails: String get() = t(
    "مشخصات را دیدم؛ این عکس واقعاً همین کالای من است",
    "I've seen the details; this photo really is my product",
    "مشخصات مې ولیدل؛ دا انځور رښتیا زما همدا توکی دی",
  )
  val staleResult: String get() = t(
    "این نتیجه از کشِ بی‌اینترنتی است؛ برای انتخاب، با اینترنت دوباره جست‌وجو کنید",
    "This result is from the offline cache; to choose, search again with the internet",
    "دا پایله د بې‌انټرنېټه زېرمې ده؛ د ټاکلو لپاره له انټرنېټ سره بیا ولټوئ",
  )
  val notCertain: String get() = t(
    "تطبیق قطعی نیست — مشخصات را ببینید و تیک بزنید",
    "The match isn't certain — check the details and tick the box",
    "سمون یقیني نه دی — مشخصات وګورئ او نښه یې کړئ",
  )
  val barcodeMismatch: String get() = t(
    "بارکدِ کالای شما با کالای این عکس فرق دارد",
    "Your product's barcode differs from this photo's product",
    "ستاسو د توکي بارکوډ د دې انځور له توکي سره توپیر لري",
  )
  val noLongerSelectable: String get() = t(
    "این عکس دیگر در بانک قابلِ انتخاب نیست",
    "This photo can no longer be chosen in the bank",
    "دا انځور نور په بانک کې د ټاکلو وړ نه دی",
  )
  val offlineRetry: String get() = t("اینترنت قطع است — دوباره امتحان کنید", "The internet is down — try again", "انټرنېټ پرې دی — بیا هڅه وکړئ")
  val photoNotTaken: String get() = t("عکس گرفته نشد", "The photo wasn't taken", "انځور وانه خیستل شو")
  val chooseThisPhoto: String get() = t("انتخاب این عکس", "Choose this photo", "دا انځور وټاکئ")

  // ── `BankPhotoSource.kt` ──

  // منبعِ عکسِ کالا
  val sourceOffline: String get() = t(
    "منبعِ عکس خوانده نشد — اینترنت قطع است",
    "The photo source couldn't be read — the internet is down",
    "د انځور سرچینه ونه لوستل شوه — انټرنېټ پرې دی",
  )
  val sourceUnread: String get() = t("منبعِ عکس خوانده نشد", "The photo source couldn't be read", "د انځور سرچینه ونه لوستل شوه")
  val notCertainUseBank: String get() = t(
    "تطبیق قطعی نیست — از «انتخاب از بانکِ تصاویر» با دیدنِ مشخصات انتخاب کنید",
    "The match isn't certain — choose from “Choose from the image bank” after seeing the details",
    "سمون یقیني نه دی — د مشخصاتو له لیدو وروسته له «د انځورونو له بانک څخه ټاکل» وټاکئ",
  )
  val versionNotApproved: String get() = t(
    "آن نسخهٔ عکس تأییدشده نیست",
    "That version of the photo isn't approved",
    "د انځور هغه نسخه تایید شوې نه ده",
  )
  val consentNeeded: String get() = t("اجازهٔ صریح لازم است", "Explicit permission is required", "ښکاره اجازه اړینه ده")
  val notDone: String get() = t("انجام نشد", "Not done", "ونه شو")
  val photoSource: String get() = t("منبعِ عکس", "Photo source", "د انځور سرچینه")
  val consentText: String get() = t(
    "اجازه می‌دهم VILL3N این عکس را بررسی کند و اگر پذیرفت، در بانکِ مرکزی برای دکان‌های دیگر هم بگذارد",
    "I allow VILL3N to review this photo and, if accepted, put it in the central bank for other shops too",
    "زه VILL3N ته اجازه ورکوم چې دا انځور وڅېړي او که ومنل شو، د نورو دوکانونو لپاره یې هم په مرکزي بانک کې کېږدي",
  )
  val proposeCentral: String get() = t("پیشنهاد به بانکِ مرکزی", "Propose to the central bank", "مرکزي بانک ته وړاندیز")
  // §۳۷.۷ «گزارشِ عکسِ اشتباه یا بستهٔ قدیمی» (عکسِ مرکزی)
  val reportPhoto: String get() = t("گزارشِ عکسِ نادرست", "Report a wrong photo", "د ناسم انځور راپور")
  val reportWrongProduct: String get() = t("کالای دیگری است", "It is another product", "بل توکی دی")
  val reportOldPackaging: String get() = t("بسته‌بندیِ قدیمی", "Old packaging", "زوړ بسته‌بندي")
  val reportQuality: String get() = t("کیفیتِ بد", "Poor quality", "خراب کیفیت")
  val reportOther: String get() = t("چیزِ دیگر", "Something else", "بل څه")
  val reportNote: String get() = t("توضیح (اختیاری)", "Note (optional)", "تشریح (اختیاري)")
  val reportSend: String get() = t("فرستادنِ گزارش", "Send report", "راپور لېږل")
  val reportSent: String get() = t("گزارش به بانکِ مرکزی رسید؛ عکس تا بررسیِ مدیر همان می‌ماند", "The report reached the central bank; the photo stays until an admin reviews it", "راپور مرکزي بانک ته ورسېد؛ انځور د مدیر تر کتنې پورې همداسې پاتې کېږي")
  val backToCentral: String get() = t("بازگشت به عکسِ مرکزی", "Back to the central photo", "مرکزي انځور ته بېرته")
  fun newerCentral(v: Any?): String = t("گذاشتنِ نسخهٔ تازهٔ مرکزی (${asIs(v)})", "Use the new central version ($v)", "د مرکزي نوې نسخې ایښودل ($v)")
  fun previousVersion(v: Any?): String = t("بازگشت به نسخهٔ قبلی (${asIs(v)})", "Back to the previous version ($v)", "مخکنۍ نسخې ته بېرته ($v)")
  fun centralVersion(v: Any?): String = t("عکسِ بانکِ مرکزی، نسخهٔ ${asIs(v)}", "Central bank photo, version $v", "د مرکزي بانک انځور، $v نسخه")
  val thisDevicePhoto: String get() = t(
    "عکسِ همین دستگاه (بیرون از بانک)",
    "This device's photo (outside the bank)",
    "د همدې وسیلې انځور (له بانک بهر)",
  )
  val shownInStore: String get() = t(
    "همین عکس در ویترینِ مشتری دیده می‌شود",
    "This photo is shown in the customer storefront",
    "دا انځور د پېرودونکي په هټۍ کې ښکاري",
  )
  val placeholderNote: String get() = t(
    "ویترین جای خالی نشان می‌دهد، نه عکسِ کالای دیگر",
    "The storefront shows an empty placeholder, not another product's photo",
    "هټۍ تش ځای ښيي، نه د بل توکي انځور",
  )
  val viaSync: String get() = t(
    "از راهِ همگام‌سازیِ همیشگی؛ در بانکِ تصاویر نیست",
    "Via the usual sync; not in the image bank",
    "د تل والا همغږۍ له لارې؛ د انځورونو په بانک کې نشته",
  )
  val userConfirmed: String get() = t(
    "تطبیق با بارکد نبود؛ خودتان تأیید کردید",
    "Not matched by barcode; you confirmed it yourself",
    "د بارکوډ له مخې سمون نه و؛ تاسو پخپله تایید کړ",
  )
  val archivedInBank: String get() = t(
    "از بانک بایگانی شده؛ همین‌جا می‌ماند ولی دیگر انتخاب‌شدنی نیست",
    "Archived in the bank; it stays here but can no longer be chosen",
    "په بانک کې آرشیف شوی؛ دلته پاتې کېږي خو نور د ټاکلو وړ نه دی",
  )
  val centralKept: String get() = t(
    "عکسِ مرکزیِ قبلی برای بازگشت نگه داشته شده",
    "The previous central photo is kept so you can go back",
    "مخکینی مرکزي انځور د بېرته تګ لپاره ساتل شوی",
  )
  val proposalPending: String get() = t(
    "پیشنهاد به بانکِ مرکزی در انتظارِ بررسی است",
    "The proposal to the central bank is awaiting review",
    "مرکزي بانک ته وړاندیز د څېړنې په تمه دی",
  )
  val proposalApproved: String get() = t(
    "پیشنهادتان در بانکِ مرکزی پذیرفته شد",
    "Your proposal was accepted in the central bank",
    "ستاسو وړاندیز په مرکزي بانک کې ومنل شو",
  )

  // ── `ImageBulkImport.kt` ──

  // ورودِ گروهیِ عکس‌ها
  fun preparingFiles(n: Any?): String = t(
    "در حالِ آماده‌سازیِ ${asIs(n)} فایل…",
    "Preparing $n ${pl(n ?: 0, "file", "files")}…",
    "د $n فایلونو چمتو کول…",
  )
  fun preparingPhoto(i: Any?, n: Any?): String = t(
    "در حالِ آماده‌سازیِ عکسِ ${asIs(i)} از ${asIs(n)}…",
    "Preparing photo $i of $n…",
    "د $n څخه د $i انځور چمتو کول…",
  )
  val readHalfDone: String get() = t(
    "خواندنِ فایل‌ها نیمه‌کاره ماند — دوباره انتخاب کنید",
    "Reading the files stopped halfway — choose them again",
    "د فایلونو لوستل نیمګړي پاتې شول — بیا یې وټاکئ",
  )
  val netLostStopped: String get() = t(
    "اینترنت قطع شد — ورود متوقف شد؛ بعداً «ادامهٔ ورود» را بزنید",
    "The internet dropped — import stopped; press “Continue import” later",
    "انټرنېټ پرې شو — راوړل ودرېدل؛ وروسته «د راوړلو دوام» کېکاږئ",
  )
  val lowMemory: String get() = t(
    "حافظهٔ دستگاه برای این عکس کافی نبود",
    "The device didn't have enough memory for this photo",
    "وسیلې د دې انځور لپاره بسنه حافظه نه درلوده",
  )
  fun replaceCount(n: Any?): String = t("${asIs(n)} عکس جایگزین می‌شود", "$n ${pl(n ?: 0, "photo", "photos")} will be replaced", "$n انځورونه بدلېږي")
  fun replaceNote(n: Any?): String = t(
    "عکسِ فعلیِ ${asIs(n)} کالا با عکسِ تازه جایگزین می‌شود و عکسِ قبلی برنمی‌گردد.",
    "The current photo of $n ${pl(n ?: 0, "product", "products")} is replaced with the new photo and the old photo doesn't come back.",
    "د $n توکو اوسنی انځور په نوي انځور بدلېږي او پخوانی انځور بېرته نه راځي.",
  )
  val replaceBtn: String get() = t("جایگزین کن", "Replace", "بدل یې کړه")
  val bulkTitle: String get() = t("ورودِ گروهیِ عکسِ کالاها", "Bulk product photo import", "د توکو د انځورونو ډله‌ییز راوړل")
  val bulkHelp: String get() = t(
    "نامِ هر فایل را بارکد یا نامِ دقیقِ کالا بگذارید (مثلاً 6261234567890.jpg)، یا یک CSV با ستون‌های barcode/name و image_filename کنارشان انتخاب کنید. فایلِ مبهم یا بی‌تطبیق حدس زده نمی‌شود.",
    "Name each file with the barcode or the exact product name (e.g. 6261234567890.jpg), or choose a CSV with barcode/name and image_filename columns next to them. An ambiguous or unmatched file isn't guessed.",
    "هر فایل ته د بارکوډ یا د توکي دقیق نوم ورکړئ (لکه 6261234567890.jpg)، یا ورسره یو CSV د barcode/name او image_filename ستنو سره وټاکئ. مبهم یا بې‌سمونه فایل اټکل نه کېږي.",
  )
  val shrinkBank: String get() = t(
    "هر عکس روی دستگاه کوچک می‌شود (۱۲۰۰ و ۴۰۰ پیکسل، بی EXIF/مکان)، عکسِ کالا می‌شود و در بانکِ تصاویرِ همین دکان هم ثبت می‌شود.",
    "Each photo is shrunk on the device (1200 and 400 pixels, no EXIF/location), becomes the product photo and is also recorded in this shop's image bank.",
    "هر انځور په وسیله کوچنی کېږي (۱۲۰۰ او ۴۰۰ پکسله، بې EXIF/ځای)، د توکي انځور کېږي او د همدې دوکان د انځورونو په بانک کې هم ثبتېږي.",
  )
  val shrinkSync: String get() = t(
    "هر عکس روی دستگاه کوچک می‌شود (بی EXIF/مکان) و عکسِ کالا می‌شود؛ مثلِ همیشه با حساب همگام می‌شود.",
    "Each photo is shrunk on the device (no EXIF/location) and becomes the product photo; it syncs with the account as always.",
    "هر انځور په وسیله کوچنی کېږي (بې EXIF/ځای) او د توکي انځور کېږي؛ د تل په څېر له حساب سره همغږي کېږي.",
  )
  fun prevHalfDone(n: Any?): String = t(
    "ورودِ قبلی نیمه‌کاره ماند — ${asIs(n)} ردیف مانده",
    "The previous import stopped halfway — $n ${pl(n ?: 0, "row", "rows")} left",
    "پخوانی راوړل نیمګړی پاتې شو — $n قطارونه پاتې دي",
  )
  val choosePhotos: String get() = t("انتخابِ عکس‌ها (و CSV)", "Choose photos (and CSV)", "انځورونه (او CSV) وټاکئ")
  val replaceExisting: String get() = t(
    "عکسِ کالاهایی که از قبل عکس دارند هم جایگزین شود",
    "Also replace photos of products that already have one",
    "د هغو توکو انځورونه هم بدل شي چې دمخه انځور لري",
  )
  fun dupCount(n: Any?): String = t("${asIs(n)} تکراری", "$n duplicate", "$n تکراري")
  fun ambiguousCount(n: Any?): String = t("${asIs(n)} مبهم", "$n ambiguous", "$n مبهم")
  fun unmatchedCount(n: Any?): String = t("${asIs(n)} بی‌تطبیق", "$n unmatched", "$n بې‌سمونه")
  fun hasPhotoCount(n: Any?): String = t("${asIs(n)} عکس دارد", "$n have a photo", "$n انځور لري")
  val noFileYet: String get() = t("هنوز فایلی انتخاب نشده.", "No file chosen yet.", "لا کوم فایل نه دی ټاکل شوی.")
  val continueImport: String get() = t("ادامهٔ ورود", "Continue import", "د راوړلو دوام")
  val startImport: String get() = t("شروعِ ورود", "Start import", "راوړل پیل کړئ")
  val clearList: String get() = t("پاک کردنِ فهرست", "Clear the list", "لړلیک پاک کړئ")
  val howFileBarcode: String get() = t("بارکد در نامِ فایل", "Barcode in the file name", "د فایل په نوم کې بارکوډ")
  val howFullName: String get() = t("نامِ کاملِ کالا", "Full product name", "د توکي بشپړ نوم")
  val howCsvBarcode: String get() = t("بارکدِ ردیفِ CSV", "CSV row barcode", "د CSV قطار بارکوډ")
  val howCsvName: String get() = t("نامِ ردیفِ CSV", "CSV row name", "د CSV قطار نوم")
  val howManual: String get() = t("انتخابِ شما", "Your choice", "ستاسو ټاکنه")
  val whichProduct: String get() = t("کدام کالا؟", "Which product?", "کوم توکی؟")
  val barcodeOrExact: String get() = t("بارکد یا نامِ دقیق", "Barcode or exact name", "بارکوډ یا دقیق نوم")
  val matchBtn: String get() = t("تطبیق", "Match", "سمون")
  val noSuchProduct: String get() = t(
    "کالایی با این بارکد یا نام پیدا نشد (یا بیش از یکی بود)",
    "No product with this barcode or name was found (or there was more than one)",
    "په دې بارکوډ یا نوم کوم توکی ونه موندل شو (یا له یوه ډېر وو)",
  )
}
