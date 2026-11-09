package ir.vil3ntec.tohid.core.i18n

/**
 *  انبارگردانیِ تازه (INV-06 اسکن، INV-07 چند دستگاه، INV-08 کور) — پنجرهٔ «انبارگردانی» و کلیدِ «تنظیمات».
 *
 *  قاعده‌ها سرِ `I18n.kt`. ⛔ یادداشتِ انبارگردانی («انبارگردانی — …») و برگهٔ اکسل داده‌اند و همان فارسی می‌مانند.
 */
@Suppress("unused")
object StrCount {

  val title: String get() = t("انبارگردانی", "Stock count", "د زېرمې شمېرنه")
  val subtitle: String get() = t(
    "بارکد را اسکن کنید یا عدد را بنویسید؛ خانهٔ خالی یعنی شمرده نشد و دست نمی‌خورد",
    "Scan barcodes or type the count; an empty box means not counted and left unchanged",
    "بارکوډ سکن کړئ یا شمېر ولیکئ؛ تش خونه یعنې نه ده شمېرل شوې او نه بدلېږي",
  )
  val blindSubtitle: String get() = t(
    "شمارشِ کور: فقط آنچه در قفسه هست را بشمارید؛ عددِ برنامه دیده نمی‌شود",
    "Blind count: count only what is on the shelf; the system quantity is hidden",
    "ړنده شمېرنه: یوازې هغه وشمېرئ چې په المارۍ کې دي؛ د پروګرام شمېر نه ښکاري",
  )
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val scan: String get() = t("اسکنِ بارکد — هر اسکن یک عدد", "Scan a barcode — one unit per scan", "بارکوډ سکن کړئ — هر سکن یو عدد")
  val camera: String get() = t("دوربین", "Camera", "کمره")
  val search: String get() = t("جست‌وجوی کالا یا بارکد…", "Search products or barcodes…", "توکي یا بارکوډ ولټوئ…")
  val note: String get() = t("یادداشت (مثلاً: قفسهٔ برنج)", "Note (e.g. rice shelf)", "یادښت (لکه: د وریجو المارۍ)")
  val counted: String get() = t("شمرده", "Counted", "شمېرل شوي")
  fun system(q: Any, unit: Any): String = t("برنامه: ${asIs(q)} ${asIs(unit)}", "System: $q $unit", "پروګرام: $q $unit")
  fun others(q: Any): String = t("دستگاه‌های دیگر: ${asIs(q)}", "Other devices: $q", "نورې وسیلې: $q")
  val sharedMark: String get() = t("در چند برگه شمرده شده", "Counted on more than one sheet", "په څو پاڼو کې شمېرل شوی")
  fun scanned(name: Any, q: Any): String = t("«${asIs(name)}»: ${asIs(q)}", "“$name”: $q", "«$name»: $q")
  fun unknown(code: Any): String = t("بارکدِ «${asIs(code)}» مالِ هیچ کالایی نیست", "Barcode “$code” doesn't belong to any product", "بارکوډ «$code» د هېڅ توکي نه دی")
  fun weighed(name: Any): String = t("«${asIs(name)}» وزنی است؛ وزنش را بنویسید", "“$name” is sold by weight; type its weight", "«$name» وزني دی؛ وزن یې ولیکئ")
  fun inactive(name: Any): String = t("«${asIs(name)}» غیرفعال است", "“$name” is inactive", "«$name» غیرفعال دی")
  /** بارکدخوان روی صفحهٔ انبارگردانی با «ابزارهای تازهٔ دستگاه» ِ خاموش (`CountState.scan`) */
  val scanOff: String get() = t(
    "بارکد خوانده شد ولی شمرده نشد؛ این صفحه و عددهایش سرِ جایشان است. برای شمردن با بارکدخوان، «ابزارهای تازهٔ دستگاه» را در «دستگاه‌ها» روشن کنید.",
    "Barcode read but not counted; this page and its numbers are kept. To count with the scanner, turn on “New device tools” in “Devices”.",
    "بارکوډ ولوستل شو خو ونه شمېرل شو؛ دا پاڼه او شمېرې یې خپل ځای دي. د بارکوډ لوستونکي سره د شمېرلو لپاره، په «وسیلې» کې «د وسیلو نوې اوزار» روښانه کړئ.",
  )
  fun badNumber(name: Any): String = t("عددِ «${asIs(name)}» درست نیست", "The count for “$name” isn't valid", "د «$name» شمېر سم نه دی")

