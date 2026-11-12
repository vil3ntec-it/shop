package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.data.CancelAsk
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.isOnlineOrder
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.staff.ApprovalDialog
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  دو پنجرهٔ لغو و «اصلاح» ِ تاریخچهٔ فروشِ امروز (`SalesHistoryScreen`) — همان متن (سه‌زبانه، `StrInvoices`) و همان ظاهرِ پیشین،
 *  جدا شده تا SEC-03 فایلِ تاریخچه را از ۸۰۰ خط بزرگ‌تر نکند. کنترل و اثر در صفحه است
 *  (`InvoiceVoid.check` ⇐ `SalesEngine.cancel` ⇐ سندِ معکوس)؛ این‌ها فقط می‌پرسند.
 *  نسخهٔ ۳ (`CancelSheet`، #391) همین کنترل را از core می‌خواند.
 */

/**
 *  اصلاحِ فاکتور.
 *
 *  ویرایشِ مستقیمِ فاکتورِ ثبت‌شده ممکن نیست و نباید هم باشد: فاکتور سند است و شمارهٔ آن در حسابِ
 *  قرض‌دار و سابقهٔ عملیات نوشته شده (SEC-03). ولی تا دیروز راهی هم برای اشتباهِ ساده نبود: فروشنده
 *  باید فاکتور را لغو می‌کرد و کلِ سبد را از نو می‌ساخت. حالا همان کار یک دکمه است — فاکتور لغو می‌شود
 *  (سندِ معکوس؛ موجودی برمی‌گردد) و اقلامش با همان قیمتِ همان فاکتور دوباره در سبد می‌نشینند.
 */
@Composable
internal fun ReopenInvoiceDialog(sale: Sale, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrInvoices.reopenTitle, color = Shop.colors.text) },
    text = {
      Text(
        StrInvoices.reopenBody(sale.invoiceLabel() ?: "0"),
        style = MaterialTheme.typography.bodySmall,
        color = Shop.colors.muted,
      )
    },
    confirmButton = { TextButton(onClick = onConfirm) { Text(StrInvoices.cancelAndToCart) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.goBack) } },
  )
}

/** لغوِ فروش — بی دلیل ثبت نمی‌شود؛ دلیل در فاکتور، سابقه و سندِ معکوس می‌ماند */
@Composable
internal fun CancelInvoiceDialog(sale: Sale, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  var reason by remember(sale.id) { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrInvoices.cancelSaleTitle, color = Shop.colors.text) },
    text = {
      Column {
        Text(
          SalesEngine.cancelWarning(sale),
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it.take(InvoiceVoid.REASON_MAX) },
          label = { Text(StrInvoices.cancelReason) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(enabled = reason.isNotBlank(), onClick = { onConfirm(reason) }) {
        Text(StrInvoices.cancelSale, color = if (reason.isNotBlank()) Shop.colors.danger else Shop.colors.muted)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.goBack) } },
  )
}

/**
 *  STF-02 — برگهٔ تأییدِ مدیر برای لغوِ [sale] در جدولِ کامپیوتر (نقشِ «نیازمندِ تأیید» روی Sync v3، `CancelAsk.offered`): همان
 *  نیاز و همان نوشته‌های `CancelRoute` ِ نسخهٔ ۳، روی پنجرهٔ مشترکِ `ApprovalDialog` (PIN ِ تأییدکنندهٔ همین دستگاه یا «پرسیدن از مدیر»).
 */
@Composable
internal fun CancelApprovalDialog(sale: Sale, shopId: String, requesterUserId: String, onApproved: (ApprovalSlip.Slip) -> Unit, onDismiss: () -> Unit) =
  ApprovalDialog(
    title = StrSale.cancelApproval,
    summary = StrSale.cancelApprovalSummary(sale.invoiceLabel() ?: "0", money(sale.finalTotal)),
    shopId = shopId, requesterUserId = requesterUserId, need = CancelAsk.need(sale), onApproved = onApproved, onDismiss = onDismiss,
  )

/** پیوندِ دفترِ امروز با `InvoiceVoid` (SEC-03) */
internal object SalesVoid {

  fun facts(d: ShopData, saleId: String): InvoiceVoid.Facts? =
    d.sales.find { it.id == saleId }?.let { InvoiceVoid.Facts(it.status, it.isOnlineOrder()) }

  /** ردِ `InvoiceVoid.check` ⇐ همان پیام‌های امروزِ صفحه و موتور */
  fun denied(code: String, noPermission: String): String = when (code) {
    "already_cancelled" -> StrInvoices.alreadyCancelled
    "online_order" -> SalesEngine.ONLINE_ORDER_LOCKED
    "no_permission" -> noPermission
    "reason_required" -> StrInvoices.reasonRequired
    "reason_too_long" -> StrInvoices.reasonTooLong(InvoiceVoid.REASON_MAX)
    else -> StrInvoices.invoiceNotFound
  }

  /** نتیجهٔ یک لغو یا «اصلاح» پیش از ذخیره */
  sealed class Outcome {
    /** ردِ `InvoiceVoid.check` با پیامِ صفحه؛ دفتر دست نخورد */
    data class Denied(val message: String) : Outcome()
    /** ردِ خودِ موتور (`SalesEngine.cancel`)؛ دفتر دست نخورد */
    data class Failed(val message: String) : Outcome()
    /** دفترِ پس از لغو و دلیلِ پذیرفته (اصلاح ⇒ همان `CORRECTION_REASON`) */
    data class Ok(val data: ShopData, val reason: String) : Outcome()
  }

  /**
   *  SEC-03 — کنترلِ یگانه و اثرِ دفتریِ لغو/«اصلاح» برای هر صفحه (نسخهٔ ۳: `CancelRoute`/`FixRoute`):
   *  `InvoiceVoid.check` ⇐ `SalesEngine.cancel` با همان دلیلِ پذیرفته. نه ذخیره می‌کند، نه زنجیره را؛ صفحه پس از
   *  `store.save` ِ موفق `DocChainSync.recordVoid` را با دفترِ **پیش از** لغو صدا می‌زند.
   */
  fun run(
    d: ShopData,
    saleId: String,
    canCancel: Boolean,
    kind: InvoiceVoid.Kind,
    reason: String,
    today: String,
    now: Long,
    newId: () -> String,
    by: String?,
    noPermission: String,
    /** STF-02: برگهٔ تأییدِ لغو (فقط Sync v3، `CancelApproval.offered`) — جای «اجازه» برای نقشِ «نیازمند تأیید» */
    cancelApproval: kotlinx.serialization.json.JsonObject? = null,
  ): Outcome = when (val check = InvoiceVoid.check(facts(d, saleId), canCancel || cancelApproval != null, kind, reason)) {
    is InvoiceVoid.Decision.Denied -> Outcome.Denied(denied(check.code, noPermission))
    is InvoiceVoid.Decision.Allowed -> when (val r = SalesEngine.cancel(d, saleId, today, now, newId, reason = check.reason, by = by)) {
      is SalesEngine.Result.Failed -> Outcome.Failed(r.message)
      is SalesEngine.Result.Ok -> Outcome.Ok(
        if (cancelApproval == null) r.data
        else r.data.copy(sales = r.data.sales.map { if (it.id == saleId) it.copy(cancelApproval = cancelApproval) else it }),
        check.reason,
      )
    }
  }
}
