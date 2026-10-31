package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.Qty
import ir.vil3ntec.tohid.core.stock.CountMerge
import ir.vil3ntec.tohid.core.stock.CountScan

/**
 *  حالتِ پنجرهٔ «انبارگردانیِ تازه» (INV-06/07/08) — جدا از صفحه تا آزمون‌پذیر باشد.
 *
 *  متنِ هر خانه همان چیزی است که کاربر نوشته (رقمِ فارسی، «٫» و «,» هم)؛ اسکن یک واحد به همان می‌افزاید.
 *  شمرده‌شدهٔ نهایی = برگهٔ همین دستگاه + برگه‌های دستگاه‌های دیگر (`CountMerge`)، و همان به
 *  `WarehouseEngine.stockCount` ِ امروز می‌رود؛ پس ثبت و سابقه همان است که بود.
 */
object CountSheetModel {

  sealed interface Cell {
    data object Blank : Cell
    data class Ok(val milli: Long) : Cell
    data object Bad : Cell
  }

  fun parse(text: String): Cell {
    val t = ir.vil3ntec.tohid.latinDigits(text.trim()).replace('٫', '.').replace(',', '.')
    if (t.isEmpty()) return Cell.Blank
    val v = t.toDoubleOrNull() ?: return Cell.Bad
    if (v.isNaN() || v.isInfinite() || v < 0) return Cell.Bad
    return runCatching { Cell.Ok(Qty.fromLegacy(v).milli) }.getOrDefault(Cell.Bad)
  }

  /** خانه‌های درستِ این دستگاه، و نخستین کالایی که عددش نادرست است */
  fun own(texts: Map<String, String>): Pair<Map<String, Long>, String?> {
    val out = LinkedHashMap<String, Long>()
    var bad: String? = null
    for ((id, text) in texts) when (val c = parse(text)) {
      is Cell.Ok -> out[id] = c.milli
      Cell.Bad -> if (bad == null) bad = id
      Cell.Blank -> {}
    }
    return out to bad
  }

  /** همهٔ برگه‌ها با هم؛ برگهٔ این دستگاه با `ownSheetId` (برگهٔ هم‌شناسه از بیرون جای آن را نمی‌گیرد) */
  fun total(own: Map<String, Long>, ownSheetId: String, imported: List<CountMerge.Sheet>): CountMerge.Result =
    CountMerge.merge(imported.filter { it.sheetId != ownSheetId } + CountMerge.Sheet(ownSheetId, "", Long.MAX_VALUE, own))

  /** برای `WarehouseEngine.stockCount` (همان Double ِ امروز، از هزارم) */
  fun asDouble(counts: Map<String, Long>): Map<String, Double> = counts.mapValues { (_, m) -> m / 1000.0 }

  /** متنِ خانه پس از اسکن: همان عدد به لاتین و بی صفرِ اضافه */
  fun text(milli: Long): String {
    val frac = (milli % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (frac.isEmpty()) (milli / 1000).toString() else "${milli / 1000}.$frac"
  }

  /** اسکن روی خانه‌های این دستگاه: فقط برگهٔ همین دستگاه عوض می‌شود */
  fun scan(texts: Map<String, String>, code: String, d: ShopData, index: Map<String, String>): Pair<Map<String, String>, CountScan.Result> {
    val byId = d.products.associateBy { it.id }
    val r = CountScan.scan(
      code,
      ownerOf = { index[it] },
      counted = { id -> (parse(texts[id].orEmpty()) as? Cell.Ok)?.milli },
      weighed = { id -> SalesEngine.isFractionalUnit(byId[id]?.unit.orEmpty()) },
      active = { id -> byId[id]?.active == true },
    )
    return if (r is CountScan.Result.Counted) (texts + (r.productId to text(r.countedMilli))) to r else texts to r
  }
}
