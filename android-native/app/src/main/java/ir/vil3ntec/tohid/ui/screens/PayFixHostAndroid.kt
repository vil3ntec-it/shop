package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPayFix
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.pay.PayFix
import ir.vil3ntec.tohid.data.PayFixFlow
import ir.vil3ntec.tohid.data.PayFixPref
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StaffPins
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.staff.ApprovalDialog
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  POS-40 — مرزِ پلتفرمِ «اصلاحِ روشِ پرداخت» در فاکتورهای گذشته (گوشی و کامپیوتر، `SalesHistoryScreen`).
 *
 *  مجوز از `sales.pay_fix` (`PayFixPref.decision`)؛ «نیازمند تأیید» ⇒ همان `ApprovalDialog` ِ تخفیف و برگشت (PIN ِ مدیر
 *  یا صاحب روی همین دستگاه، برگه برای `auditLog/<شناسهٔ اصلاح>` تا مبلغِ همین اصلاح)؛ نامِ تأییدکننده در `approvedBy`.
 *  پیش از PIN همان قاعده‌ها روی دفترِ صفحه سنجیده می‌شوند تا مدیر برای اصلاحِ ردشدنی PIN نزند؛ ثبتِ واقعی و سنجشِ
 *  نهایی فقط `PayFixPref.record` روی دفترِ همین لحظهٔ دیسک. کلید یک بار با باز شدنِ پنجره ⇒ دو ضربه یک ردیف.
 */
@Composable
fun PayFixHost(d: ShopData, store: ShopStore, sale: Sale, me: String, toast: (String) -> Unit, onClose: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val decision = remember { PayFixPref.decision(context) }
  val key = remember(sale.id) { newId() }
  //  نامِ کاربر؛ دستگاهِ بی حساب ⇒ نامِ نقش (همان «خودِ صاحب»)، هرگز تأییدکننده‌ای ساختگی
  val by = me.trim().ifEmpty { ShopRole.current(context) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var asking by remember { mutableStateOf<PayFixFlow.Request?>(null) }

  fun save(r: PayFixFlow.Request) {
    busy = true
    scope.launch {
      when (val s = PayFixPref.record(store, key, r)) {
        is PayFixPref.Saved.Ok -> { if (!s.again) toast(StrPayFix.done); onClose() }
        is PayFixPref.Saved.Bad -> error = payFixMessage(s.code, s.method)
        PayFixPref.Saved.NotSaved -> Unit
      }
      busy = false
    }
  }

  PayFixDialog(
    d = d, sale = sale, needsApproval = decision == "approve", busy = busy, error = error,
    onSubmit = { x ->
      error = null
      val r = PayFixFlow.Request(sale.id, x.from, x.to, x.amountMinor, by, decision, null, x.reason, x.ref.ifBlank { null })
      //  پیش‌سنجش روی دفترِ صفحه با «مجاز»: هر ردی جز تأیید همین حالا دیده می‌شود
      val pre = PayFixFlow.build(d, key, r.copy(decision = if (decision == "approve") "allow" else decision), ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis())
      when {
        pre is PayFixFlow.Built.Bad -> error = payFixMessage(pre.code, pre.method)
        decision == "approve" -> asking = r
        else -> save(r)
      }
    },
    onDismiss = onClose,
  )

  asking?.let { r ->
    val requester = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountId }.getOrDefault("") }
    ApprovalDialog(
      title = StrPayFix.menu,
      summary = StrPayFix.approvalSummary(money(PayFlow.major(r.amountMinor)), PayFlow.methodName(r.from), PayFlow.methodName(r.to)),
      shopId = ShopRole.shopId(context),
      requesterUserId = requester,
      need = ApprovalSlip.Need(PayFix.PERM, "auditLog/$key", null, r.amountMinor),
      onApproved = { slip ->
        val who = StaffPins(context).get(slip.by)?.name?.ifBlank { null } ?: slip.by
        asking = null
        save(r.copy(approvedBy = who))
      },
      onDismiss = { asking = null },
    )
  }
}

/** کدِ ردِ هسته (`PayFix.check`) ⇒ پیامِ سه‌زبانه */
private fun payFixMessage(code: String, method: String?): String {
  val name = method?.let(PayFlow::methodName).orEmpty()
  return when (code) {
    "DAY_CLOSED" -> StrPayFix.errDayClosed
    "SALE_CANCELLED" -> StrPayFix.errCancelled
    "SALE_ONLINE" -> StrPayFix.errOnline
    "SALE_RETURNED" -> StrPayFix.errReturned
    "PERM_DENIED" -> StrPayFix.errDenied
    "APPROVAL_REQUIRED" -> StrPayFix.errApproval
    "SELF_APPROVAL" -> StrPayFix.errSelf
    "BY_REQUIRED" -> StrPayFix.errBy
    "REASON_REQUIRED", "REASON_TOO_LONG", "BAD_REASON" -> StrPayFix.errReason
    "SAME_METHOD" -> StrPayFix.errSame
    "UNKNOWN_METHOD", "METHOD_DISABLED" -> StrPayFix.errMethod
    "INVALID_AMOUNT" -> StrPayFix.errAmount
    "FIX_EXCEEDS" -> StrPayFix.errExceeds(name)
    "REF_REQUIRED" -> StrPayFix.errRef(name)
    "BAD_REF" -> StrPayFix.errRefBad(name)
    "METHOD_LIMIT" -> StrPayFix.errLimit(name)
    "ID_CONFLICT" -> StrPayFix.errConflict
    "UNREAD" -> StrPayFix.errUnread
    else -> StrPayFix.errOther
  }
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در `Settings` کنارِ تنظیم‌های صندوق */
@Composable
fun PayFixSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(PayFixPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.SwapHoriz,
    title = StrPayFix.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrPayFix.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; PayFixPref.set(context, on) },
    )
  }
}
