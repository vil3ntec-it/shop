package ir.vil3ntec.tohid.ui.v3.sales.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPayStatus
import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PayStatusFlow
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.coroutines.launch

/*
 *  PAY-07 — وضعیتِ پرداخت در نسخهٔ ۳: تراشهٔ فهرستِ فروش، بخشِ «وضعیتِ پرداخت» ِ صفحهٔ فاکتور، برگهٔ تغییرِ وضعیتِ
 *  یک ردیف (`sales/{saleId}/pay/{i}`) و فهرستِ «نیازمند بررسی» ِ صاحب و مدیر (`sales/pay-review`). بی تنظیمِ تازه:
 *  چیزی که تصمیم نمی‌خواهد روشن و بی‌صداست. منطق همه در `data/PayStatusFlow` و `core/pay/PayStatus`.
 */

/** نام، رنگ و پیامِ هر وضعیت — بیرون از جدولِ نوشته (تابعِ بی جای خالی در `StrPayStatus` نمی‌نشیند) */
object PayStatusText {
  fun name(status: String): String = when (status) {
    PayStatus.PENDING -> StrPayStatus.pending
    PayStatus.CONFIRMED -> StrPayStatus.confirmed
    PayStatus.FAILED -> StrPayStatus.failed
    PayStatus.CANCELLED -> StrPayStatus.cancelled
    PayStatus.NEEDS_REVIEW -> StrPayStatus.needsReview
    PayStatus.REFUNDED -> StrPayStatus.refunded
    PayStatus.PARTIAL -> StrPayStatus.partiallyRefunded
    else -> status
  }

  fun action(to: String): String = when (to) {
    PayStatus.CONFIRMED -> StrPayStatus.toConfirmed
    PayStatus.NEEDS_REVIEW -> StrPayStatus.toReview
    PayStatus.FAILED -> StrPayStatus.toFailed
    PayStatus.CANCELLED -> StrPayStatus.toCancelled
    else -> name(to)
  }

  fun error(code: String): String = when (code) {
    "PERM_DENIED" -> StrPayStatus.errDenied
    "BAD_TRANSITION", "BAD_STATUS", "ID_CONFLICT", "NOT_MANUAL", "BAD_INDEX" -> StrPayStatus.errMoved
    "UNREAD" -> StrPayStatus.errUnread
    else -> StrPayStatus.errOther
  }

  fun pill(status: String): V3Status = when (status) {
    PayStatus.CONFIRMED -> V3Status.DELIVERED
    PayStatus.PENDING -> V3Status.AWAITING
    PayStatus.NEEDS_REVIEW -> V3Status.REVIEW
    PayStatus.FAILED -> V3Status.CANCELLED
    PayStatus.CANCELLED -> V3Status.INACTIVE
    else -> V3Status.WARNING
  }

  fun line(r: PayStatusFlow.RowView): String = StrPayStatus.row(PayFlow.methodName(r.method), ir.vil3ntec.tohid.money(PayFlow.major(r.amount)))
}

/** بخشِ «وضعیتِ پرداخت» ِ صفحهٔ فاکتور؛ فاکتورِ بی ردیفِ غیرنقد ⇒ هیچ */
@Composable
internal fun PayStatusSection(rows: List<PayStatusFlow.RowView>, onRow: ((Int) -> Unit)?) {
  if (rows.isEmpty()) return
  V3Section(StrPayStatus.section) {
    rows.forEachIndexed { i, r ->
      if (i > 0) V3Divider(start = 64.dp)
      V3ListRow(
        PayStatusText.line(r),
        icon = Icons.Outlined.Payments,
        hue = V3Hue.BLUE,
        subtitle = if (r.manual) null else StrPayStatus.serverOnly,
        trailing = { V3StatusPill(PayStatusText.pill(r.status), PayStatusText.name(r.status)) },
        chevron = false,
        onClick = if (r.manual && onRow != null) ({ onRow(r.index) }) else null,
      )
    }
  }
}

/** صاحب یا مدیر */
@Composable
private fun manages(): Boolean {
  val context = LocalContext.current
  return remember { !ShopRole.isStaff(context) }
}