  // خلاصه و ثبت
  fun summaryCounted(n: Any): String = t("شمرده: ${asIs(n)}", "Counted: $n", "شمېرل شوي: $n")
  fun summaryDiff(n: Any, value: Any): String = t("اختلاف: ${asIs(n)} · ارزش: ${asIs(value)} ؋", "Differences: $n · value: $value ؋", "توپیر: $n · ارزښت: $value ؋")
  val save: String get() = t("ثبتِ شمارش", "Save count", "شمېرنه ثبتول")

  // بازبینی و صافی پیش از ثبت (S20: INV-09، INV-05)
  val all: String get() = t("همه", "All", "ټول")
  val allCategories: String get() = t("همهٔ دسته‌ها", "All categories", "ټولې ډلې")
  val allSuppliers: String get() = t("همهٔ تأمین‌کننده‌ها", "All suppliers", "ټول عرضه‌کوونکي")
  /** INV-05: صافیِ برند و قفسه (فقط وقتی کالایی برند یا قفسه دارد) */
  val allBrands: String get() = t("همهٔ برندها", "All brands", "ټول برندونه")
  val allShelves: String get() = t("همهٔ قفسه‌ها", "All shelves", "ټولې المارۍ")
  fun uncounted(n: Any): String = t("شمرده‌نشده: ${asIs(n)}", "Not counted: $n", "نه شمېرل شوي: $n")
  fun matched(n: Any): String = t("منطبق: ${asIs(n)}", "Matching: $n", "سمون لرونکي: $n")
  fun differing(n: Any, value: Any): String = t("مغایر: ${asIs(n)} · ${asIs(value)} ؋", "Different: $n · $value ؋", "توپیر لرونکي: $n · $value ؋")
  val untouched: String get() = t("کالاهای شمرده‌نشده دست نمی‌خورند", "Products that aren't counted are left unchanged", "نه شمېرل شوي توکي نه بدلېږي")
  // ── INV-09: «مستثنا» و «شمارشِ دوباره» ──
  fun excludedChip(n: Any): String = t("مستثنا: ${asIs(n)}", "Excluded: $n", "مستثنا: $n")
  val exclude: String get() = t("مستثنا", "Exclude", "مستثنا")
  val includeAgain: String get() = t("برگرداندن", "Include", "بېرته")
  val excludedMark: String get() = t("مستثنا — موجودی‌اش دست نمی‌خورد", "Excluded — its stock stays unchanged", "مستثنا — موجودي یې نه بدلېږي")
  fun recountButton(n: Any): String = t("شمارشِ دوبارهٔ مغایرها (${asIs(n)})", "Recount the differences ($n)", "د توپیر لرونکو بیا شمېرنه ($n)")
  fun recountStarted(n: Any): String = t("${asIs(n)} کالای مغایر خالی شد؛ دوباره بشمارید.", "$n differing items cleared; count them again.", "$n توپیر لرونکي توکي تش شول؛ بیا یې وشمېرئ.")
  val recountMark: String get() = t("شمارشِ دوباره — هنوز شمرده نشده", "Recount — not counted yet", "بیا شمېرنه — لا نه دی شمېرل شوی")
  val csv: String get() = t("CSV", "CSV", "CSV")

