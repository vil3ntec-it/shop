package ir.vil3ntec.tohid.ui.v3.online.delivery

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.delivery.AutoAssign
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.isoMillis
import ir.vil3ntec.tohid.ui.theme.V3Tone

/**
 *  حالتِ خالصِ میزِ تحویل (آزمون: `DeliveryModelTest`): برگه‌ها و شمارشان، چهار عددِ بالای
 *  میز، متن‌های هر کارت (چه کسی، چه روشی، یادداشت‌ها) و وضعیتِ هر پیک — همه از همان
 *  `DeskData`، `CourierCash`، `AutoAssign` و `DeskActions`؛ هیچ عددی این‌جا ساخته نمی‌شود.
 */
object DeliveryModel {
  const val ASSIGN = "assign"
  const val COURIERS = "couriers"
  const val PICKUP = "pickup"
  const val EXTERNAL = "external"

  /** «شرکت‌های بیرونی» فقط با `delivery.assign` (کرایه دارد) */
  fun tabs(cfg: DeskConfig): List<String> = listOf(ASSIGN, COURIERS, PICKUP) + if (cfg.perms.assign) listOf(EXTERNAL) else emptyList()

  fun initialTab(query: String?, cfg: DeskConfig?): String = query?.takeIf { cfg == null || it in tabs(cfg) } ?: ASSIGN

  fun label(tab: String): String = when (tab) {
    ASSIGN -> StrOnline.dlTabAssign
    COURIERS -> StrOnline.dlTabCouriers
    PICKUP -> StrOnline.dlTabPickup
    EXTERNAL -> StrOnline.dlTabExternal
    else -> tab
  }

  internal fun count(tab: String, d: DeskData): Int = when (tab) {
    ASSIGN -> d.toAssign.size
    COURIERS -> d.couriers.size
    PICKUP -> d.pickups.size
    EXTERNAL -> d.companies.size
    else -> 0
  }

  /** چهار عددِ بالای میز */
  data class Stats(val ready: Int, val live: Int, val heldMinor: Long, val atCap: Int)

  internal fun stats(d: DeskData): Stats = Stats(
    ready = d.toAssign.size,
    live = d.missions.size,
    heldMinor = d.couriers.sumOf { it.cashHeldMinor },
    atCap = d.couriers.count { it.active && CourierCash.atCap(it) },
  )

  /** نقدی که هنگامِ تحویل گرفته می‌شود (پرداخت‌شده ⇐ صفر) */
  fun expected(o: OrderDto): Long = AutoAssign.expectedOf(o.paymentMethod, o.paymentStatus, o.totalMinor)

  fun landmark(o: OrderDto): String = o.address.landmark.ifEmpty { o.address.text }

  /** چه کسی می‌برد: نامِ پیکِ دکان، نامِ شرکت، یا نامِ روش */
  internal fun who(m: Mission, d: DeskData): String = when (m.mode) {
    "shop_courier" -> d.couriers.firstOrNull { it.id == m.courierId }?.name ?: StrOnline.dlCourier
    "external" -> m.externalName.ifEmpty { StrOnline.deliveryMode("external") }
    else -> StrOnline.deliveryMode(m.mode)
  }

  internal fun how(m: Mission, d: DeskData): String =
    "${StrOnline.deliveryMode(m.mode)} · ${who(m, d)}" + if (m.externalRef.isNotEmpty()) " · ${m.externalRef}" else ""

  fun notes(m: Mission): List<String> = buildList {
    if (m.recallRequested && m.recallOutcome == "pending") add(StrOnline.dlRecallSent)
    if (m.cashReview) add(StrOnline.dlCashReview)
    if (m.attemptNo > 1) add(StrOnline.dlAttempt(m.attemptNo))
  }

  /** کارهای «فرعی» با دکمهٔ دوم (پس‌گرفتن، ناموفق، فراخوانی)؛ بقیه دکمهٔ اصلی */
  fun secondary(act: DeskActions.Act): Boolean =
    act == DeskActions.Act.FAIL || act == DeskActions.Act.WITHDRAW || act == DeskActions.Act.RECALL

  /** وضعیتِ پیک برای برچسبِ کارت (متن + گونه، رنگ تنها نیست) */
  fun courierState(cr: CourierRow): Pair<String, V3Tone?> = when {
    !cr.active -> StrOnline.dlInactive to null
    CourierCash.overCap(cr) -> StrOnline.dlOverCap to V3Tone.ERROR
    CourierCash.atCap(cr) -> StrOnline.dlAtCap to V3Tone.WARNING
    else -> StrOnline.dlReadyState to V3Tone.SUCCESS
  }

  /** حالتِ کدِ دریافتِ یک سفارشِ حضوری */
  fun pickupState(m: Mission?): String = when {
    m == null -> StrOnline.dlNoCodeYet
    m.codeLocked -> StrOnline.dlCodeLocked
    m.hasCode && !m.codeUsed -> StrOnline.dlCodeGiven
    else -> StrOnline.missionStatus(m.status)
  }

  /** جست‌وجوی برگهٔ حضوری: شمارهٔ سفارش (رقمِ فارسی هم) یا نامِ مشتری */
  fun matches(o: OrderDto, query: String): Boolean {
    val q = ir.vil3ntec.tohid.core.orders.OrderDraft.latin(query).removePrefix("#").trim()
    return q.isEmpty() || o.orderNo.toString().contains(q) || o.customer.name.contains(query.trim(), ignoreCase = true)
  }

  /** خالصِ حساب با شرکت در یک جهتِ روشن (مثبت ⇐ دکان می‌دهد، منفی ⇐ شرکت می‌دهد) */
  fun net(label: String, netToCompanyMinor: Long): Pair<String, String> = when {
    netToCompanyMinor > 0 -> StrOnline.dlNetShopPays(label) to StrOnline.afn(netToCompanyMinor)
    netToCompanyMinor < 0 -> StrOnline.dlNetCompanyPays(label) to StrOnline.afn(-netToCompanyMinor)
    else -> label to StrOnline.dlSquare
  }
}

/** آغاز و پایانِ یک ماهِ خورشیدی به ساعتِ محلی؛ `offset` = ۰ این ماه، −۱ ماهِ پیش (جابه‌جاشده از `DeliveryCash.kt`) */
internal fun jalaliMonth(offset: Int): Pair<Long, Long> {
  val cal = java.util.Calendar.getInstance()
  val j = Jalali.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
  var y = j.year
  var m = j.month + offset
  while (m < 1) { m += 12; y -= 1 }
  while (m > 12) { m -= 12; y += 1 }
  val ny = if (m == 12) y + 1 else y
  val nm = if (m == 12) 1 else m + 1
  //  isoMillis ظهرِ همان روز است؛ نیم‌روز عقب ⇒ نیمه‌شب
  val half = 12 * 3_600_000L
  val from = isoMillis(Jalali.toIso(y, m, 1).orEmpty()) - half
  val to = isoMillis(Jalali.toIso(ny, nm, 1).orEmpty()) - half
  return from to to
}
