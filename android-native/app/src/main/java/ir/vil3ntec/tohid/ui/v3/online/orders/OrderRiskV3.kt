package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrRisk
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderRisk
import ir.vil3ntec.tohid.core.shopper.GeoPin
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section

/**
 *  RSK-03 (§۶.۲۱) — «سابقهٔ رد و برگشتِ مشتری پیش از ارسال» زیرِ سرِ جزئیاتِ سفارشِ اینترنتی: شمارِ سفارش‌های پیشینِ همین
 *  شماره در همین دکان و نشانه‌های توضیح‌دارِ سرور (تحویلِ ناموفق، نبودن سرِ تحویل، برگشت به دکان، لغوِ پیاپی، فهرستِ
 *  بسته/مطمئن، شاید تکراری). منطق `core/orders/OrderRisk` (commonMain، آیفون هم)؛ این‌جا فقط کشیدن.
 *
 *  RSK-04 — صاحب و مدیر (`orders.contact`، همان‌ها که شمارهٔ کامل را می‌بینند) از همین کارت شمارهٔ همین سفارش را با دلیل
 *  و انقضا به فهرستِ بسته یا مطمئن می‌برند، **جای** همین سفارش (پینِ ارسال) را با شعاع می‌بندند و ردیف‌های فعالِ فهرست را
 *  می‌بینند و برمی‌دارند (برگه‌ها و صفحهٔ جدای فهرست: `OrderRiskListsV3.kt`). «دستگاه» ِ همین سفارش (شناسهٔ تصادفیِ مرورگر یا
 *  برنامهٔ خرید؛ خودش هرگز به برنامه نمی‌رسد) فقط وقتی سرور می‌گوید دارد (`OrderRisk.View.device`).
 *
 *  RSK-03 «کارت در صف» — [OrderRiskQueueCardV3] بالای صف: سفارش‌های پیش از ارسال با هشدار (`GET …/risk/queue`، یک پرسش
 *  برای همهٔ صف) و نشانِ همان هشدار روی کارتِ هر سفارش (`OrderRisk.cardFlags` ⇐ `OrderCard.risk`).
 *
 *  ⛔ پرچمِ سرورِ `shop.order_risk` خاموش (پیش‌فرض) ⇒ `enabled = false` ⇒ هیچ چیز کشیده نمی‌شود؛ صفحه همان امروز.
 *  ⛔ هیچ تصمیمی خودکار گرفته نمی‌شود (رد و قبول همان دکمه‌های امروز). شماره در کارت نشان داده نمی‌شود.
 *  خطای شبکه ⇒ «خوانده نشد» با «دوباره» (نه «بی سابقه»).
 */
@Composable
internal fun OrderRiskCardV3(
  orderId: String, repo: OrdersRepository, rev: Long = 0, canList: Boolean = false, phone: String? = null, pin: GeoPin? = null,
) {
  var view by remember(orderId) { mutableStateOf<OrderRisk.View?>(null) }
  var failed by remember(orderId) { mutableStateOf(false) }
  var retry by remember(orderId) { mutableStateOf(0) }
  var adding by remember(orderId) { mutableStateOf<String?>(null) }
  var addingPin by remember(orderId) { mutableStateOf(false) }
  var addingDevice by remember(orderId) { mutableStateOf<String?>(null) }
  var listOpen by remember(orderId) { mutableStateOf(false) }
  LaunchedEffect(orderId, rev, retry) {
    val r = repo.risk(orderId)
    val v = r.valueOrNull()
    if (v != null) { view = v; failed = false } else if (view == null) failed = true
  }
  val v = view
  if (v == null) {
    if (failed) V3Message(V3Tone.INFO, StrRisk.orderRiskFailed, Modifier.padding(top = 8.dp), action = StrKit.retry, onAction = { retry++ })
    return
  }
  if (!OrderRisk.visible(v)) return
  val h = v.history
  V3Section(StrRisk.orderRiskTitle, footer = StrRisk.orderRiskFooter) {
    Column(Modifier.fillMaxWidth()) {
      if (h != null && h.orders > 0) {
        V3ListRow(
          StrRisk.orderRiskHistory(h.orders, h.completed, h.cancelledByCustomer + h.cancelledByShop + h.rejectedByShop, h.open),
          icon = Icons.Outlined.History, chevron = false, subtitleLines = 2,
        )
      }
      OrderRisk.shown(v).forEach { f ->
        V3Message(
          if (f.severity == OrderRisk.Severity.WARN) V3Tone.WARNING else V3Tone.INFO,
          flagText(f),
          Modifier.padding(top = 6.dp),
        )
      }
      if (canList) {
        RiskListButtons(phone, pin != null, v.device, onAdd = { adding = it }, onPin = { addingPin = true }, onDevice = { addingDevice = it }, onList = { listOpen = true })
      }
    }
  }
  adding?.let { list ->
    val p = phone
    if (p != null) RiskEntrySheet(repo, list, pinKind = false, fixedPhone = p, fixedPin = null, onClose = { adding = null }, onDone = { adding = null; retry++ })
  }
  if (addingPin && pin != null) {
    RiskEntrySheet(repo, OrderRisk.BLOCK, pinKind = true, fixedPhone = null, fixedPin = pin, onClose = { addingPin = false }, onDone = { addingPin = false; retry++ })
  }
  addingDevice?.let { list ->
    RiskEntrySheet(repo, list, pinKind = false, fixedPhone = null, fixedPin = null, deviceOrderId = orderId,
      onClose = { addingDevice = null }, onDone = { addingDevice = null; retry++ })
  }
  if (listOpen) RiskListSheet(repo, onClose = { listOpen = false; retry++ })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RiskListButtons(
  phone: String?, hasPin: Boolean, hasDevice: Boolean, onAdd: (String) -> Unit, onPin: () -> Unit, onDevice: (String) -> Unit, onList: () -> Unit,
) {
  FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (phone != null) {
      V3Button(StrRisk.blockNumber, { onAdd(OrderRisk.BLOCK) }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Block)
      V3Button(StrRisk.trustNumber, { onAdd(OrderRisk.ALLOW) }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.VerifiedUser)
    }
    if (hasPin) V3Button(StrRisk.blockPlace, onPin, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.WrongLocation)
    if (hasDevice) {
      V3Button(StrRisk.blockDevice, { onDevice(OrderRisk.BLOCK) }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Devices)
      V3Button(StrRisk.trustDevice, { onDevice(OrderRisk.ALLOW) }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.VerifiedUser)
    }
    V3Button(StrRisk.listsTitle, onList, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Checklist)
  }
}

