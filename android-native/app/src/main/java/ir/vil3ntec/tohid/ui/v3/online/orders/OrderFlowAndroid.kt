package ir.vil3ntec.tohid.ui.v3.online.orders

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrderFlow
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderFlow
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/*
 *  پروندهٔ سکو (`*Android.kt`): ثبتِ فاکتور `Context` می‌خواهد (دفتر، همگام‌سازی، لرزش)؛ منطقِ خالص در
 *  `core/orders/OrderFlow` است و آیفون همین پرونده را با شیم‌های همان API می‌سازد.
 */

/**
 *  جزئیاتِ سفارشِ آنلاین (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۳): «پرداختِ مشتری» (حالِ `payStep` ِ سرور — «پرداخت پس از
 *  پذیرش»، نقد، حساب‌پی) و «فاکتور»: یک ضرب ⇒ همهٔ قلم‌ها با همان قیمتِ توافق‌شدهٔ سفارش یک‌جا فاکتور می‌شوند.
 *
 *  ⛔ فاکتور همان فروشِ تحویلِ سفارش است (`OrderFulfillment.fulfill` ⇒ `SalesEngine.record`، شناسهٔ ثابتِ `o-<سفارش>`،
 *     سریِ O با شمارهٔ همان سفارش، زمانِ «فاکتور شد» ِ سرور): دو بار زدن یا دو دستگاه یک فاکتور، تحویلِ بعدی «پیش‌تر
 *     ثبت شده»، و موجودی یک بار. سبدِ آزادِ صندوق نه: تخفیفِ خودکار، امتیاز و قیمتِ دستی جمعی می‌ساختند که مشتری
 *     نپذیرفته — تغییرِ قلم فقط با «پیشنهادِ تازه» (ORD-14) پیش از بسته‌بندی.
 *  ⛔ گام‌ها از `OrderFlow.plan`: پیش از «بسته شد» نخست بسته‌بندی؛ «دریافت از دکان» ِ نقدی ⇒ فاکتور با تحویل به مشتری.
 */
@Composable
internal fun RouteScope.OrderFlowCardsV3(o: OrderDto, cfg: OrdersConfigDto?) {
  o.payStep?.let { PayStepCard(o, it.state, it.choice) }
  InvoiceCard(o, cfg)
}

@Composable
private fun PayStepCard(o: OrderDto, state: String, choice: String) {
  if (state.isBlank()) return
  V3Section(null) {
    V3ListRow(
      StrOrderFlow.payTitle, subtitle = StrOrderFlow.payState(state, o.fulfillment == "pickup", choice == "cash"),
      icon = Icons.Outlined.Payments, chevron = false, subtitleLines = 3,
    )
  }
}

@Composable
private fun RouteScope.InvoiceCard(o: OrderDto, cfg: OrdersConfigDto?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ledger by store.data.collectAsState()
  val perms = cfg?.permissions ?: OrderPermsDto()
  val invoicedAt = OrderFulfillment.invoicedAt(o)
  val sale = OrderFulfillment.saleOf(ledger, o.id)
  val pack = OrdersHubModel.prep(o, perms, chatOn = false).pack
  val plan = if (perms.manage && OrdersLive.canRecord(cfg, o)) {
    OrderFlow.plan(o.status, o.fulfillment, o.paymentStatus, o.isTest, invoiced = invoicedAt != null, pack = pack)
  } else null
  if (invoicedAt == null && plan == null) return
  var asking by remember(o.id) { mutableStateOf(false) }
  var busy by remember(o.id) { mutableStateOf(false) }
  var error by remember(o.id) { mutableStateOf<String?>(null) }

  V3Section(StrOrderFlow.invoiceTitle) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (invoicedAt != null) {
        val label = sale?.invoiceLabel() ?: "${OrderFulfillment.SERIES}-${o.orderNo}"
        V3Message(V3Tone.SUCCESS, StrOrderFlow.invoiced(label, OrdersModel.stamp(invoicedAt)))
        if (sale == null) Text(StrOrderFlow.invoicedElsewhere, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
      } else if (plan != null) {
        Text(StrOrderFlow.invoiceHint(o.lines.size.takeIf { it > 0 } ?: o.itemsCount, OnlineFmt.afn(o.totalMinor)), style = V3Type.callout, color = PhoneV3.colors.ink)
        Text(
          when {
            o.paymentStatus == "paid" -> StrOrderFlow.payOnline
            plan.handsOver -> StrOrderFlow.payCounter
            else -> StrOrderFlow.payCourier
          },
          style = V3Type.footnote, color = PhoneV3.colors.inkMuted,
        )
        if (plan.packs) Text(StrOrderFlow.packFirst, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
        if (!asking) {
          V3Button(
            when {
              plan.handsOver -> StrOrderFlow.invoiceAndHandOver
              plan.packs -> StrOrderFlow.packAndInvoice
              else -> StrOrderFlow.toInvoice
            },
            { asking = true; error = null }, Modifier.fillMaxWidth(), kind = V3ButtonKind.PRIMARY, icon = Icons.Outlined.ReceiptLong,
            haptic = true,
          )
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            V3Button(
              StrOrderFlow.confirmInvoice,
              {
                if (!busy) {
                  busy = true
                  error = null
                  scope.launch {
                    val failed = runInvoice(context, store, o, plan) { toasts.show(it) }
                    if (failed == null) {
                      Haptics.success(context)
                      toasts.show(StrOrderFlow.invoiceDone(o.orderNo))
                      asking = false
                    } else error = failed
                    busy = false
                  }
                }
              },
              Modifier.weight(1f), kind = V3ButtonKind.PRIMARY, loading = busy, haptic = true,
            )
            V3Button(StrOrderFlow.notNow, { asking = false }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy)
          }
        }
      }
      error?.let { V3Message(V3Tone.ERROR, it) }
    }
  }
}