/** `sales/{saleId}/pay/{i}` — برگهٔ وضعیتِ یک ردیف: فقط گذارهایی که همین کاربر دارد (`PayStatus.allowed`) */
@Composable
fun RouteScope.PayStatusRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sale = data.sales.find { it.id == args["saleId"].orEmpty() }
  val index = args.int("i") ?: -1
  val row = sale?.let { s -> PayStatusFlow.view(data, s).firstOrNull { it.index == index && it.manual } }
  if (sale == null || row == null) {
    LaunchedEffect(Unit) { toasts.show(StrPayStatus.errMoved, V3Tone.WARNING); back() }
    return
  }
  val sell = remember { ShopRole.canWrite(context, "sales") }
  val manage = manages()
  val me = remember { runCatching { SyncStore(context).accountName }.getOrDefault("").trim().ifEmpty { ShopRole.current(context) } }
  val targets = PayStatus.allowed(row.status, sell, manage)
  //  یک انتخاب در هر باز شدن: ضربهٔ دوم ردیفِ دوم نمی‌سازد و بستنِ برگه دو بار برنمی‌گردد
  val chosen = remember { booleanArrayOf(false) }
  V3Sheet(onDismiss = { if (!chosen[0]) back() }, title = PayStatusText.line(row), detents = false) { closer ->
    Column(Modifier.padding(horizontal = 16.dp)) {
      V3StatusPill(PayStatusText.pill(row.status), PayStatusText.name(row.status))
      Text(
        if (targets.isEmpty()) StrPayStatus.noAction else StrPayStatus.sheetBody,
        style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(vertical = 10.dp),
      )
    }
    targets.forEach { to ->
      V3ListRow(
        PayStatusText.action(to),
        icon = if (to == PayStatus.CONFIRMED) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
        hue = if (to == PayStatus.CONFIRMED) V3Hue.MINT else V3Hue.AMBER,
        danger = to == PayStatus.FAILED || to == PayStatus.CANCELLED,
        chevron = false,
        onClick = onClick@{
          if (chosen[0]) return@onClick
          chosen[0] = true
          //  کلید پیش از ثبت ساخته می‌شود ⇒ تکرارِ همان ثبت یک ردیف
          val key = Ulid.next()
          val req = PayStatusFlow.Request(sale.id, row.index, to, me, sell, manage)
          scope.launch {
            when (val r = PayStatusFlow.record(store, key, req, todayIso(), System.currentTimeMillis())) {
              is PayStatusFlow.Saved.Ok -> toasts.show(StrPayStatus.done, V3Tone.SUCCESS)
              is PayStatusFlow.Saved.Bad -> toasts.show(PayStatusText.error(r.code), V3Tone.ERROR)
              PayStatusFlow.Saved.NotSaved -> toasts.show(StrPayStatus.errOther, V3Tone.ERROR)
            }
            closer.close { back() }
          }
        },
      )
    }
  }
}

/** `sales/pay-review` — «نیازمند بررسی» ِ صاحب و مدیر؛ هر ردیف ⇐ صفحهٔ همان فاکتور */
@Composable
fun RouteScope.PayReviewRoute() {
  if (!manages()) {
    LaunchedEffect(Unit) { toasts.show(StrPayStatus.errDenied, V3Tone.WARNING); back() }
    return
  }
  val items = remember(data) { PayStatusFlow.review(data) }
  V3Scaffold(title = StrPayStatus.reviewTitle, largeTitle = false, onBack = backOrNull) { pad ->
    if (items.isEmpty()) {
      V3EmptyState(Icons.Outlined.FactCheck, StrPayStatus.reviewEmpty)
      return@V3Scaffold
    }
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding() + 16.dp),
    ) {
      V3Section(StrPayStatus.reviewCount(ir.vil3ntec.tohid.plain(items.size))) {
        items.forEachIndexed { i, it ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(
            StrPayStatus.reviewRow(
              it.sale.invoiceLabel() ?: "0", PayFlow.methodName(it.row.method), ir.vil3ntec.tohid.money(PayFlow.major(it.row.amount)),
            ),
            subtitle = ReceiptModel.title(it.sale),
            icon = Icons.Outlined.FactCheck,
            hue = V3Hue.AMBER,
            trailing = { V3StatusPill(V3Status.REVIEW, StrPayStatus.needsReview) },
            onClick = { nav.open(SalesPaths.invoice(it.sale.id)) },
          )
        }
      }
    }
  }
}

/** شمارِ «نیازمند بررسی» برای سطرِ تاریخچه — فقط صاحب و مدیر؛ صفر ⇒ هیچ */
@Composable
internal fun reviewCount(d: ir.vil3ntec.tohid.data.ShopData): Int {
  val manage = manages()
  return remember(d) { if (manage) PayStatusFlow.review(d).size else 0 }
}
