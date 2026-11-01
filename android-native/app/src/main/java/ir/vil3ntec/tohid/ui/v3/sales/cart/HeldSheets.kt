package ir.vil3ntec.tohid.ui.v3.sales.cart

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.components.pos.HeldDialog
import ir.vil3ntec.tohid.ui.components.pos.HeldInvoicesUi
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  نگه‌داشتنِ فاکتور و چند فاکتورِ هم‌زمان (POS-12، #332) روی کیت — همان `HeldInvoicesUi`
 *  (حال، قفلِ سبد وسطِ جابه‌جایی، پیام‌ها) و همان `HeldInvoiceStore`؛ این‌جا فقط نوار و برگه‌ها.
 *  پشتِ تنظیمِ «چند فاکتورِ هم‌زمان» (پیش‌فرض خاموش): خاموش و بی نگه‌داشته ⇒ هیچ.
 */

/** نوارِ فاکتورهای باز در سبدِ باز: جاری (برجسته)، نگه‌داشته‌ها، «+ فاکتورِ تازه» و «همه» */
@Composable
fun HeldStrip(ui: HeldInvoicesUi, d: ShopData, modifier: Modifier = Modifier) {
  val book by ui.store.book.collectAsState()
  val lines by ui.cart.lines.collectAsState()
  if (!ui.visible) return
  val active = ui.cart.snapshot()
  val activeNo = HeldRules.activeNumber(book, active)
  val tag = HeldRules.activeTag(book, active)
  val stale = HeldRules.stale(book, System.currentTimeMillis()).mapTo(HashSet()) { it.id }
  V3ChipRow(modifier) {
    V3Chip(
      listOfNotNull(activeNo?.let { "#${plain(it)}" }, tag?.label?.ifBlank { null } ?: if (lines.isEmpty()) StrPos.newInvoice else StrPos.currentInvoice).joinToString(" · "),
      selected = true,
      onClick = {},
    )
    book.items.forEach { h ->
      V3Chip(
        "#${plain(h.number)} · " + title(d, h) + if (h.id in stale) " · " + StrPos.stale else "",
        selected = false,
        onClick = { ui.resume(h.id) },
      )
    }
    if (lines.isNotEmpty()) V3AddChip(StrPos.newInvoice) { ui.newInvoice() }
    if (book.items.isNotEmpty()) V3Chip(Str.all, false, { ui.dialog = HeldDialog.List }, icon = Icons.Outlined.Layers)
  }
}

/** برگه‌های نگه‌داشتن، فهرست، دور انداختن و پاک کردنِ کهنه‌ها — هر کدام از `ui.dialog` */
@Composable
fun HeldSheets(ui: HeldInvoicesUi, d: ShopData) {
  val c = PhoneV3.colors
  val book by ui.store.book.collectAsState()
  when (val dlg = ui.dialog) {
    null -> Unit
    is HeldDialog.Hold -> {
      var label by remember(dlg) { mutableStateOf(dlg.label) }
      var note by remember(dlg) { mutableStateOf(dlg.note) }
      val chosen = remember(dlg) { booleanArrayOf(false) }
      V3Sheet(onDismiss = { if (!chosen[0]) ui.close() }, title = StrPos.holdInvoice, detents = false) { closer ->
        Text(StrPos.holdNote, style = V3Type.callout, color = c.inkMuted)
        Spacer(Modifier.height(12.dp))
        V3TextField(label, { label = it.take(HeldRules.LABEL_MAX) }, StrPos.nameOrNumberOptional)
        Spacer(Modifier.height(10.dp))
        V3TextField(note, { note = it.take(HeldRules.NOTE_MAX) }, StrPos.noteOptional, singleLine = false)
        Spacer(Modifier.height(16.dp))
        V3Button(StrPos.holdIt, { chosen[0] = true; closer.close { ui.hold(label, note) } }, Modifier.fillMaxWidth(), haptic = true)
      }
    }
    HeldDialog.List -> {
      val now = System.currentTimeMillis()
      val stale = HeldRules.stale(HeldBook(items = book.items), now)
      V3Sheet(onDismiss = { ui.close() }, title = StrPos.heldInvoices) {
        if (book.items.isEmpty()) Text(StrPos.noHeldInvoices, style = V3Type.callout, color = c.inkMuted)
        book.items.forEachIndexed { i, h ->
          if (i > 0) V3Divider(start = 64.dp)
          V3ListRow(
            title = "#${plain(h.number)} · " + title(d, h),
            subtitle = listOfNotNull(summary(d, h.draft()) + " · " + ago(now, h.heldAt), h.note.ifBlank { null }).joinToString("\n"),
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            hue = if (h in stale) V3Hue.AMBER else V3Hue.BLUE,
            trailing = if (h in stale) ({ V3StatusPill(V3Status.WARNING, StrPos.stale) }) else null,
            endActions = listOf(V3SwipeAction(Icons.Outlined.DeleteOutline, StrPos.discardIt, V3ButtonKind.DANGER) { ui.dialog = HeldDialog.Discard(h) }),
            onLongClick = { ui.dialog = HeldDialog.Discard(h) },
            onClick = { ui.resume(h.id) },
          )
        }
        if (ui.canDropStale && stale.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          V3TextButton(StrPos.clearStale(plain(stale.size)), { ui.dialog = HeldDialog.DropStale }, danger = true)
        }
      }
    }
    is HeldDialog.Discard -> V3ConfirmSheet(
      title = StrPos.discardQ(plain(dlg.invoice.number)),
      message = StrPos.discardWarn(dlg.invoice.label.ifBlank { StrPos.unnamed }, summary(d, dlg.invoice.draft())),
      confirm = StrPos.discardIt,
      onConfirm = { ui.discard(dlg.invoice) },
      onDismiss = { if (ui.dialog is HeldDialog.Discard) ui.dialog = HeldDialog.List },
      icon = Icons.Outlined.DeleteOutline,
    )
    HeldDialog.DropStale -> V3ConfirmSheet(
      title = StrPos.clearStaleQ,
      message = StrPos.clearStaleWarn(plain(HeldRules.STALE_HOURS)),
      confirm = StrPos.clearIt,
      onConfirm = { ui.dropStale() },
      onDismiss = { if (ui.dialog == HeldDialog.DropStale) ui.dialog = HeldDialog.List },
      icon = Icons.Outlined.DeleteOutline,
    )
  }
}

private fun title(d: ShopData, h: HeldInvoice): String =
  h.label.ifBlank { null } ?: h.debtorId?.let { id -> d.debtors.firstOrNull { it.id == id }?.name?.ifBlank { null } } ?: StrPos.invoice

private fun summary(d: ShopData, draft: SaleDraft): String {
  val cart = draft.cart()
  val total = cart.sumOf { SalesEngine.lineTotal(SalesEngine.linePrice(d, it), it.quantity) }
  return StrPos.linesAndTotal(plain(cart.size), money(total))
}

private fun ago(now: Long, at: Long): String {
  val min = ((now - at).coerceAtLeast(0) / 60_000L).toInt()
  return when {
    min < 1 -> StrNav.justNow
    min < 60 -> StrNav.minutesAgo(plain(min))
    min < 48 * 60 -> StrNav.hoursAgo(plain(min / 60))
    else -> StrNav.daysAgo(plain(min / (24 * 60)))
  }
}
