package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrConflictInbox
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.data.ConflictFix
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.data.repo.ConflictInboxRepository
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.launch

/**
 *  فاکتورِ کاملِ یک سندِ ردشده و سه کار — «دوباره ثبت با تأییدِ من» (با پرسشِ دوباره)، «اصلاح و ثبت» (سبدِ صندوق با همان
 *  قلم‌ها و قرض‌دار؛ پس از ثبت، `ConflictFix` ردیف را می‌بندد) و «کنار بگذار» (دلیلِ اجباری). دکمه تا پاسخِ سرور قفل
 *  است (دو لمس دو درخواست نمی‌شود؛ تکرار هم در سرور همان پاسخ است).
 */
@Composable
internal fun RouteScope.ConflictDetailSheet(item: ConflictInbox.Item, onClose: () -> Unit, onChanged: (ConflictInbox.Item) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val c = PhoneV3.colors
  val red = PhoneV3Roles.tone(c, V3Tone.ERROR).fg
  var full by remember(item.id) { mutableStateOf<ConflictInbox.Item?>(null) }
  var busy by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var ask by remember { mutableStateOf(false) }
  var dismissing by remember { mutableStateOf(false) }
  LaunchedEffect(item.id) {
    when (val r = ConflictInboxLive.repo(context)?.get(item.id)) {
      is ApiResult.Success -> full = r.value
      is ApiResult.Failure -> error = r.error.userMessage
      null -> Unit
    }
  }
  val shown = full ?: item
  val acts = ConflictInbox.actions(shown)

  fun done(r: ApiResult<ConflictInboxRepository.Outcome>, ok: String) {
    busy = null
    when (r) {
      is ApiResult.Success -> {
        onChanged(r.value.item)
        full = r.value.item.copy(invoice = full?.invoice)
        toasts.show(if (r.value.duplicate && r.value.item.status == ConflictInbox.RESUBMITTED) StrConflictInbox.doneAlready else ok, V3Tone.SUCCESS)
        if (!r.value.item.open) onClose()
      }
      is ApiResult.Failure -> {
        val again = ConflictInboxRepository.rejectReason(r.error)
        error = when {
          again != null -> StrConflictInbox.rejectedAgain(StrConflictInbox.reason(again))
          r.error.code == "resubmit_in_progress" -> StrConflictInbox.inProgress
          else -> r.error.userMessage
        }
        scope.launch { runCatching { ConflictInboxLive.refresh(context, force = true) } }
      }
    }
  }

  V3Sheet(onDismiss = onClose, title = conflictDoc(shown)) { _ ->
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(StrConflictInbox.why(StrConflictInbox.reason(shown.reason)), style = V3Type.body, color = if (shown.open) red else c.inkMuted)
      Text(
        listOf(StrConflictInbox.staff(shown.staff.name.ifBlank { "—" }), StrConflictInbox.device(shown.deviceName.ifBlank { shown.deviceId.takeLast(8) }))
          .joinToString(" · "),
        style = V3Type.subhead, color = c.inkMuted,
      )
      Text(SaleTime.stamp(shown.at), style = V3Type.footnote, color = c.inkMuted)
      error?.let { V3Message(V3Tone.ERROR, it) }
      shown.lastError?.takeIf { shown.open && error == null }?.let { Text(StrConflictInbox.lastTry(StrConflictInbox.reason(it)), style = V3Type.footnote, color = red) }
      val inv = full?.invoice
      if (inv == null && error == null) V3Skeleton(rows = 3)
      inv?.let { InvoiceLines(it) }
      if (inv?.truncated == true || (shown.open && !shown.resubmittable && shown.reason !in ConflictInbox.NO_APPROVAL)) {
        Text(StrConflictInbox.partial, style = V3Type.footnote, color = c.inkMuted)
      }
      if (!shown.open) Text(
        StrConflictInbox.closedBy(conflictStatus(shown), shown.resolvedBy?.name?.ifBlank { null } ?: "—") +
          (if (shown.note.isNotBlank()) " · " + shown.note else ""),
        style = V3Type.subhead, color = c.inkMuted,
      )
      if (shown.busy) Text(StrConflictInbox.busy, style = V3Type.footnote, color = c.inkMuted)
      Spacer(Modifier.height(8.dp))
      if ("resubmit" in acts) V3Button(
        StrConflictInbox.resubmit, { ask = true }, Modifier.fillMaxWidth(),
        icon = Icons.Outlined.CloudUpload, enabled = busy == null, loading = busy == "resubmit",
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if ("fix" in acts && inv != null) V3Button(
          StrConflictInbox.fix, { fixAtTill(context, shown, inv, onClose) }, Modifier.weight(1f),
          kind = V3ButtonKind.SECONDARY, enabled = busy == null,
        )
        if ("dismiss" in acts) V3Button(
          StrConflictInbox.dismiss, { dismissing = true }, Modifier.weight(1f),
          kind = V3ButtonKind.NEUTRAL, enabled = busy == null, loading = busy == "dismiss",
        )
      }
    }
  }

  if (ask) V3ConfirmSheet(
    title = StrConflictInbox.resubmit,
    message = StrConflictInbox.confirmResubmit(conflictDoc(shown)),
    confirm = StrConflictInbox.resubmit,
    icon = Icons.Outlined.CloudUpload,
    danger = false,
    onDismiss = { ask = false },
    onConfirm = {
      ask = false
      val repo = ConflictInboxLive.repo(context) ?: return@V3ConfirmSheet
      busy = "resubmit"; error = null
      scope.launch { done(repo.resubmit(shown.id), StrConflictInbox.doneResubmitted) }
    },
  )

  if (dismissing) DismissSheet(
    onDismiss = { dismissing = false },
    onConfirm = { reason ->
      dismissing = false
      val repo = ConflictInboxLive.repo(context) ?: return@DismissSheet
      busy = "dismiss"; error = null
      scope.launch { done(repo.dismiss(shown.id, reason), StrConflictInbox.doneDismissed) }
    },
  )
}

