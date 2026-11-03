package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrRisk
import ir.vil3ntec.tohid.core.orders.OrderRisk
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.launch

/**
 *  RSK-03 (§۶.۲۱) — «سابقهٔ رد و برگشتِ مشتری پیش از ارسال» زیرِ سرِ جزئیاتِ سفارشِ اینترنتی: شمارِ سفارش‌های پیشینِ همین
 *  شماره در همین دکان و نشانه‌های توضیح‌دارِ سرور (تحویلِ ناموفق، نبودن سرِ تحویل، برگشت به دکان، لغوِ پیاپی، فهرستِ
 *  بسته/مطمئن، شاید تکراری). منطق `core/orders/OrderRisk` (commonMain، آیفون هم)؛ این‌جا فقط کشیدن.
 *
 *  RSK-04 — صاحب و مدیر (`orders.contact`، همان‌ها که شمارهٔ کامل را می‌بینند) از همین کارت شمارهٔ همین سفارش را با دلیل
 *  و انقضا به فهرستِ بسته یا مطمئن می‌برند و ردیف‌های فعالِ فهرست را می‌بینند و برمی‌دارند. پین از برنامه هنوز نه.
 *
 *  ⛔ پرچمِ سرورِ `shop.order_risk` خاموش (پیش‌فرض) ⇒ `enabled = false` ⇒ هیچ چیز کشیده نمی‌شود؛ صفحه همان امروز.
 *  ⛔ هیچ تصمیمی خودکار گرفته نمی‌شود (رد و قبول همان دکمه‌های امروز). شماره در کارت نشان داده نمی‌شود.
 *  خطای شبکه ⇒ «خوانده نشد» با «دوباره» (نه «بی سابقه»).
 */
@Composable
internal fun OrderRiskCardV3(orderId: String, repo: OrdersRepository, rev: Long = 0, canList: Boolean = false, phone: String? = null) {
  var view by remember(orderId) { mutableStateOf<OrderRisk.View?>(null) }
  var failed by remember(orderId) { mutableStateOf(false) }
  var retry by remember(orderId) { mutableStateOf(0) }
  var adding by remember(orderId) { mutableStateOf<String?>(null) }
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
      if (canList) RiskListButtons(phone, onAdd = { adding = it }, onList = { listOpen = true })
    }
  }
  adding?.let { list ->
    val p = phone
    if (p != null) AddEntrySheet(repo, list, p, onClose = { adding = null }, onDone = { adding = null; retry++ })
  }
  if (listOpen) ListSheet(repo, onClose = { listOpen = false; retry++ })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RiskListButtons(phone: String?, onAdd: (String) -> Unit, onList: () -> Unit) {
  FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (phone != null) {
      V3Button(StrRisk.blockNumber, { onAdd(OrderRisk.BLOCK) }, kind = V3ButtonKind.SECONDARY, icon = Icons.Outlined.Block)
      V3Button(StrRisk.trustNumber, { onAdd(OrderRisk.ALLOW) }, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.VerifiedUser)
    }
    V3Button(StrRisk.listsTitle, onList, kind = V3ButtonKind.NEUTRAL, icon = Icons.Outlined.Checklist)
  }
}

/** «بستن/مطمئن کردنِ این شماره»: دلیل (۳ تا ۲۰۰) و انقضا (۱ تا ۳۶۵ روز، پیش‌فرض ۳۰) — همان مرزهای سرور */
@Composable
private fun AddEntrySheet(repo: OrdersRepository, list: String, phone: String, onClose: () -> Unit, onDone: () -> Unit) {
  val scope = rememberCoroutineScope()
  var reason by remember { mutableStateOf("") }
  var days by remember { mutableStateOf(OrderRisk.DAYS_DEFAULT.toString()) }
  var tried by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  val n = ir.vil3ntec.tohid.latinDigits(days).trim().toIntOrNull()
  val err = OrderRisk.entryError(list, phone, reason, n)
  V3Sheet(onDismiss = onClose, title = if (list == OrderRisk.BLOCK) StrRisk.blockNumber else StrRisk.trustNumber) { _ ->
    Text(if (list == OrderRisk.BLOCK) StrRisk.blockHelp else StrRisk.trustHelp, style = V3Type.subhead, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(10.dp))
    V3TextField(reason, { reason = it.take(OrderRisk.REASON_MAX); failure = null }, StrRisk.entryReason, required = true,
      error = StrRisk.entryReasonError.takeIf { tried && err == "reason_required" })
    Spacer(Modifier.height(10.dp))
    V3TextField(days, { days = it.take(3); failure = null }, StrRisk.entryDays, kind = V3FieldKind.NUMBER, required = true,
      error = StrRisk.entryDaysError.takeIf { tried && err == "bad_expiry" })
    failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(14.dp))
    V3Button(StrKit.save, {
      tried = true
      if (err == null && n != null && !busy) {
        busy = true
        scope.launch {
          val r = repo.addRiskEntry(OrderRisk.phoneEntryBody(list, phone, reason, n))
          busy = false
          if (r.valueOrNull() != null) onDone() else failure = r.errorMessage()
        }
      }
    }, Modifier.fillMaxWidth(), loading = busy, haptic = true)
  }
}

/** ردیف‌های فعالِ فهرستِ بسته و مطمئن با «برداشتن» (نرم؛ سابقه می‌ماند) */
@Composable
private fun ListSheet(repo: OrdersRepository, onClose: () -> Unit) {
  val scope = rememberCoroutineScope()
  var items by remember { mutableStateOf<List<OrderRisk.Entry>?>(null) }
  var failure by remember { mutableStateOf<String?>(null) }
  var reload by remember { mutableStateOf(0) }
  var busy by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(reload) {
    val r = repo.riskLists()
    r.valueOrNull()?.let { items = it; failure = null } ?: run { failure = r.errorMessage() }
  }
  V3Sheet(onDismiss = onClose, title = StrRisk.listsTitle) { _ ->
    val list = items
    when {
      list == null && failure != null -> V3Message(V3Tone.ERROR, failure.orEmpty(), action = StrKit.retry, onAction = { reload++ })
      list == null -> V3Skeleton(rows = 3)
      list.isEmpty() -> Text(StrRisk.listsEmpty, style = V3Type.callout, color = PhoneV3.colors.inkMuted)
      else -> list.forEach { e ->
        V3ListRow(
          e.phone.let { p ->
            when {
              p != null && e.list == OrderRisk.BLOCK -> StrRisk.entryBlocked(p)
              p != null -> StrRisk.entryTrusted(p)
              else -> StrRisk.entryPin(e.radiusM ?: 0L)
            }
          },
          subtitle = StrRisk.entrySubtitle(e.reason, ir.vil3ntec.tohid.formatMillis(e.expiresAt)),
          icon = if (e.list == OrderRisk.BLOCK) Icons.Outlined.Block else Icons.Outlined.VerifiedUser,
          chevron = false, subtitleLines = 3,
          trailing = {
            V3Button(StrRisk.remove, {
              if (busy == null) {
                busy = e.id
                scope.launch {
                  val r = repo.removeRiskEntry(e.id)
                  busy = null
                  if (r.valueOrNull() != null) reload++ else failure = r.errorMessage()
                }
              }
            }, kind = V3ButtonKind.NEUTRAL, loading = busy == e.id)
          },
        )
      }
    }
    if (items != null) failure?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
  }
}

/** متنِ هر نشانهٔ سرور؛ کدِ ناشناختهٔ سرورِ تازه‌تر ⇒ متنِ عمومی با خودِ کد */
private fun flagText(f: OrderRisk.Flag): String = when (f.code) {
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