  // چند دستگاه
  val share: String get() = t("فرستادنِ برگهٔ این دستگاه", "Send this device's sheet", "د دې وسیلې پاڼه لېږل")
  val importSheet: String get() = t("افزودنِ برگهٔ دستگاهِ دیگر", "Add another device's sheet", "د بلې وسیلې پاڼه ورزیاتول")
  val multiNote: String get() = t(
    "چند دستگاه با هم: هر دستگاه جای خودش را بشمارد و «فرستادنِ برگه» بزند؛ این‌جا «افزودنِ برگه» شمارشِ همه را جمع می‌کند. برگهٔ تکراری دو بار جمع نمی‌شود.",
    "Several devices at once: each device counts its own area and taps “Send this device's sheet”; here, “Add another device's sheet” adds everyone's counts. The same sheet is never added twice.",
    "څو وسیلې یوځای: هره وسیله خپل ځای وشمېري او «پاڼه لېږل» ووهي؛ دلته «پاڼه ورزیاتول» د ټولو شمېرنه جمع کوي. تکراري پاڼه دوه ځله نه جمع کېږي.",
  )
  fun sheetChip(device: Any, n: Any): String = t("${asIs(device)} · ${asIs(n)} کالا", "$device · $n ${pl(n, "item", "items")}", "$device · $n ${pl(n, "توکی", "توکي")}")
  val remove: String get() = t("برداشتن", "Remove", "لرې کول")
  val badSheet: String get() = t("این فایل برگهٔ انبارگردانی نیست یا خراب است", "This file isn't a stock-count sheet or is damaged", "دا دوتنه د شمېرنې پاڼه نه ده یا خرابه ده")
  val ownSheet: String get() = t("این برگهٔ خودِ همین شمارش است", "This is this count's own sheet", "دا د همدې شمېرنې خپله پاڼه ده")
  fun sheetAdded(device: Any): String = t("برگهٔ «${asIs(device)}» افزوده شد", "Sheet from “$device” added", "د «$device» پاڼه ورزیاته شوه")
  val readFailed: String get() = t("فایل خوانده نشد", "The file couldn't be read", "دوتنه ونه لوستل شوه")
  val writeFailed: String get() = t("فایل نوشته نشد", "The file couldn't be saved", "دوتنه خوندي نه شوه")
  val thisDevice: String get() = t("این دستگاه", "This device", "دا وسیله")
  fun fileName(date: Any): String = t("انبارگردانی-${asIs(date)}", "stock-count-$date", "شمېرنه-$date")

  // تنظیمات
  val settingsTitle: String get() = t("انبارگردانیِ تازه", "New stock count", "نوې شمېرنه")
  val settingsOn: String get() = t("اسکن و چند دستگاه", "Scanning and several devices", "سکن او څو وسیلې")
  val settingsNote: String get() = t(
    "پنجرهٔ «انبارگردانی» ِ انبار با اسکن (هر اسکن یک عدد؛ کالای وزنی با نوشتن) و شمارشِ هم‌زمان با چند دستگاه (برگهٔ هر دستگاه با فایل می‌آید و جمع می‌شود)، صافیِ دسته و بازبینیِ شمرده‌نشده/منطبق/مغایر. ثبت «اصلاحِ شمارش» است — حرکتی جدا از اصلاحِ دستی، با همان اثر بر موجودی. پس از تغییر، «انبار» را دوباره باز کنید.",
    "The Warehouse “Stock count” window with scanning (one unit per scan; weighed products are typed) and counting with several devices at once (each device's sheet comes as a file and is added up), a category filter and a review of not counted / matching / different. Saving records “count adjustments” — a movement type of their own, separate from manual adjustments, with the same effect on stock. After changing this, reopen “Warehouse”.",
    "د ګودام «شمېرنې» کړکۍ له سکن سره (هر سکن یو عدد؛ وزني توکي لیکل کېږي) او په یو وخت د څو وسیلو شمېرنه (د هرې وسیلې پاڼه د دوتنې په توګه راځي او جمع کېږي)، د ډلې چاڼ او د نه شمېرل شویو/سمون لرونکو/توپیر لرونکو بیاکتنه. ثبتول «د شمېرنې سمون» دی — له لاسي سمون نه جلا خوځښت، په زېرمه همغه اغېز. له بدلون وروسته «ګودام» بیا پرانیزئ.",
  )
  val blindTitle: String get() = t("شمارشِ کور", "Blind count", "ړنده شمېرنه")
  val blindNote: String get() = t(
    "روی همین دستگاه، شمارنده موجودیِ برنامه، اختلاف و ارزشش را نمی‌بیند و خروجیِ اختلاف ندارد؛ فقط آنچه شمرده را می‌نویسد. اختلاف‌ها پس از ثبت در «گردشِ موجودی» دیده می‌شوند.",
    "On this device the counter does not see the system quantity, the difference or its value, and cannot export differences; they only enter what they counted. Differences show under “Stock movements” after saving.",
    "په دې وسیله کې شمېرونکی د پروګرام زېرمه، توپیر او ارزښت یې نه ویني او د توپیر وتنه نه لري؛ یوازې هغه لیکي چې شمېرلي یې دي. توپیرونه له ثبت وروسته په «د زېرمې ګرځښت» کې ښکاري.",
  )
  val off: String get() = t("خاموش", "Off", "بند")
  val on: String get() = t("روشن", "On", "روښانه")
}
