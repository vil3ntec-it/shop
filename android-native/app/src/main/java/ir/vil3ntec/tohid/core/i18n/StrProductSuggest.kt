package ir.vil3ntec.tohid.core.i18n

/**
 *  «پیشنهادِ دسته و نام» در فرمِ کالا (AI-06) و بخشِ تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/product/ProductSuggest` (commonMain). واژهٔ «هوش مصنوعی» عمداً فقط در جملهٔ
 *  «در کار نیست» می‌آید: پیشنهاد با قاعده و از دفترِ همین دکان است.
 */
@Suppress("unused")
object StrProductSuggest {
  val settingsTitle: String get() = t("پیشنهادِ دسته و نام", "Category and name suggestions", "د ډلې او نوم وړاندیز")
  val settingsNote: String get() = t(
    "در فرمِ کالا: از روی نامی که می‌نویسید، دسته‌ای که کالاهای هم‌نامِ همین دکان در آن‌اند پیشنهاد می‌شود؛ و «افزودن از متن» فهرستِ چسبانده‌شده را به ردیف‌هایی با همان نام‌ها تبدیل می‌کند. فقط با قاعده و از دفترِ همین دستگاه؛ هوش مصنوعی در کار نیست و چیزی به اینترنت نمی‌رود. قیمت، بارکد، وزن و برند هرگز ساخته نمی‌شوند و هیچ پیشنهادی بی زدنِ شما نوشته نمی‌شود.",
    "In the product form: from the name you type, the category where this shop's products with the same words are is suggested; «Add from text» turns a pasted list into rows with those names. Rule-based and from this device's books only; no AI is involved and nothing goes to the internet. Price, barcode, weight and brand are never made up and no suggestion is written until you tap it.",
    "د توکي په فورمه کې: له هغه نوم چې لیکئ یې، هغه ډله وړاندیز کېږي چې د همدې دوکان همنومه توکي پکې دي؛ او «له متن زیاتول» نښلول شوی لېست د همغو نومونو کتارونو ته اړوي. یوازې د قاعدې له مخې او د همدې وسیلې له دفتر؛ مصنوعي ځیرکتیا نشته او هېڅ شی انټرنېټ ته نه ځي. بیه، بارکوډ، وزن او برانډ هېڅکله نه جوړېږي او هېڅ وړاندیز تر ستاسې ټکولو مخکې نه لیکل کېږي.",
  )
  val suggested: String get() = t("دستهٔ پیشنهادی", "Suggested category", "وړاندیز شوې ډله")
  fun why(word: Any, count: Int, category: Any): String = t(
    "«${asIs(word)}» در نامِ ${asIs(count)} کالای «${asIs(category)}» — بزنید تا برگزیده شود",
    "«$word» is in the name of $count ${pl(count, "product", "products")} in «$category» — tap to choose",
    "«$word» د «$category» د $count توکو په نوم کې دی — د ټاکلو لپاره یې وټکوئ",
  )
  fun whyName(word: Any, category: Any): String = t(
    "«${asIs(word)}» در نامِ دستهٔ «${asIs(category)}» است — بزنید تا برگزیده شود",
    "«$word» is in the category name «$category» — tap to choose",
    "«$word» د «$category» ډلې په نوم کې دی — د ټاکلو لپاره یې وټکوئ",
  )

  // افزودن از متن
  val fromText: String get() = t("افزودن از متن", "Add from text", "له متن زیاتول")
  val pasteTitle: String get() = t("افزودنِ کالا از متن", "Add products from text", "له متن توکي زیاتول")
  val pasteLabel: String get() = t("متن (هر کالا در یک سطر)", "Text (one product per line)", "متن (هر توکی په یوه کرښه)")
  val pastePlaceholder: String get() = t(
    "روغن آفتابگردان ۵ لیتر\nبرنج سیلا ۵ کیلو، دانه دراز",
    "Sunflower oil 5 l\nSella rice 5 kg, long grain",
    "د لمر ګل غوړي ۵ لیتره\nسېلا وريجې ۵ کیلو، اوږده دانه",
  )
  val pasteHelp: String get() = t(
    "از هر سطر فقط نام برداشته می‌شود (پیش از «،» یا « - »). قیمت و بارکدِ درونِ متن کنار می‌رود و جایی نوشته نمی‌شود؛ آن‌ها را خودتان در کادرِ خودش بنویسید.",
    "Only the name is taken from each line (before «,» or « - »). Prices and barcodes in the text are left out and written nowhere; type them into their own fields yourself.",
    "له هرې کرښې یوازې نوم اخیستل کېږي (له «،» یا « - » مخکې). په متن کې بیه او بارکوډ پرېښودل کېږي او چېرته نه لیکل کېږي؛ هغه پخپله په خپل ځای کې ولیکئ.",
  )
  fun addRows(n: Int): String = t("افزودنِ ${asIs(n)} ردیف", "Add $n ${pl(n, "row", "rows")}", "$n کتاره زیاتول")
  val droppedPrice: String get() = t("قیمت برداشته شد", "price left out", "بیه پرېښودل شوه")
  val droppedBarcode: String get() = t("بارکد برداشته شد", "barcode left out", "بارکوډ پرېښودل شو")
  val exists: String get() = t("از پیش در دکان هست", "already in the shop", "مخکې په دوکان کې شته")
  fun skipped(n: Int): String = t(
    "${asIs(n)} سطر نامی نداشت (فقط عدد، قیمت یا بارکد)",
    "$n ${pl(n, "line", "lines")} had no name (only a number, price or barcode)",
    "$n کرښو نوم نه درلود (یوازې شمېره، بیه یا بارکوډ)",
  )
  fun truncated(max: Int): String = t(
    "فقط ${asIs(max)} سطرِ نخست خوانده شد",
    "Only the first $max lines were read",
    "یوازې لومړۍ $max کرښې ولوستل شوې",
  )
  val none: String get() = t("در این متن نامی پیدا نشد.", "No name was found in this text.", "پدې متن کې نوم ونه موندل شو.")
}
