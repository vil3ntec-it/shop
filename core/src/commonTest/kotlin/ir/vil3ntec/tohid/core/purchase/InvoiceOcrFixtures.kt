package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Line

/**
 *  نمونه‌های متنِ فاکتورِ فارسی/دری برای [InvoiceOcrTest] — همان شکلی که `POST /api/shop/purchase-ocr` برمی‌گرداند.
 */
internal object InvoiceOcrFixtures {

  /**
   *  خروجیِ **واقعیِ** Tesseract 5.3.4 (`fas+eng`، `--psm 6`) روی `server/test/fixtures/purchase-ocr/invoice-fa.jpg` (۱۴۰۵/۰۸/۲۶)
   *  با همان خطاهای راستین: «مبلغ» ⇒ «glo»، «۱۲۴۵» ⇒ «۱۳۴۵»، جداکنندهٔ «٬» ⇒ «,»، رقمِ عربیِ «٩» کنارِ فارسی، جای «فی» و
   *  «جمع» در ردیفِ یک جابه‌جا (پاره‌ای LTR)، و «۱٬۳۲۰» ⇒ «۱۱۳۲۰» (جداکننده «۱» خوانده شد).
   */
  val REAL_TESSERACT = listOf(
    Line("شرکت پخش البرز - فاکتور فروش", 89),
    Line("شماره: ۱۳۴۵ تاریخ: ۱۴۰۵/۰۸/۲۵", 90),
    Line("ردیف شرح کالا تعداد فی glo کل", 85),
    Line("۱ برنج سیلا ۵ کیلویی ۱۰ ٩,۵۰۰ ٩۵۰", 85),
    Line("۲ روغن آفتابگردان ۵ لیتری ۴ ۱,۲۵۰ ۵,۰۰۰", 90),
    Line("۳ چای سبز ۶ ۲۲۰ ۱۱۳۲۰", 89),
    Line("۴ شکر سفید ۲۰ ۸۵ ۱۷۰۰", 87),
    Line("جمع کل ۱۷,۵۲۰", 93),
  )

  /** فاکتورِ دریِ عمده‌فروش با رقمِ لاتین، جداکنندهٔ «,» و «|» ِ جدول؛ ستونِ «قیمت فی» پیش از «تعداد» */
  val DARI_PRICE_FIRST = listOf(
    Line("عمده فروشی نور - کابل، سرک میوند", 80),
    Line("فاکتور نمبر 2231   تاریخ 1405/08/20", 82),
    Line("شرح جنس | قیمت فی | تعداد | قیمت مجموعی", 78),
    Line("آرد سفید ۴۹ کیلویی | 2,450 | 5 | 12,250", 84),
    Line("روغن مایع 16 لیتری | 3,100 | 2 | 6,200", 86),
    Line("صابون لوکس | 35 | 24 | 840", 88),
    Line("نسکافه 3in1 | 12.5 | 10 | 125", 70),
    Line("تخفیف 5%   - 300", 75),
    Line("مجموعه: 19,115", 90),
  )

  /** عکسِ کج و کم‌نور: سرخط خوانده نشد، یک نام دو خط شد، یک خط کم‌رنگ، و یک خطِ نشانی با عدد */
  val NO_HEADER = listOf(
    Line("آدرس: کابل، کارته ۴، کوچه ۱۲", 70),
    Line("شیر پاستوریزه یک لیتری", 81),
    Line("12 45 540", 77),
    Line("ماست چکیده ۱ کیلویی ۱۴۰ ۳ ۴۲۰", 52),
    Line("پنیر ۸ ۱٫۲۵۰", 74),
    Line("تلفن 0799123456", 90),
  )

  /** بارکدِ چاپی کنارِ نام؛ «ي» و «ك» ِ عربی؛ نیم‌فاصله */
  val ARABIC_LETTERS = listOf(
    Line("بيسكويت مادر 6260100100018 ۲ ۱۵۰ ۳۰۰", 88),
    Line("چاي‌سبز گلستان ۳ ۹۰ ۲۷۰", 88),
    Line("كنسرو لوبيا ۶ ۵۵ ۳۳۰", 88),
  )

  /** کاتالوگِ دفترِ دکان برای تطبیق */
  val CATALOG = listOf(
    InvoiceOcr.CatalogItem("p-rice", "برنج سیلا ۵ کیلویی", purchasePrice = 950.0),
    InvoiceOcr.CatalogItem("p-oil", "روغن آفتابگردان ۵ لیتری لادن", purchasePrice = 1200.0),
    InvoiceOcr.CatalogItem("p-tea", "چای‌سبز گلستان", purchasePrice = 210.0),
    InvoiceOcr.CatalogItem("p-sugar", "شکر", purchasePrice = 80.0),
    InvoiceOcr.CatalogItem("p-salt", "نمک یددار", purchasePrice = 20.0),
    InvoiceOcr.CatalogItem("p-milk", "شیر پاستوریزه ۱ لیتری", purchasePrice = 45.0),
    InvoiceOcr.CatalogItem("p-yogurt", "ماست چکیده ۱ کیلویی", purchasePrice = 140.0),
    InvoiceOcr.CatalogItem("p-cheese", "پنیر سفید", purchasePrice = 1250.0),
    InvoiceOcr.CatalogItem("p-biscuit", "بیسکویت ساقه طلایی", barcodes = listOf("6260100100018"), purchasePrice = 150.0),
    InvoiceOcr.CatalogItem("p-beans", "کنسرو لوبیا چیتی", purchasePrice = 55.0),
  )
}
