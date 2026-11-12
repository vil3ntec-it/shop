package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.nav.RoutePaths

/**
 *  کارهای افزودهٔ «انبار» که در رابطِ قدیم دکمهٔ صفحهٔ انبار بودند و پس از پاک شدنش بی‌راه ماندند
 *  (`V3ReachabilityTest`): در «…» ِ گوشی و تراشه‌های کارِ کامپیوتر. خالص — دروازه‌ها همان تنظیم‌ها و نقشِ امروز
 *  (`StockExtrasAndroid.kt` می‌خواندشان)؛ آزمون: `StockExtrasTest`.
 */
enum class StockExtra(val route: String) {
  /** INV-20/21 — `StockPlacesPref` (محل‌ها با موتورِ تازه همان «دفترِ موجودی» است) */
  PLACES(StockPaths.PLACES),
  /** PRD-23/HW-04 — `LabelToolsPref` */
  LABELS(StockPaths.LABELS),
  /** دفترِ موجودی — فقط با موتورِ تازهٔ موجودی و بها (`StockLedger.isOn`) */
  BOOK(StockPaths.BOOK),
  BULK_PRODUCTS(StockPaths.BULK_PRODUCTS),
  /** کارِ صاحب/مدیر */
  IMAGES(StockPaths.IMAGES),
  /** IMG-PRO-17 — فقط با بانکِ تصاویرِ روشن (`shop.image_bank`) و نقشِ غیرِ شاگرد (`ImageChangesGate`) */
  IMAGE_CHANGES(StockPaths.IMAGE_CHANGES),
  /** ۴-۲۳ (۱) — استودیوی عکس: همان دروازهٔ بانکِ روشن و غیرِ شاگرد؛ پرچمِ `shop.image_studio` را صفحه از سرور می‌پرسد */
  IMAGE_STUDIO(StockPaths.IMAGE_STUDIO),
  /** INV-11 — فقط با پرچمِ `shop.catalog` و `stock.move` ِ سرور (`StockTransferFlag.on`) */
  TRANSFERS(StockPaths.TRANSFERS),
}

object StockExtras {
  /** آنچه از تنظیم‌ها و نقشِ همین دستگاه لازم است */
  data class Flags(
    val places: Boolean,
    val repack: Boolean,
    val staff: Boolean,
    val stockOn: Boolean,
    val labelTools: Boolean,
    /** IMG-PRO-17: بانکِ تصاویر برای این دکان روشن است و کاربر شاگرد نیست */
    val imageChanges: Boolean = false,
    /** INV-11: انتقالِ موجودیِ سرور برای این دکان و این کاربر روشن است */
    val transfers: Boolean = false,
  )

  /** کارهای دیدنی، به ترتیبِ صفحهٔ انبارِ پیشین */
  fun of(f: Flags): List<StockExtra> = buildList {
    if (!f.staff && ((f.places && !f.stockOn) || f.repack)) add(StockExtra.PLACES)
    if (f.labelTools) add(StockExtra.LABELS)
    if (f.stockOn) add(StockExtra.BOOK)
    add(StockExtra.BULK_PRODUCTS)
    if (!f.staff) add(StockExtra.IMAGES)
    if (f.imageChanges && !f.staff) add(StockExtra.IMAGE_CHANGES)
    if (f.imageChanges && !f.staff) add(StockExtra.IMAGE_STUDIO)
    if (f.transfers) add(StockExtra.TRANSFERS)
  }

  /**
   *  «انبارگردانی»: موتورِ تازه روشن ⇒ شمارش با بازبینی در زبانهٔ «شمارش» ِ دفترِ موجودی (INV-09/10)؛ وگرنه
   *  «انبارگردانیِ تازه» (اسکن، چند دستگاه، شمارشِ کور — `CountToolsPref`، کلاسِ A و پیش‌فرض روشن) یا برگهٔ ساده.
   */
  fun countRoute(stockOn: Boolean, countTools: Boolean): String = when {
    stockOn -> StockPaths.book("count")
    countTools -> StockPaths.COUNT_TOOLS
    else -> StockPaths.COUNT
  }

  /** زبانه‌های «دفترِ موجودی» (`StockBookNav.tab`) */
  val BOOK_TABS = setOf("pos", "ledger", "review", "count")

  fun bookTab(arg: String?): String? = arg?.takeIf { it in BOOK_TABS }

  internal fun bookPath(tab: String?): String = RoutePaths.withQuery(StockPaths.BOOK, "tab" to bookTab(tab))
}
