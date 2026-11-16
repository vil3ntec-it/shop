package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransferLedger

/**
 *  INV-11/INV-01 — آینهٔ انتقالِ میانِ شعبه در **دفترِ همین دستگاه** (فازِ ۴ ِ شعبه‌ها، `docs/BRANCHES-fa.md` §۱۰؛ قاعده‌ها در
 *  `core/stock/BranchTransferLedger`). پس از هر پاسخِ سرور (گامِ همین صفحه، یا خواندنِ فهرست) دفترِ شعبهٔ روی میز برای هر
 *  (انتقال، کالا، سو) یک ردیفِ «اصلاحِ موجودی» دارد که مقدارش اثرِ کنونیِ همان انتقال است — فرستنده −(فرستاده − برگشته)،
 *  گیرنده +رسیده — تا موجودیِ برنامه همان موجودیِ سرورِ آن شعبه بماند (الف کالای فرستاده را دیگر نمی‌فروشد، ب کالای رسیده را
 *  می‌فروشد). ردیف از همان صفِ همگام‌سازیِ امروز می‌رود (جدولِ `warehouseEntries`؛ هیچ جدولِ همگامِ تازه).
 *
 *  ⛔ سرور همین ردیف را دو بار نمی‌شمرد: نشانِ `adjKind = branch_transfer` و `refId` = شناسهٔ انتقالِ واقعیِ همین شعبه
 *     (`catalog.ingestTx` ⇐ `lib/branch-transfer-mirror.js`).
 *  ⛔ فقط وقتی میز هنوز روی همان شعبه است (زیرِ قفلِ نوشتنِ `ShopStore.update`)، نه در حالتِ آموزش، و فقط برای نقشی که ردیفِ
 *     انبار را بی «قاعدهٔ ردیف» می‌نویسد (صندوق‌دار فقط اصلاحِ برگشت را دارد ⇒ ردیفش در سرور رد می‌شد) — نقشِ دیگر در دستگاهِ همان
 *     شعبه آن را می‌نویسد.
 *  ⛔ کالایی که هنوز در دفترِ این شعبه نیست (آینهٔ کاتالوگ نرسیده) این بار نوشته نمی‌شود؛ بارِ بعد.
 */
object BranchTransferBook {

  /** مقدارِ ردیف‌های آینهٔ موجود (شناسهٔ ردیف ⇒ هزارم) */
  fun have(d: ShopData): Map<String, Long> =
    d.warehouseEntries.filter { it.adjKind == BranchTransferLedger.ADJ_KIND }.associate { it.id to Math.round(it.units * 1000.0) }

  /** یادداشتِ ردیفِ دفتر (داده، همگام می‌شود) */
  private fun noteOf(t: BranchTransfer.Transfer?, w: BranchTransferLedger.Write): String {
    val other = when (w.side) { BranchTransferLedger.OUT -> t?.toBranch; else -> t?.fromBranch }
    val name = other?.name?.ifBlank { null } ?: if (other?.main == true) "اصلی" else "—" // داده: نامِ شعبهٔ آن سو در یادداشتِ دفتر
    return if (w.side == BranchTransferLedger.OUT) "انتقال به شعبهٔ «$name»" else "انتقال از شعبهٔ «$name»" // داده: یادداشتِ ردیفِ دفتر
  }

  /** نوشتن‌ها روی دفتر؛ هیچ تغییری ⇒ null */
  fun apply(d: ShopData, writes: List<BranchTransferLedger.Write>, transfers: List<BranchTransfer.Transfer>, today: String, now: Long): ShopData? {
    if (writes.isEmpty()) return null
    val products = d.products.associateBy { it.id }
    val byT = transfers.associateBy { it.id }
    val list = d.warehouseEntries.toMutableList()
    val at = HashMap<String, Int>().apply { list.forEachIndexed { i, e -> put(e.id, i) } }
    var changed = false
    for (w in writes) {
      val p = products[w.productId] ?: continue
      val units = w.milli / 1000.0
      val i = at[w.rowId]
      if (i != null) {
        val e = list[i]
        if (Math.round(e.units * 1000.0) != w.milli) { list[i] = e.copy(units = units); changed = true }
      } else {
        list += WarehouseEntry(
          id = w.rowId, productId = p.id, units = units, unit = p.unit, date = today, notes = noteOf(byT[w.transferId], w),
          isAdjustment = true, adjKind = BranchTransferLedger.ADJ_KIND, refId = w.transferId, createdAt = now,
        )
        at[w.rowId] = list.size - 1
        changed = true
      }
    }
    return if (changed) d.copy(warehouseEntries = list) else null
  }

  /** این نقش ردیفِ انبار را بی «قاعدهٔ ردیف» می‌نویسد؟ (همان `PermCore` ِ سرور) */
  fun canMirror(role: String): Boolean =
    role !in PermCore.ROLES || (PermCore.canWrite(role, "warehouseEntries") && PermCore.rowRule(role, "warehouseEntries") == null)

  /**
   *  دفترِ شعبهٔ [here] را با [transfers] هم‌تراز می‌کند ⇒ آیا چیزی نوشته شد. میزِ عوض‌شده، آموزش، نقشِ ناتوان یا دیسکِ بسته ⇒ false.
   */
  suspend fun mirror(context: Context, store: ShopStore, here: String, transfers: List<BranchTransfer.Transfer>): Boolean {
    if (transfers.isEmpty() || TrainingMode.on || store.training) return false
    if (!canMirror(ShopRole.current(context))) return false
    val today = ir.vil3ntec.tohid.todayIso()
    val now = System.currentTimeMillis()
    val app = context.applicationContext
    return store.update { cur ->
      if (BranchDesk.current(app) != here) null
      else apply(cur, BranchTransferLedger.planAll(transfers, here, have(cur)), transfers, today, now)
    } == true
  }
}
