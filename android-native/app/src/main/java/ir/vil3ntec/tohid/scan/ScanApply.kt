package ir.vil3ntec.tohid.scan

import ir.vil3ntec.tohid.core.hw.CodeCount
import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.PendingScans
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore

/**
 *  اسکن‌های در انتظار ⇒ برگهٔ مقصد (خالص؛ آزمون: `ScanInboxTest`). انبارگردانی: شمارِ هر کالا به خانه‌اش افزوده
 *  می‌شود (عددِ نوشته‌شده می‌ماند و افزوده می‌شود). ورودِ کالا: یک ردیف برای هر کالا با همان شمار؛ بارکدِ ناشناس
 *  ردیفِ «محصولِ تازه» با همان بارکد (همان راهِ «ثبتِ کالا» ِ برگهٔ ورود). بارکدِ فرمان هرگز کالا نیست.
 */
object ScanApply {
  data class Counted(val counts: Map<String, String>, val ids: Set<String>, val unknown: List<CodeCount>)

  /** یک ردیفِ ورود: کالای شناخته ([productId]) یا بارکدِ تازه */
  data class Received(val productId: String?, val barcode: String, val qty: Int)

  private fun groups(scans: List<QueuedScan>) = PendingScans.group(scans.filterNot { CommandCard.isCommandLike(it.code) })

  private fun number(v: Double): String = if (v == Math.floor(v) && v < 1e15) v.toLong().toString() else v.toString()

  private fun read(text: String?): Double {
    val t = ir.vil3ntec.tohid.latinDigits(text.orEmpty().trim()).replace('٫', '.').replace(',', '.')
    return t.toDoubleOrNull()?.takeIf { !it.isNaN() && it >= 0 } ?: 0.0
  }

  /** خانه‌های انبارگردانی (شناسهٔ کالا ⇐ متن) + اسکن‌ها */
  fun counts(current: Map<String, String>, d: ShopData, scans: List<QueuedScan>): Counted {
    val index = ShopStore.barcodeIndex(d)
    val ids = d.products.map { it.id }.toSet()
    val out = LinkedHashMap(current)
    val unknown = ArrayList<CodeCount>()
    val touched = HashSet<String>()
    groups(scans).forEach { g ->
      val id = index[g.code]?.takeIf { it in ids }
      if (id == null) unknown += g
      else { out[id] = number(read(out[id]) + g.count); touched += id }
    }
    return Counted(out, touched, unknown)
  }

  /** ردیفِ برگهٔ ورودِ رابطِ امروز: واحد و قیمتِ خریدِ کالای شناخته؛ بارکدِ ناشناس ⇒ «محصولِ تازه» */
  data class LegacyRow(val productId: String?, val barcode: String, val qty: String, val unit: String, val price: String)

  fun legacyRows(d: ShopData, scans: List<QueuedScan>): List<LegacyRow> {
    val byId = d.products.associateBy { it.id }
    val fallback = d.products.firstOrNull()?.unit.orEmpty()
    return receive(d, scans).map { r ->
      val p = r.productId?.let(byId::get)
      LegacyRow(
        p?.id, if (p == null) r.barcode else "", r.qty.toString(), p?.unit?.ifBlank { fallback } ?: fallback,
        if (p != null && p.purchasePrice > 0) p.purchasePrice.toLong().toString() else "",
      )
    }
  }

  /** ردیف‌های ورودِ کالا: هر کالا یک بار (دو بارکدِ یک کالا یکی)، ناشناس‌ها جدا */
  fun receive(d: ShopData, scans: List<QueuedScan>): List<Received> {
    val index = ShopStore.barcodeIndex(d)
    val ids = d.products.map { it.id }.toSet()
    val out = LinkedHashMap<String, Received>()
    groups(scans).forEach { g ->
      val id = index[g.code]?.takeIf { it in ids }
      val k = id ?: ("?" + g.code)
      out[k] = out[k]?.let { it.copy(qty = it.qty + g.count) } ?: Received(id, g.code, g.count)
    }
    return out.values.toList()
  }
}
