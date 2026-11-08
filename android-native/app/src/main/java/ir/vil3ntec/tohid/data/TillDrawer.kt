package ir.vil3ntec.tohid.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.pay.Drawers

/**
 *  PAY-17 — «چند کشو یا چند صندوقدار، و شیفتِ شناور»: کدام شیفتِ صندوق روی **همین دستگاه** است. قاعده‌ها در
 *  `core/pay/Drawers`؛ تنظیم و نگه‌داری در `TillDrawerAndroid.kt` (`MultiDrawerPref`، پیش‌فرض خاموش).
 *
 *  ⛔ خاموش (پیش‌فرض) ⇒ [picked] همیشه `null` ⇒ هیچ ردیفی مهر نمی‌گیرد و `CashShift.current` همان کشوی اصلیِ امروز است.
 *  روشن و کشوی نام‌دارِ باز برگزیده ⇒ هر فروش، برگشت و رسیدی که **همین دستگاه** ثبت می‌کند مهرِ همان شیفت را می‌گیرد
 *  ([stampNew]؛ در `SalesEngine.record`/`recordReturn`، `ReturnFlow.post` و `DebtEntry.change`) — جای دیگری مهر نمی‌زند.
 *  مثلِ `ShopDayChoice.hours` حالِ همین دستگاه است و همگام نمی‌شود؛ خودِ مهر روی ردیف همگام می‌شود.
 */
object TillDrawer {

  /** `shift_resume`: «ادامهٔ همین شیفت روی دستگاهِ دیگر» (`refId` = شیفت، `by`، یادداشت) — فقط سابقه، بی اثرِ پولی */
  const val RESUME = "shift_resume"

  /** شیفتِ نام‌دارِ برگزیدهٔ همین دستگاه؛ `null` ⇒ کشوی اصلی */
  var picked by mutableStateOf<String?>(null)

  /** شیفت‌های نام‌دارِ دفتر: شناسهٔ `shift_open` ِ با `drawer` ⇐ لحظهٔ بستن */
  fun named(d: ShopData): Drawers.Named {
    val opens = d.auditLog.filter { it.type == CashShift.OPEN && it.drawer != null }
    if (opens.isEmpty()) return Drawers.Named(emptyMap())
    val closes = d.auditLog.filter { it.type == CashShift.CLOSE && it.refId != null }.associate { it.refId to it.createdAt }
    return Drawers.Named(opens.associate { it.id to closes[it.id] })
  }

  /** مهرِ ردیفی که همین حالا روی این دستگاه ثبت می‌شود (فقط شیفتِ نام‌دارِ باز) */
  fun stamp(d: ShopData): String? = picked?.let { Drawers.stamp(it, named(d)) }

  /**
   *  ردیف‌های **تازهٔ** [after] (شناسه‌ای که در [before] نبود) در فروش، برگشت و قرض با مهرِ [stamp] ِ [before]؛
   *  بی شیفتِ برگزیده ⇒ همان شیء (بی هزینه). ردیفِ مهردار دست نمی‌خورد.
   */
  fun stampNew(before: ShopData, after: ShopData): ShopData {
    val id = stamp(before) ?: return after
    val sales = before.sales.mapTo(HashSet()) { it.id }
    val returns = before.saleReturns.mapTo(HashSet()) { it.id }
    val txns = before.transactions.mapTo(HashSet()) { it.id }
    return after.copy(
      sales = after.sales.map { if (it.id !in sales && it.shiftId == null) it.copy(shiftId = id) else it },
      saleReturns = after.saleReturns.map { if (it.id !in returns && it.shiftId == null) it.copy(shiftId = id) else it },
      transactions = after.transactions.map { if (it.id !in txns && it.shiftId == null) it.copy(shiftId = id) else it },
    )
  }

  fun stamped(before: ShopData, r: SalesEngine.Result): SalesEngine.Result =
    if (r is SalesEngine.Result.Ok && picked != null) r.copy(data = stampNew(before, r.data)) else r

  /** کشوهای بازِ دفتر برای فهرستِ «کشوها»: اصلی (اگر باز است) و نام‌دارها، تازه‌ترین اول */
  fun open(d: ShopData): List<CashShift.Shift> = CashShift.list(d).filter { it.isOpen }

  /** نامِ نمایشیِ یک صندوق: نامِ کشوی نام‌دار، یا `null` برای کشوی اصلی */
  fun label(s: CashShift.Shift): String? = s.open.drawer

  /** ردیفِ سابقهٔ «ادامه روی این دستگاه» */
  fun resumeRow(shift: CashShift.Shift, by: String, today: String, now: Long, id: String): AuditEntry = AuditEntry(
    id = id, type = RESUME, date = today, refId = shift.open.id, createdAt = now, by = by.trim().ifEmpty { null },
    notes = "ادامهٔ صندوقِ «" + (shift.open.drawer ?: "اصلی") + "» روی این دستگاه" + if (by.isNotBlank()) " — ${by.trim()}" else "",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
  )
}