/** قلم‌ها × مقدار — جمع، و جمع‌ها و قرض‌دارِ فاکتور */
@Composable
private fun InvoiceLines(inv: ConflictInbox.Invoice) {
  val c = PhoneV3.colors
  val lines = inv.items.ifEmpty { inv.returns }
  if (lines.isNotEmpty()) {
    Text(StrConflictInbox.lines, style = V3Type.callout, color = c.inkMuted)
    lines.forEach { l ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(listOf(l.name.ifBlank { "—" }, conflictQty(l.qtyMilli)).joinToString(" × "), style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
        Text(conflictMoney(l.totalMinor), style = V3Type.body, color = c.ink)
      }
    }
    V3Divider()
  }
  listOf(
    StrConflictInbox.total to inv.totalMinor, StrConflictInbox.discount to inv.discountMinor, StrConflictInbox.payable to inv.finalMinor,
    StrConflictInbox.paid to inv.paidMinor, StrConflictInbox.debt to inv.debtMinor,
  ).filter { (label, v) -> v != null && (v != 0L || label == StrConflictInbox.payable) }.forEach { (label, v) ->
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = V3Type.subhead, color = c.inkMuted)
      Text(conflictMoney(v), style = V3Type.subhead, color = c.ink)
    }
  }
  if (inv.debtorName.isNotBlank()) Text(StrConflictInbox.debtor(inv.debtorName), style = V3Type.subhead, color = c.ink)
}

/** «کنار بگذار»: دلیلِ اجباری (یک خط) */
@Composable
private fun DismissSheet(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  val c = PhoneV3.colors
  var text by remember { mutableStateOf("") }
  var tried by remember { mutableStateOf(false) }
  V3Sheet(onDismiss = onDismiss, title = StrConflictInbox.dismissTitle, detents = false) { _ ->
    Text(StrConflictInbox.dismissBody, style = V3Type.subhead, color = c.inkMuted)
    Spacer(Modifier.height(8.dp))
    val ok = ConflictInbox.dismissBody(text) != null
    V3TextField(
      text, { text = it.take(ConflictInbox.MAX_NOTE) }, StrConflictInbox.reasonLabel, Modifier.fillMaxWidth(),
      required = true, error = if (tried && !ok) StrConflictInbox.reasonRequired else null,
    )
    Spacer(Modifier.height(12.dp))
    V3Button(StrConflictInbox.dismiss, { tried = true; if (ok) onConfirm(text) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER)
  }
}

/**
 *  «اصلاح و ثبت»: سبدِ خالیِ صندوق ⇐ همان قلم‌ها با همان قیمت و همان قرض‌دار (اگر روی همین دفتر هست)؛ کالایی که این دفتر
 *  ندارد قلمِ آزاد با همان نام. سبدِ پُر دست نمی‌خورد (فاکتورِ نیمه‌کارهٔ صندوقدار گم نمی‌شود).
 */
private fun RouteScope.fixAtTill(context: android.content.Context, item: ConflictInbox.Item, inv: ConflictInbox.Invoice, onClose: () -> Unit) {
  if (cart.lines.value.isNotEmpty()) { toasts.show(StrConflictInbox.cartBusy, V3Tone.WARNING); return }
  val known = data.products.mapTo(HashSet()) { it.id }
  val lines = ConflictInbox.cartLines(inv).map { l ->
    if (l.productId in known) SalesEngine.CartLine(productId = l.productId, quantity = l.quantity, unitPrice = l.unitPrice)
    else SalesEngine.CartLine(productId = Ulid.next(), quantity = l.quantity, unitPrice = l.unitPrice, label = l.name.ifBlank { StrConflictInbox.lines })
  }
  if (lines.isEmpty()) { toasts.show(StrConflictInbox.fixNoLines, V3Tone.WARNING); return }
  if (!cart.set(lines)) { toasts.show(StrConflictInbox.cartBusy, V3Tone.WARNING); return }
  cart.setDebtor(inv.debtorId.takeIf { id -> id.isNotBlank() && data.debtors.any { it.id == id } })
  //  تخفیف و دریافتیِ نسیهٔ همان فاکتور به برگهٔ تسویه (`SaleReopen.extras`) — پیش‌تر می‌افتادند
  ir.vil3ntec.tohid.ui.v3.sales.cart.HeldSession.keep(cart, ir.vil3ntec.tohid.data.SaleReopen.extras(inv))
  ConflictFix.begin(context, cart.saleKey(), item.id)
  toasts.show(StrConflictInbox.fixOpened, V3Tone.INFO)
  onClose()
  nav.switchTab(SalesPaths.SALE)
}
