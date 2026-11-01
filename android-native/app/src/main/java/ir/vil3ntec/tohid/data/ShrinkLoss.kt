package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Qty
import ir.vil3ntec.tohid.core.stock.ShrinkReport

/**
 *  گزارشِ ضایعات و کسری (INV-17) — سمتِ دفترِ دکان برای هستهٔ مشترکِ `core/stock/ShrinkReport`.
 *
 *  فقط از `stockMovements` می‌خواند (هر اصلاح، انبارگردانی و ضایعاتِ برگشتی یک حرکت دارد)، تا ردیفِ انبار و
 *  حرکتِ همان کار دو بار شمرده نشوند؛ نوعِ اصلاح (INV-04: `damage`، `expired`، `lost`، `count`) از ردیفِ انبارِ
 *  همان حرکت (`refId`) می‌آید. بها همان `purchasePrice` ِ امروزِ کالاست — همان مبنای «ارزشِ موجودی».
 *  فقط خواندنی؛ صفحه پشتِ `ShrinkReportPref` (پیش‌فرض خاموش).
 */
object ShrinkLoss {

  /** حرکت‌های دفتر ⇒ ردیف‌های گزارش (فروش، خرید و برگشت‌ها بیرون) */
  fun moves(d: ShopData): List<ShrinkReport.Move> {
    val adjKind = HashMap<String, String>()
    for (w in d.warehouseEntries) {
      val k = w.adjKind
      if (w.isAdjustment && k != null) adjKind[w.id] = k
    }
    return d.stockMovements.mapNotNull { m ->
      val q = runCatching { Qty.fromLegacy(m.qty).milli }.getOrNull() ?: return@mapNotNull null
      val kind = ShrinkReport.classify(m.type, m.notes, m.refId?.let { adjKind[it] }, q) ?: return@mapNotNull null
      ShrinkReport.Move(m.productId, m.date, q, kind, m.notes)
    }
  }

  private fun minor(afghani: Double): Long? = runCatching { Money.fromLegacy(afghani, Currency.AFN).minor }.getOrNull()

  fun report(d: ShopData, from: String, to: String): ShrinkReport.Report {
    val cost = d.products.associate { it.id to minor(it.purchasePrice) }
    val sales = minor(ReportEngine.sales(d, from, to).net)
    return ShrinkReport.build(moves(d), from, to, { cost[it] }, salesMinor = sales)
  }

  /** نامِ هر نوع در برگه — داده است و با زبانِ صفحه عوض نمی‌شود */
  fun kindName(k: ShrinkReport.Kind): String = when (k) {
    ShrinkReport.Kind.WASTE -> "ضایعات"
    ShrinkReport.Kind.EXPIRED -> "منقضی"
    ShrinkReport.Kind.LOST -> "گم‌شده"
    ShrinkReport.Kind.SHORTAGE -> "کسریِ شمارش"
    ShrinkReport.Kind.SURPLUS -> "اضافهٔ شمارش"
    ShrinkReport.Kind.OTHER -> "اصلاحِ دستی"
  }

  /** مبلغ به افغانی برای برگه: رقمِ لاتین، بی جداکننده، اعشار فقط اگر هست (۳۳٫۳ ⇒ 33.30) */
  fun afn(minor: Long): String {
    val a = if (minor < 0) -minor else minor
    val body = if (a % 100 == 0L) (a / 100).toString() else "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    return if (minor < 0) "-$body" else body
  }

  /** مقدار از هزارم: بی صفرِ اضافه (۲٫۵۰۰ ⇒ 2.5) */
  fun qtyText(milli: Long): String {
    val a = if (milli < 0) -milli else milli
    val frac = (a % 1000).toString().padStart(3, '0').trimEnd('0')
    val body = if (frac.isEmpty()) (a / 1000).toString() else "${a / 1000}.$frac"
    return if (milli < 0) "-$body" else body
  }

  /**
   *  برگهٔ اکسل/چاپ: یک ردیف برای هر حرکت به ترتیبِ تاریخ؛ «زیان» مثبت = زیان، منفی = اضافه، خالی = بی‌بها.
   *  جمعِ ستونِ «زیان» همان «زیانِ کل» ِ صفحه است.
   */
  fun sheet(d: ShopData, from: String, to: String): ReportCsv.Sheet {
    val r = report(d, from, to)
    val products = d.products.associateBy { it.id }
    val rows = mutableListOf(listOf("تاریخ", "نام کالا", "نوع", "مقدار", "واحد", "زیان", "دلیل"))
    for (l in r.lines) {
      val p = products[l.move.productId]
      rows += listOf(
        l.move.date,
        p?.name ?: "کالای حذف‌شده",
        kindName(l.move.kind),
        qtyText(l.move.qtyMilli),
        p?.unit.orEmpty(),
        if (l.priced) afn(l.lossMinor) else "",
        l.move.reason,
      )
    }
    return ReportCsv.Sheet("ضایعات-و-کسری-$from-تا-$to", rows)
  }
}
