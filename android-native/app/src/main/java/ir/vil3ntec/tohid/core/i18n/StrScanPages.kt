package ir.vil3ntec.tohid.core.i18n

/**
 *  اسکنِ بارکد برای پُر کردنِ یک کادر (`ScanSheet.kt`)، دوربینِ گوشی برای کامپیوتر (`PhoneLinkHost.kt`) و بسته‌ها و بارکدِ هر
 *  واحد در فرمِ کالا (`PackEditor.kt`) — صفحه‌های زندهٔ کوچکِ `ui/screens` که پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی
 *  نمی‌گذشتند (LOC-08 دری، دورِ ۵). فارسی کلمه‌به‌کلمه همان پیشین؛ هر جا همان جمله در جدولِ دیگری بود (آماده‌سازی و اجازهٔ
 *  دوربین، «گرفتنِ عکس»، «بعداً»…) همان دوباره به کار رفته. نوشته‌ای که روی کامپیوتر از «دوربین/گوشی ِ همین دستگاه» می‌گوید `td(…)`
 *  است (فارسیِ کامپیوتر را جدولِ `phrases` ِ `desktop/build.gradle.kts` می‌سازد). «گوشی» و «کامپیوتر» ِ `PhoneLinkHost` نامِ دستگاهِ
 *  آن سرِ کار است (گوشی عکس می‌فرستد، کامپیوتر می‌گیرد)، پس روی هر دو همان می‌ماند و `t(…)` است. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrScanPages {
  // ── `ScanSheet.kt` ──
  val scanBarcode: String get() = t("اسکن بارکد", "Scan barcode", "بارکوډ سکن کړئ")
  val scanOnce: String get() = td(
    "بارکد را جلوی دوربین بگیرید — با اولین خواندن، کادر پر می‌شود",
    "Hold the barcode up to the camera — the field fills on the first read",
    "بارکوډ کمرې ته ونیسئ — په لومړي لوستلو خونه ډکېږي",
    "Scan the barcode with the barcode scanner — the field fills on the first read",
    "بارکوډ په بارکوډ لوستونکي سکن کړئ — په لومړي لوستلو خونه ډکېږي",
  )

  // ── `PhoneLinkHost.kt` (گوشی: کامپیوتر عکس خواست؛ کامپیوتر: عکسِ گوشی رسید) ──
  val pcWantsPhoto: String get() = t("کامپیوتر عکس می‌خواهد", "The computer wants a photo", "کمپیوټر انځور غواړي")
  fun takePhotoOf(name: String): String = t(
    "عکسِ «${asIs(name)}» را بگیرید؛ همان لحظه در کامپیوتر باز می‌شود.",
    "Take a photo of “$name”; it opens on the computer right away.",
    "د «$name» انځور واخلئ؛ سمدستي په کمپیوټر کې پرانیستل کېږي.",
  )
  val sendingToPc: String get() = t("در حالِ فرستادن به کامپیوتر…", "Sending to the computer…", "کمپیوټر ته لېږل کېږي…")
  val phonePhotoNotReceived: String get() = t("عکسِ گوشی دریافت نشد", "The phone's photo wasn't received", "د موبایل انځور ترلاسه نه شو")
  fun phonePhotoSet(name: String): String = t(
    "عکسِ گوشی روی «${asIs(name)}» نشست",
    "The phone's photo is now on “$name”",
    "د موبایل انځور پر «$name» کېښودل شو",
  )

  // ── `PackEditor.kt` (`u`: واحدِ کالا، دادهٔ کاربر) ──
  val packsTitle: String get() = t("بسته‌ها و بارکدِ هر واحد (اختیاری)", "Packs and per-unit barcodes (optional)", "بستې او د هر واحد بارکوډ (اختیاري)")
  fun packHelp1(u: String): String = t(
    "مثلاً «کارتن» = ۲۴ ${asIs(u)} با بارکدِ روی کارتن؛ اسکنِ آن ۲۴ ${asIs(u)} به سبد می‌برد و قیمت همان قیمتِ هر ${asIs(u)} است. ",
    "For example “carton” = 24 $u with the barcode on the carton; scanning it puts 24 $u in the cart at the price of each $u. ",
    "د بېلګې په توګه «کارتن» = ۲۴ $u د کارتن پر بارکوډ سره؛ د هغه سکن ۲۴ $u ټوکرۍ ته وړي او بیه یې د هر $u همغه بیه ده. ",
  )
  fun packHelp2(u: String): String = t(
    "قیمتِ بسته (اختیاری) وقتی است که در صندوق واحدِ ردیف «کارتن» شود؛ خالی ⇒ اندازه × قیمتِ هر ${asIs(u)}.",
    "The pack price (optional) is used when the line's unit at the till becomes “carton”; empty ⇒ size × price of each $u.",
    "د بستې بیه (اختیاري) هغه وخت ده چې په دخل کې د کرښې واحد «کارتن» شي؛ تش ⇒ اندازه × د هر $u بیه.",
  )
  val packName: String get() = t("نامِ بسته", "Pack name", "د بستې نوم")
  fun howMany(u: String): String = t("چند ${asIs(u)}", "How many $u", "څو $u")
  /** نامِ جای خالیِ بسته‌ای که هنوز نام ندارد */
  val pack: String get() = t("بسته", "Pack", "بسته")
  fun packBarcodes(name: String): String = t("بارکدهای ${asIs(name)} (با «،» جدا)", "$name barcodes (separated by “,”)", "د $name بارکوډونه (په «،» جلا)")
  fun packPrice(name: String): String = t("قیمتِ ${asIs(name)}", "$name price", "د $name بیه")
  val addPack: String get() = t("+ بسته (کارتن، شل، …)", "+ Pack (carton, bundle, …)", "+ بسته (کارتن، بنډل، …)")
}
