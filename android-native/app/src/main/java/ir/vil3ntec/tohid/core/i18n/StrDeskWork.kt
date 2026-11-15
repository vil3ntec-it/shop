package ir.vil3ntec.tohid.core.i18n

/**
 *  صفحه‌های کاریِ **کامپیوتر** روی سیستمِ طراحیِ `ui/desk` (`claude/e-desk-d3a`): صندوق، کالاها و انبار. هر نامِ دکمه و
 *  ستونی که پیش‌تر بود همان نوشتهٔ `StrSale`/`StrPos`/`StrStock`/`StrProductPages` می‌ماند (راهنمای F1 و `ScreenGuides`
 *  همان را می‌گوید)؛ این‌جا فقط نوشته‌های تازه. دری از لایهٔ `DariText`.
 */
@Suppress("unused")
object StrDeskWork {
  //  ── صندوق ──
  val colQty: String get() = t("تعداد", "Qty", "شمېر")
  val colLineTotal: String get() = t("جمع", "Total", "ټول")
  val exactCash: String get() = t("نقدِ دقیق", "Exact cash", "دقیقې نغدې")
  val keyAdd: String get() = t("افزودن", "Add", "اضافه")
  val keyRows: String get() = t("ردیف", "Row", "کرښه")
  val keyQty: String get() = t("تعداد", "Qty", "شمېر")
  val keyCustomer: String get() = t("مشتری", "Customer", "پیرودونکی")
  val keyEditRow: String get() = t("قیمت/تعداد", "Price/qty", "بیه/شمېر")
  val keyFreeItem: String get() = t("قلمِ آزاد", "Custom item", "آزاد توکی")
  val keyPay: String get() = t("ثبت", "Record", "ثبت")
  val keyPayPanel: String get() = t("پرداخت", "Pay", "تادیه")
  val keyReprint: String get() = t("چاپِ آخر", "Print last", "وروستی چاپ")
  val keyClear: String get() = t("پاک", "Clear", "پاکول")
  val keyAll: String get() = t("همهٔ کلیدها", "All keys", "ټولې کیلي")
  val keyReceive: String get() = t("دریافت", "Receive", "ترلاسه")
  val keyPriceCheck: String get() = t("قیمت‌پرسی", "Price check", "بیه پوښتنه")
  val catalogTitle: String get() = t("کالاها", "Items", "توکي")
  fun listPrice(p: Any): String = t("قیمتِ فهرست ${asIs(p)} ؋", "List price $p ؋", "د لړلیک بیه $p ؋")

  //  ── کالاها ──
  val colCode: String get() = t("بارکد / کد", "Barcode / code", "بارکوډ / کوډ")
  val colCategory: String get() = t("دسته", "Category", "ډله")
  val colUnit: String get() = t("واحد", "Unit", "واحد")
  val colCost: String get() = t("خرید ؋", "Cost ؋", "پېر ؋")
  val colPrice: String get() = t("فروش ؋", "Price ؋", "پلور ؋")
  val colStock: String get() = t("موجودی", "Stock", "موجودي")
  val colExpiry: String get() = t("انقضا", "Expiry", "د پای نېټه")
  val colStatus: String get() = t("وضعیت", "Status", "حالت")
  val expired: String get() = t("منقضی", "Expired", "تېر شوی")
  fun daysLeft(n: Any): String = t("${asIs(n)} روز", "$n ${pl(n, "day", "days")}", "$n ورځې")
  fun sortBy(col: Any): String = t("مرتب‌سازی با ${asIs(col)}", "Sort by $col", "د $col له مخې ترتیب")
  val closePanel: String get() = t("بستن", "Close", "بندول")
  val newItemsNone: String get() = t("کالای تازه‌ای نیامد", "No new items", "نوی توکی نشته")

  //  ── انبار ──
  val colCartons: String get() = t("کارتن", "Cartons", "کارتنونه")
  val colCover: String get() = t("کافی برای", "Lasts", "بسنه ده")
  val colSalePrice: String get() = t("قیمتِ فروش", "Sale price", "د پلور بیه")
  val stockValue: String get() = t("ارزشِ موجودی", "Stock value", "د موجودۍ ارزښت")
  fun stockValueNote(items: Any, units: Any): String = t("${asIs(items)} کالا • ${asIs(units)} دانه", "$items ${pl(items, "item", "items")} • $units units", "$items توکي • $units دانې")
  val lowStock: String get() = t("کم‌موجود", "Low stock", "لږ موجودي")
  val outStock: String get() = t("تمام‌شده", "Out of stock", "پای ته رسېدلي")
  val expiring: String get() = t("نزدیک به انقضا", "Expiring soon", "نږدې پای ته")
  val expiredLots: String get() = t("منقضی‌شده", "Expired", "تېر شوي")
  val needsAction: String get() = t("نیازِ رسیدگی", "Needs attention", "پاملرنې ته اړتیا")
  val allClear: String get() = t("همه رو به راه است", "All good", "ټول سم دي")
  val moreActions: String get() = t("کارهای دیگر", "More actions", "نور کارونه")
  val pickToSee: String get() = t("کالایی را از جدول بزنید تا جزئیات و کارهایش این‌جا باز شود.", "Click an item in the table to see its details and actions here.", "په جدول کې یو توکی کېکاږئ چې جزئیات یې دلته پرانیستل شي.")
  val colDate: String get() = t("تاریخ و ساعت", "Date & time", "نېټه او وخت")
  val colKind: String get() = t("نوع", "Type", "ډول")
  val colAmount: String get() = t("مقدار", "Quantity", "اندازه")
  val colNote: String get() = t("توضیح", "Note", "یادونه")
  val openLink: String get() = t("باز کردن", "Open", "پرانیستل")
  val place: String get() = t("محل", "Location", "ځای")
  val today: String get() = t("امروز", "Today", "نن")
  fun overDays(n: Any): String = t("بیش از ${asIs(n)} روز", "Over $n days", "له $n ورځو زیات")
}