/**
 *  گام‌های [plan] به ترتیب — هر کدام همان درِ همیشگی: گذار ⇒ `OrdersHub.transition` (که تحویل را هم فروش می‌کند)،
 *  «فاکتور شد» ⇒ `POST /shop/orders/:id/invoice` (سرور یک بار، زیرِ قفل)، فروش ⇒ [record]. پیش از هر گذار موجودی
 *  مثلِ صندوق سنجیده می‌شود تا سفارشِ «بسته‌شده» بی فاکتور نماند. ⇒ null یا پیامِ خطا.
 */
private suspend fun runInvoice(context: Context, store: ShopStore, o: OrderDto, plan: OrderFlow.Plan, say: (String) -> Unit): String? {
  if (!store.loaded.value) return StrOrderFlow.ledgerNotReady
  val dry = OrderFulfillment.fulfill(
    store.data.value, o, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), blockExpired = ShopPolicy.blockExpired(context),
  )
  if (dry is OrderFulfillment.Result.Failed) return StrOrderFlow.invoiceFailed(dry.message)
  var cur = o
  for (to in plan.before) {
    when (val r = OrdersHub.transition(context, store, cur, to, say = say)) {
      is ApiResult.Success -> cur = r.value
      is ApiResult.Failure -> return if (r.error.code == "status_changed") StrOnline.staleStatus else r.error.userMessage
    }
  }
  when (val c = OrdersHub.repo(context).invoice(cur.id)) {
    is ApiResult.Success -> { cur = c.value; OrdersHub.put(cur); OrdersHub.changed.value++ }
    is ApiResult.Failure -> return StrOrderFlow.invoiceFailed(c.error.userMessage)
  }
  if (plan.recordNow) record(context, store, cur)?.let { return it }
  for (to in plan.after) {
    when (val r = OrdersHub.transition(context, store, cur, to, say = say)) {
      is ApiResult.Success -> cur = r.value
      is ApiResult.Failure -> return if (r.error.code == "status_changed") StrOnline.staleStatus else r.error.userMessage
    }
  }
  return null
}

/**
 *  فروشِ «فاکتور شد» همین حالا در دفترِ همین دستگاه — همان `OrderFulfillment.fulfill` ِ تحویل، زیرِ قفلِ موتور
 *  (`ShopStore.update`، همیشه روی تازه‌ترین دفتر: پیش‌تر ثبت‌شده ⇒ هیچ)، با زمان و روزِ «فاکتور شد» ِ سرور؛ موجودی
 *  مثلِ صندوق (`allowShortage = false`). سپس همگام‌سازیِ فوری تا دستگاه‌های دیگر و سرور همان را ببینند.
 */
private suspend fun record(context: Context, store: ShopStore, o: OrderDto): String? {
  val at = OrderFulfillment.invoicedAt(o) ?: System.currentTimeMillis()
  val cal = java.util.Calendar.getInstance().apply { timeInMillis = at }
  val day = "${cal.get(java.util.Calendar.YEAR)}-" + (cal.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0') +
    "-" + cal.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  val seller = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
  val blockExpired = ShopPolicy.blockExpired(context)
  var saved = false
  var failed: String? = null
  val wrote = store.update { cur ->
    when (val r = OrderFulfillment.fulfill(cur, o, day, at, seller = seller, blockExpired = blockExpired, allowShortage = false)) {
      is OrderFulfillment.Result.Ok -> { saved = true; r.data }
      is OrderFulfillment.Result.Already -> null
      is OrderFulfillment.Result.Failed -> { failed = r.message; null }
      is OrderFulfillment.Result.Review -> { failed = r.message; null }
    }
  }
  failed?.let { return StrOrderFlow.invoiceFailed(it) }
  if (saved && wrote != true) return StrOrderFlow.notWritten
  if (saved) ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
  return null
}
