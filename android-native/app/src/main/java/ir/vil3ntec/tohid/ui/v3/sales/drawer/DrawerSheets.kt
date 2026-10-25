package ir.vil3ntec.tohid.ui.v3.sales.drawer

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTotals

/*
 *  برگه‌های صندوق: باز کردن، بستن (ساده: شمرده و اختلافِ زنده؛ شیفت: شمارشِ اسکناس)، ورود/
 *  خروجِ نقد و کشو بی فروش، و گزارشِ X/Z. هر «ثبت» به `onSubmit` می‌رود؛ پاسخِ نادرستِ موتور
 *  همان‌جا زیرِ کادرها (`error`) و برگه باز می‌ماند.
 */

/** `sales/drawer/open` — «پولِ اولِ صندوق» ⇒ «باز کن» */
@Composable
fun DrawerOpenSheet(onSubmit: (Double) -> Unit, onClose: () -> Unit, start: String = "") {
  var amount by remember { mutableStateOf(start) }
  V3Sheet(onDismiss = onClose, title = StrSale.openDrawer, detents = false) {
    V3TextField(amount, { amount = it }, StrSale.openingCashField, kind = V3FieldKind.MONEY, imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = { onSubmit(DrawerModel.amount(amount) ?: Double.NaN) })
    Spacer(Modifier.height(16.dp))
    V3Button(StrSale.openIt, { onSubmit(DrawerModel.amount(amount) ?: Double.NaN) }, Modifier.fillMaxWidth(), haptic = true)
  }
}

/** `sales/drawer/close` (صندوقِ ساده) — ریز، «پولِ شمرده‌شده در دخل» با قرصِ اختلافِ زنده، یادداشت */
@Composable
fun DrawerCloseSheet(totals: CashShift.Totals, onSubmit: (counted: Double, note: String) -> Unit, onClose: () -> Unit, start: String = "") {
  val c = PhoneV3.colors
  var amount by remember { mutableStateOf(start) }
  var note by remember { mutableStateOf("") }
  val diff = DrawerModel.diff(DrawerModel.amount(amount), totals.expected)
  V3Sheet(onDismiss = onClose, title = StrSale.closeDrawer) {
    SaleTotals(DrawerModel.breakdown(totals))
    Spacer(Modifier.height(14.dp))
    V3TextField(amount, { amount = it }, StrSale.countedField, kind = V3FieldKind.MONEY)
    diff?.let { x ->
      Spacer(Modifier.height(8.dp))
      val tone = PhoneV3Roles.tone(c, if (x.kind == DiffKind.NONE) V3Tone.SUCCESS else V3Tone.ERROR)
      val bg by animateColorAsState(tone.bg, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "diffBg")
      val fg by animateColorAsState(tone.fg, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "diffFg")
      Text(
        DrawerModel.diffText(x),
        style = V3Type.callout,
        color = fg,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
          .clip(RoundedCornerShape(50))
          .background(bg)
          .padding(horizontal = 12.dp, vertical = 6.dp),
      )
    }
    Spacer(Modifier.height(10.dp))
    V3TextField(note, { note = it }, StrSale.noteOptional)
    Spacer(Modifier.height(16.dp))
    V3Button(StrSale.closeIt, { onSubmit(DrawerModel.amount(amount) ?: Double.NaN, note) }, Modifier.fillMaxWidth(), haptic = true)
  }
}

/** `sales/drawer/close` (صندوقِ شیفت) — شمارشِ اسکناس (کور برای شاگرد)، جمعِ شمرده، دلیلِ اختلاف */
@Composable
fun DrawerCountSheet(
  blind: Boolean,
  expectedMinor: Long?,
  error: String?,
  onSubmit: (counts: Map<String, Long>, reason: String) -> Unit,
  onClose: () -> Unit,
) {
  val c = PhoneV3.colors
  val texts = remember { mutableStateMapOf<String, String>() }
  var reason by remember { mutableStateOf("") }
  val counts = DrawerModel.counts(texts)
  V3Sheet(onDismiss = onClose, title = StrSale.closeDrawerCount) {
    Text(
      if (blind) StrSale.blindCountClose else StrSale.expectedCash(expectedMinor?.let(DrawerModel::shown) ?: "—"),
      style = V3Type.footnote,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(10.dp))
    DrawerModel.notes().chunked(2).forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        pair.forEach { n ->
          V3TextField(
            texts[n.toString()].orEmpty(),
            { v -> texts[n.toString()] = v.filter { it.isDigit() } },
            "${money(PayFlow.major(n))} ×",
            Modifier.weight(1f),
            kind = V3FieldKind.NUMBER,
          )
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }
    V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(StrSale.countedCash, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
        Text(DrawerModel.shown(PayFlow.countTotal(counts)), style = V3Type.headline, color = c.ink)
      }
    }
    Spacer(Modifier.height(10.dp))
    V3TextField(reason, { reason = it }, StrSale.diffReasonField)
    error?.let { Spacer(Modifier.height(8.dp)); V3Message(V3Tone.ERROR, it) }
    Spacer(Modifier.height(16.dp))
    V3Button(StrSale.closeIt, { onSubmit(counts, reason) }, Modifier.fillMaxWidth(), haptic = true)
  }
}

