package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.hw.MonoImage

/**
 *  ورودیِ فاکتورِ نسخهٔ ۲ — آن‌چه برنامه از دفتر و سربرگ به هسته می‌دهد. هسته هیچ مبلغی
 *  حساب نمی‌کند: هر عدد همان فیلدِ ذخیره‌شدهٔ فروش است (`Receipts.input` در برنامه).
 */
data class ReceiptInput(
  val shop: ShopHeader,
  /** `B1-154`؛ خالی ⇒ «—» */
  val invoiceNo: String,
  /** `YYYY-MM-DD` ِ میلادیِ دفتر */
  val dateIso: String,
  /** `14:35`؛ خالی ⇒ فقط تاریخ */
  val time: String,
  val cashier: String,
  /** «نقدی»، «نسیه»، «آنلاین»، «ترکیبی»… */
  val kind: String,
  val cancelled: Boolean,
  val items: List<LineView>,
  val subtotal: Double,
  /** تخفیف‌ها به ترتیبِ چاپ (تخفیفِ ویژه پیش از تخفیفِ دستی)؛ صفرها نمی‌آیند */
  val discounts: List<DiscountLine>,
  val total: Double,
  /** پرداختِ ترکیبی (`Sale.payments`)؛ خالی ⇒ یک ردیفِ «پرداختی» با [paid] */
  val payments: List<PaymentLine>,
  val paid: Double,
  val tendered: Double? = null,
  /** فقط از `Sale.changeMinor` — هرگز حساب نمی‌شود */
  val change: Double? = null,
  val remaining: Double = 0.0,
  /** فقط روی کاغذ، هرگز روی صفحهٔ عمومی */
  val debtorName: String? = null,
  /** نشانیِ QR؛ null ⇒ فروشِ بی توکن یا QR ِ خاموش */
  val qrUrl: String? = null,
  /** «برای دیدنِ این خرید اسکن کنید» زیرِ QR — فقط برای نشانیِ عمومی، نه کدِ داخلیِ برگشت */
  val qrCaption: Boolean = true,
  /**
   *  MKT-10: QR ِ ویترینِ دکان (پیوندِ کمپینِ «QR ِ رسید»، `/store/go/<کد>`) زیرِ فاکتورِ فروش؛ null ⇒ همان رسیدِ امروز.
   *  تنظیمِ همین دستگاه است (پیش‌فرض خاموش) و فقط نشانیِ درست (`CampaignLinks.receiptUrlOk`) چاپ می‌شود.
   */
  val storeQrUrl: String? = null,
  /** لوگوی یک‌بیتیِ همین کاغذ (`LogoDither`)؛ null ⇒ بی لوگو */
  val logo: MonoImage? = null,
  /** «نسخهٔ دوم» و مانندِ آن (#300) */
  val copyMark: String? = null,
  /**
   *  ردیف‌های **آگاهی** زیرِ «جمع اقلام» — «شاملِ تخفیفِ خودکار …» (پیگیریِ #307). از جمع
   *  کم نمی‌شوند: [subtotal] همان جمعِ ستونِ قلم‌هاست و مبلغ‌ها همان پرداختی. صفرها نمی‌آیند.
   */
  val included: List<DiscountLine> = emptyList(),
  /** یادداشتِ فاکتور (POS-11، `SaleNotes.clean`)؛ null یا خالی ⇒ چاپ نمی‌شود */
  val note: String? = null,
)

data class ShopHeader(
  val name: String,
  val address: String = "",
  val phone: String = "",
  val footer: String = "",
)

data class LineView(
  val name: String,
  val qty: Double,
  val unit: String,
  val unitPrice: Double,
  val total: Double,
  val returnedQty: Double = 0.0,
  /** قیمتِ پیش از تخفیفِ ویژه (پیگیریِ #307)؛ null یا ≤ قیمتِ واحد ⇒ چاپ نمی‌شود */
  val listPrice: Double? = null,
  /** یادداشتِ همین ردیف (POS-11)؛ null یا خالی ⇒ چاپ نمی‌شود */
  val note: String? = null,
)

data class DiscountLine(val label: String, val amount: Double)

data class PaymentLine(val label: String, val amount: Double)

/**
 *  قالبِ عدد و متن — همان `LocaleText` ِ برنامه (رقمِ لاتین/فارسی LOC-05، نمادِ پول LOC-07).
 *  هسته قالبِ دومی برای پول ندارد.
 */
interface ReceiptFormat {
  /** مبلغ با نماد: «1,250 افغانی» */
  fun money(afn: Double): String
  /** مبلغ بی نماد: «1,250» */
  fun amount(afn: Double): String
  /** مقدار: «3»، «1.5» */
  fun qty(q: Double): String
  /** رقم‌های یک متن (شمارهٔ فاکتور، ساعت) */
  fun digits(text: String): String
  /** تلفن — جدا و چپ‌به‌راست، با رقمِ انتخابی */
  fun phone(text: String): String
  /** تاریخِ خورشیدیِ عددی از `YYYY-MM-DD` */
  fun date(iso: String): String
  /**
   *  تاریخِ قمریِ اختیاری (LOC-01، بخشِ ۳.۱۴) — null یعنی ردیفش چاپ نشود. پیش‌فرض null است،
   *  پس هر `ReceiptFormat` ِ پیشین همان فاکتورِ امروز را می‌دهد؛ برنامه فقط با تنظیمِ دکان پرش می‌کند.
   */
  fun lunarDate(iso: String): String? = null
}

/** برچسب‌های فاکتور — پیش‌فرضِ دری؛ سازوکارِ سه‌زبانه (#348) همین را جایگزین می‌کند */
data class ReceiptLabels(
  val fallbackShop: String = "فروشگاه",
  val invoiceNo: String = "شمارهٔ فاکتور",
  val date: String = "تاریخ",
  val lunarDate: String = "تاریخِ قمری",
  val cashier: String = "فروشنده",
  val kind: String = "نوع",
  val cancelled: String = "لغو شد",
  val returned: String = "مرجوعی",
  val listPrice: String = "قیمتِ اصلی",
  val count: String = "تعداد اقلام",
  val rows: String = "ردیف",
  val pieces: String = "عدد",
  val subtotal: String = "جمع اقلام",
  val discount: String = "تخفیف",
  val total: String = "مبلغ نهایی",
  val paid: String = "پرداختی",
  val tendered: String = "دریافتی",
  val change: String = "پولِ پس",
  val remaining: String = "باقی‌مانده (نسیه)",
  val debtor: String = "قرض‌دار",
  val phone: String = "تلفن",
  val scan: String = "برای دیدنِ این خرید اسکن کنید",
  /** MKT-10: نوشتهٔ بالای QR ِ ویترین */
  val storeScan: String = "سفارشِ اینترنتی از همین دکان — اسکن کنید",
  val footer: String = "تشکر از خرید شما",
  val cash: String = "نقدی",
  val credit: String = "نسیه",
  val online: String = "آنلاین",
  val note: String = "یادداشت",
) {
  companion object {
    val FA = ReceiptLabels()
  }
}
