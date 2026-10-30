package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.HandedOverLine
import ir.vil3ntec.tohid.core.delivery.HandedOverOrder
import ir.vil3ntec.tohid.core.delivery.HandoverKind
import ir.vil3ntec.tohid.core.delivery.HandoverSale
import ir.vil3ntec.tohid.core.delivery.HandoverSaleResult
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  تحویلِ حضوری با کد (`delivery/pickup/{orderId}`، DLV-15): نامِ مشتری برای تطبیق، کدِ ۶ رقمی (یا
 *  تأییدِ دستیِ مدیر با توضیح)، نقدِ صندوق (فرق با سفارش ⇐ دلیل) و «تحویل شد» ⇐ فروشِ همین سفارش از
 *  راهِ `HandoverSale` (همان تصمیمِ صاحب: موجودی را فروشِ برنامه می‌زند، نه سفارشِ آنلاین).
 */
@Composable
internal fun PickupHandoverSheet(
  order: OrderDto,
  m: Mission,
  cfg: DeskConfig,
  repo: DeliveryRepository,
  ordersRepo: OrdersRepository,
  onDismiss: () -> Unit,
  onDone: (String) -> Unit,
) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var h by remember { mutableStateOf(DeliveryForms.startHandover(m)) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val keys = remember { DeliveryRepository.OnceKey() }
  val cashMinor = minorOf(h.cash)
  val differs = DeliveryForms.handoverDiffers(m, h)

  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlHandoverTitle(order.orderNo)) { _ ->
    Text(StrOnline.dlMatchName, style = V3Type.footnote, color = c.inkMuted)
    Text(order.customer.name, style = V3Type.title, color = c.ink)
    Spacer(Modifier.height(14.dp))
    if (!h.manual) {
      V3TextField(
        h.code, { h = h.copy(code = it.filter(Char::isDigit).take(DeliveryForms.CODE_LENGTH)) }, StrOnline.dlCodeShown,
        kind = V3FieldKind.NUMBER, placeholder = "123456", modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
    }
    if (cfg.perms.assign) {
      V3Card(padding = PaddingValues(0.dp)) {
        V3ToggleRow(StrOnline.dlManualApprove, h.manual, { h = h.copy(manual = it); err = null }, icon = Icons.Outlined.AdminPanelSettings, hue = V3Hue.VIOLET)
      }
      Spacer(Modifier.height(12.dp))
    }
    V3TextField(
      h.cash, { h = h.copy(cash = it.take(DeliveryForms.NUMBER_MAX)) }, StrOnline.dlCashAtTill, kind = V3FieldKind.MONEY,
      helper = if (m.expectedCollectMinor > 0) StrOnline.dlOrderAmount(StrOnline.afn(m.expectedCollectMinor)) else StrOnline.pay("paid", ""),
      error = if (cashMinor == null) StrOnline.dlBadNumber else null, modifier = Modifier.fillMaxWidth(),
    )
    if (differs) {
      Spacer(Modifier.height(10.dp))
      Text(StrOnline.dlCashDiffers, style = V3Type.subhead, color = c.ink)
      Spacer(Modifier.height(8.dp))
      ReasonChips(reasonsOf(cfg, "cash"), h.cashReason) { h = h.copy(cashReason = it) }
    }
    if (h.manual || h.cashReason == "other") {
      Spacer(Modifier.height(12.dp))
      V3TextField(h.note, { h = h.copy(note = it.take(DeliveryForms.NOTE_MAX)) }, StrOnline.dlNoteRequired, required = true, singleLine = false, modifier = Modifier.fillMaxWidth())
    }
    err?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it, Modifier.v3Shake(it)) }
    Spacer(Modifier.height(20.dp))
    V3Button(
      StrOnline.missionStatus("delivered"),
      {
        busy = true
        err = null
        scope.launch {
          val body = DeliveryWire.statusJson(
            to = "delivered", from = m.status, note = h.note, code = if (h.manual) "" else h.code,
            cashCollectedMinor = cashMinor, cashReason = if (differs) h.cashReason else "",
          )
          when (val r = repo.status(m.id, body, keys.of(body))) {
            is ApiResult.Failure -> {
              err = r.error.userMessage
              if (r.error.code == "bad_code") h = h.copy(code = "")
            }
            is ApiResult.Success -> onDone(recordSale(order, m, cashMinor ?: 0L, ordersRepo))
          }
          busy = false
        }
      },
      Modifier.fillMaxWidth(),
      enabled = DeliveryForms.handoverValid(m, h) && !busy,
      loading = busy,
      haptic = true,
    )
  }
}

/**
 *  پس از «تحویل شد»: فروشِ همین سفارش از راهِ قلابِ `HandoverSale` (جابه‌جاشده از `DeliveryDialogs.kt`،
 *  همان منطق؛ #340، `OrdersLive.postById`: از سرور می‌پرسد و با شناسهٔ سفارش یکتا ثبت می‌کند). تحویل
 *  پیش‌تر در سرور ثبت شده؛ اگر ثبت همین حالا نشد، برنامه آن را از فهرستِ «تحویلِ بی‌فروش» دوباره
 *  ثبت می‌کند — کسی در صندوق دستی نمی‌زند (دو بار می‌شد).
 */
internal suspend fun recordSale(order: OrderDto, m: Mission, collected: Long, ordersRepo: OrdersRepository): String {
  val hook = HandoverSale.hook ?: return StrOnline.dlSaleNoHook(order.orderNo)
  //  قلاب فقط شناسهٔ سفارش را لازم دارد؛ جزئیات را خودش از سرور می‌گیرد
  val full = ordersRepo.get(order.id).valueOrNull() ?: order
  val handed = HandedOverOrder(
    kind = HandoverKind.PICKUP_AT_STORE,
    orderId = full.id, orderNo = full.orderNo, assignmentId = m.id, customerName = full.customer.name,
    paymentMethod = full.paymentMethod, paymentStatus = full.paymentStatus,
    subtotalMinor = full.subtotalMinor, discountMinor = full.discountMinor, deliveryFeeMinor = full.deliveryFeeMinor,
    totalMinor = full.totalMinor, collectedMinor = collected,
    lines = full.lines.map { HandedOverLine(it.productId, it.name, it.qtyMilli, it.unitPriceMinor, it.lineTotalMinor) },
  )
  return when (val r = runCatching { hook.record(handed) }.getOrElse { HandoverSaleResult.Failed(it.message ?: StrOnline.error) }) {
    is HandoverSaleResult.Recorded -> StrOnline.dlSaleRecorded(order.orderNo)
    is HandoverSaleResult.AlreadyRecorded -> StrOnline.dlSaleAlready(order.orderNo)
    is HandoverSaleResult.Failed -> StrOnline.dlSaleFailed(order.orderNo, r.message)
    //  بررسیِ صاحب: نه وعدهٔ «دوباره می‌کوشد»، فقط دلیل
    is HandoverSaleResult.NeedsReview -> StrOnline.dlSaleNeedsReview(order.orderNo, r.message)
  }
}