/** ورود/خروجِ نقد با دلیل (PAY-12) یا کشو بی فروش با تأیید (PAY-19) */
@Composable
fun DrawerMoveSheet(
  noSale: Boolean,
  staff: Boolean,
  me: String,
  error: String?,
  onSubmit: (PayFlow.Move) -> Unit,
  onClose: () -> Unit,
) {
  val c = PhoneV3.colors
  var mode by remember { mutableStateOf("cash_out") }
  var reason by remember(mode) { mutableStateOf(ShiftCore.MOVE_REASONS[mode]?.first()) }
  var amount by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  V3Sheet(onDismiss = onClose, title = if (noSale) StrSale.noSaleTitle else StrSale.cashMove, detents = false) {
    if (!noSale) {
      val modes = listOf("cash_out" to StrSale.cashOutOfDrawer, "cash_in" to StrSale.cashIntoDrawer)
      V3Segmented(modes.map { it.second }, modes.indexOfFirst { it.first == mode }, { mode = modes[it].first })
      Spacer(Modifier.height(12.dp))
      Text(StrSale.reasonWord, style = V3Type.subhead, color = c.inkMuted)
      Spacer(Modifier.height(6.dp))
      V3ChipRow(contentPadding = PaddingValues(0.dp)) {
        ShiftCore.MOVE_REASONS[mode].orEmpty().forEach { r -> V3Chip(DrawerModel.moveLabel(r), r == reason, { reason = r }) }
      }
      Spacer(Modifier.height(12.dp))
      V3TextField(amount, { amount = it }, StrSale.amountAfn, kind = V3FieldKind.MONEY)
      Spacer(Modifier.height(10.dp))
    }
    V3TextField(note, { note = it }, if (noSale) StrSale.noSaleReason else StrSale.moveNote)
    if (noSale) {
      Spacer(Modifier.height(8.dp))
      Text(if (staff) StrSale.noSaleStaff else StrSale.noSaleApprover(me.ifBlank { StrSale.thisAccount }), style = V3Type.footnote, color = c.inkMuted)
    }
    error?.let { Spacer(Modifier.height(8.dp)); V3Message(V3Tone.ERROR, it) }
    Spacer(Modifier.height(16.dp))
    V3Button(
      StrSale.recordWord,
      {
        onSubmit(
          if (noSale) PayFlow.Move("no_sale", reason = note, by = me, approvedBy = if (staff) "" else me.ifBlank { DrawerNotes.OWNER_OR_MANAGER })
          else PayFlow.Move(mode, amount, reason, note, by = me),
        )
      },
      Modifier.fillMaxWidth(),
      enabled = !(noSale && staff),
      haptic = true,
    )
  }
}

/** گزارشِ X (میانِ شیفت) یا Z (پایانِ شیفت)؛ شاگرد نمای کور (`ShiftCore.cashierView`) */
@Composable
fun ShiftReportSheet(rep: ShiftReport, staff: Boolean, title: String?, notes: List<String>, onClose: () -> Unit) {
  val c = PhoneV3.colors
  val shown = if (staff) ShiftCore.cashierView(rep) else rep
  V3Sheet(onDismiss = onClose, title = title ?: if (rep.kind == "Z") StrSale.zReport else StrSale.xReportTitle) {
    notes.forEach { Text(it, style = V3Type.footnote, color = c.inkMuted); Spacer(Modifier.height(4.dp)) }
    if (notes.isNotEmpty()) Spacer(Modifier.height(8.dp))
    V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
      DrawerModel.report(shown).forEach { l ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(l.label, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.weight(1f))
          Spacer(Modifier.padding(start = 8.dp))
          Text(l.value, style = if (l.strong) V3Type.headline else V3Type.callout, color = c.ink)
        }
      }
    }
    if (rep.kind == "Z" && rep.needsReview == true) {
      Spacer(Modifier.height(8.dp))
      V3StatusPill(V3Status.WARNING, StrSale.needsReview)
    }
  }
}

/** ریزِ یک صندوقِ گذشته بی گزارشِ Z (صندوقِ ساده) — یادداشتِ باز و بستن */
@Composable
fun ShiftNotesSheet(shift: CashShift.Shift, onClose: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onClose, title = StrSale.drawerHistory, detents = false) {
    Text(ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime.stamp(shift.open.createdAt), style = V3Type.headline, color = c.ink)
    Text(shift.open.notes, style = V3Type.callout, color = c.inkMuted)
    Spacer(Modifier.height(12.dp))
    Text(shift.close?.let { ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime.stamp(it.createdAt) } ?: StrSale.stillOpen, style = V3Type.headline, color = c.ink)
    shift.close?.let { Text(it.notes, style = V3Type.callout, color = c.inkMuted) }
  }
}
