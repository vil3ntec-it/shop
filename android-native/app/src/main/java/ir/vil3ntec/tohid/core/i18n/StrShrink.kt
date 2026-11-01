package ir.vil3ntec.tohid.core.i18n

/**
 *  گزارشِ ضایعات و کسری (INV-17) — بخشِ «گزارش‌ها»ی گوشی و کامپیوتر و کلیدِ «تنظیمات».
 *
 *  قاعده‌ها سرِ `I18n.kt`. ⛔ برگهٔ اکسل/چاپ (`ShrinkLoss.sheet`) داده است و فارسی می‌ماند.
 */
@Suppress("unused")
object StrShrink {

  val title: String get() = t("ضایعات و کسری", "Waste and shrinkage", "ضایعات او کمښت")
  val waste: String get() = t("ضایعات", "Waste", "ضایعات")
  val expired: String get() = t("منقضی", "Expired", "تېر شوی")
  val lost: String get() = t("گم‌شده", "Lost", "ورک شوی")
  val shortage: String get() = t("کسریِ شمارش", "Count shortage", "د شمېرنې کمښت")
  val surplus: String get() = t("اضافهٔ شمارش", "Count surplus", "د شمېرنې زیاتوالی")
  val other: String get() = t("اصلاحِ دستی", "Manual adjustment", "لاسي سمون")

  val wasteTotal: String get() = t("ضایعات و منقضی", "Waste and expired", "ضایعات او تېر شوي")
  val shrinkTotal: String get() = t("کسریِ خالص", "Net shrinkage", "خالص کمښت")
  val netLoss: String get() = t("زیانِ کل", "Total loss", "ټول زیان")
  fun ofSales(pct: Any): String = t("$pct٪ فروش", "$pct% of sales", "د پلور $pct٪")
  val noSales: String get() = t("بی فروش در بازه", "No sales in this period", "په دې موده کې پلور نشته")

  val byKind: String get() = t("به تفکیکِ نوع", "By type", "د ډول له مخې")
  val byProduct: String get() = t("بیشترین زیان", "Largest losses", "تر ټولو لوی زیانونه")
  val topReasons: String get() = t("دلیل‌های پرتکرار", "Common reasons", "ډېر تکرار شوي دلیلونه")
  val noReason: String get() = t("بی دلیل", "No reason given", "بې دلیله")
  fun rows(n: Any): String = t("$n ردیف", "$n ${pl(n, "row", "rows")}", "$n ${pl(n, "کرښه", "کرښې")}")
  fun outIn(out: Any, inQty: Any, unit: Any): String = t(
    "بیرون $out، آمده $inQty $unit",
    "out $out, in $inQty $unit",
    "وتلي $out، راغلي $inQty $unit",
  )
  val deletedProduct: String get() = t("کالای حذف‌شده", "Deleted product", "ړنګ شوی توکی")
  fun unpriced(n: Any): String = t(
    "ارزشِ $n ردیف معلوم نیست، چون قیمتِ خریدِ کالا ثبت نشده — در جمع صفر حساب شده است.",
    "The value of $n ${pl(n, "row", "rows")} is unknown because the product has no purchase price — counted as zero.",
    "د $n ${pl(n, "کرښې", "کرښو")} ارزښت نه دی معلوم، ځکه چې د توکي د پېر بیه نه ده ثبت شوې — په ټولګه کې صفر حساب شوی.",
  )
  val empty: String get() = t(
    "در این بازه ضایعات یا اختلافِ موجودی ثبت نشده است.",
    "No waste or stock differences were recorded in this period.",
    "په دې موده کې ضایعات یا د زېرمې توپیر نه دی ثبت شوی.",
  )
  val basis: String get() = t(
    "ارزش به قیمتِ خریدِ امروزِ هر کالا. فروش، خرید و برگشت به تأمین‌کننده جزوِ این گزارش نیستند.",
    "Valued at each product's current purchase price. Sales, purchases and returns to suppliers are not included.",
    "ارزښت د هر توکي د نن ورځې د پېر په بیه. پلور، پېر او عرضه‌کوونکي ته بېرته ورکړه پکې نه دي.",
  )

  // بازه
  val today: String get() = t("امروز", "Today", "نن")
  val week: String get() = t("۷ روز", "7 days", "۷ ورځې")
  val month: String get() = t("این ماه", "This month", "دا میاشت")
  val all: String get() = t("از آغاز", "All time", "له پیله")
  fun span(from: Any, to: Any): String = t("$from تا $to", "$from to $to", "له $from تر $to")

  // تنظیمات
  val settingsTitle: String get() = t("گزارشِ ضایعات و کسری", "Waste and shrinkage report", "د ضایعاتو او کمښت راپور")
  val settingsOn: String get() = t("در «گزارش‌ها» دیده می‌شود", "Shown under “Reports”", "په «راپورونه» کې ښکاري")
  val settingsNote: String get() = t(
    "زبانهٔ تازه در «گزارش‌ها»: ضایعات (برگشتِ خراب، منقضی)، کسری و اضافهٔ انبارگردانی و اصلاح‌های دستیِ هر بازه، با ارزش به قیمتِ خرید، سهم از فروش، کالاهای پرزیان و دلیل‌های پرتکرار؛ با خروجیِ اکسل و چاپ. فقط برای کسی که قیمتِ خرید را می‌بیند. فقط خواندنی است و چیزی نمی‌نویسد. پس از تغییر، «گزارش‌ها» را دوباره باز کنید.",
    "A new tab under “Reports”: waste (damaged returns, expired), stock-count shortages and surpluses and manual adjustments for any period, valued at purchase price, as a share of sales, with the costliest products and the most common reasons; with Excel export and printing. Only for people who can see purchase prices. It is read-only and writes nothing. After changing this, reopen “Reports”.",
    "په «راپورونه» کې نوې پاڼه: ضایعات (خراب بېرته راغلي، تېر شوي)، د زېرمې شمېرنې کمښت او زیاتوالی او لاسي سمونونه د هرې مودې لپاره، د پېر په بیه ارزښت، د پلور برخه، تر ټولو زیانمن توکي او ډېر تکرار شوي دلیلونه؛ د اکسل وتنې او چاپ سره. یوازې د هغه چا لپاره چې د پېر بیه ویني. یوازې د لوستلو لپاره دی او هېڅ نه لیکي. له بدلون وروسته «راپورونه» بیا پرانیزئ.",
  )
  val off: String get() = t("خاموش", "Off", "بند")
  val on: String get() = t("روشن", "On", "روښانه")
}