/** آخرین صفِ خوانده‌شده (`/risk/queue`) و کدِ خطا وقتی هیچ پاسخی نیست — یک بار برای کارتِ بالا و نشانِ کارت‌ها */
internal class RiskQueueState {
  val queue = mutableStateOf<OrderRisk.Queue?>(null)
  val failedStatus = mutableStateOf<Int?>(null)
  val retry = mutableStateOf(0)
}

/** [on] = `risk` ِ `/config`؛ با هر تغییرِ صف ([rev]، [changed]) یا «دوباره» یک پرسش؛ خاموش ⇒ هیچ پرسشی */
@Composable
internal fun rememberRiskQueue(on: Boolean, repo: OrdersRepository, rev: Long, changed: Any?): RiskQueueState {
  val st = remember { RiskQueueState() }
  LaunchedEffect(on, st.retry.value, rev, changed) {
    if (!on) {
      st.queue.value = null
      st.failedStatus.value = null
    } else when (val r = repo.riskQueue()) {
      is ApiResult.Success -> { st.queue.value = r.value; st.failedStatus.value = null }
      is ApiResult.Failure -> if (st.queue.value == null) st.failedStatus.value = r.error.status
    }
  }
  return st
}

/**
 *  RSK-03 «کارت در صف»: «N سفارش پیش از ارسال نگاهِ دوباره می‌خواهد» با شمارهٔ هر سفارش و هشدارهایش (زدن ⇒ همان سفارش).
 *  ⛔ تصمیم `OrderRisk.queueCard`: پرچمِ خاموش، سرورِ کهنه (۴۰۴) یا بی هشدار ⇒ هیچ؛ خطا ⇒ «سنجیده نشد» با «دوباره».
 */
@Composable
internal fun OrderRiskQueueCardV3(st: RiskQueueState, on: Boolean, onOpen: (String) -> Unit) {
  val q = st.queue.value
  when (OrderRisk.queueCard(on, q, st.failedStatus.value)) {
    OrderRisk.QueueCard.HIDDEN -> Unit
    OrderRisk.QueueCard.FAILED -> V3Message(V3Tone.INFO, StrRisk.queueFailed, action = StrKit.retry, onAction = { st.retry.value++ })
    OrderRisk.QueueCard.ITEMS -> if (q != null) {
      V3Section(StrRisk.queueTitle(q.items.size), footer = if (q.truncated) StrRisk.queueTruncated else StrRisk.queueFooter) {
        Column(Modifier.fillMaxWidth()) {
          q.items.forEach { i ->
            V3ListRow(
              StrOnline.orderTitle(i.orderNo),
              subtitle = i.flags.filter { it.severity == OrderRisk.Severity.WARN }.joinToString(" · ") { flagText(it) },
              icon = Icons.Outlined.WarningAmber, subtitleLines = 3,
              onClick = { onOpen(i.orderId) },
            )
          }
        }
      }
    }
  }
}

/** متنِ هر نشانهٔ سرور؛ کدِ ناشناختهٔ سرورِ تازه‌تر ⇒ متنِ عمومی با خودِ کد */
internal fun flagText(f: OrderRisk.Flag): String = when (f.code) {
  "blocklisted" -> StrRisk.flagBlocklisted
  "allowlisted" -> StrRisk.flagAllowlisted
  "new_customer" -> StrRisk.flagNewCustomer
  "delivery_failed_before" -> StrRisk.flagDeliveryFailed(f.count)
  "no_show_before" -> StrRisk.flagNoShow(f.count)
  "returned_to_shop_before" -> StrRisk.flagReturnedToShop(f.count)
  "returns_before" -> StrRisk.flagReturns(f.count)
  "cancels_often" -> StrRisk.flagCancelsOften(f.count)
  "many_open" -> StrRisk.flagManyOpen(f.count)
  "possible_duplicate" -> f.orderNo?.let { StrRisk.flagDuplicateOf(it) } ?: StrRisk.flagDuplicate
  else -> StrRisk.flagOther(f.code)
}
