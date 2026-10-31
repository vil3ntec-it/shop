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
import ir.vil3ntec.tohid.core.i18n.StrShiftReview
import ir.vil3ntec.tohid.core.pay.ShiftReview
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShiftReviewFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  PAY-14 — «مدیر بررسی می‌کند»: نشان و دکمهٔ هر بستنِ «نیازمند بررسی» در سابقهٔ صندوقِ شیفت (`ShiftPanelV2`، پشتِ
 *  پرچمِ خاموشِ `ShopPolicy.payV2`). شاگرد این‌جا را نمی‌بیند. بررسی هیچ عددی را عوض نمی‌کند (`core/pay/ShiftReview`).
 */

/** نشانِ وضعیتِ بررسیِ یک بستن، و برای بستنِ در انتظار دکمهٔ «ثبتِ بررسی» */
@Composable
fun ShiftReviewBadge(d: ShopData, close: AuditEntry, onReview: () -> Unit) {
  when (ShiftReviewFlow.state(d, close)) {
    ShiftReview.State.NONE -> Unit
    ShiftReview.State.PENDING -> {
      StatusPill(StrShiftReview.pending, PillTone.WARNING)
      TextButton(onClick = onReview) { Text(StrShiftReview.action) }
    }
    ShiftReview.State.REVIEWED -> ShiftReviewFlow.reviewOf(d, close)?.let { r ->
      StatusPill(StrShiftReview.reviewed(r.by, formatMillis(r.at)), PillTone.SUCCESS)
      Text(r.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
  }
}

/** پنجرهٔ «بررسیِ اختلافِ صندوق»: یادداشتِ اجباری؛ ردیفِ ساخته‌شده به `save` می‌رود. `now` ساعتِ صفحهٔ صندوق است (زمانِ باز شدنِ پنجره) */
@Composable
fun ShiftReviewDialog(
  d: ShopData, close: AuditEntry, role: String, me: String, now: Long,
  save: (AuditEntry) -> Unit, dismiss: () -> Unit,
) {
  var note by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = dismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrShiftReview.title, color = Shop.colors.text) },
    text = {
      val err = error
      Column {
        Text(StrShiftReview.body, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = note, onValueChange = { note = it.take(ShiftReview.NOTE_MAX + 1); error = null },
          label = { Text(StrShiftReview.note) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
          isError = err != null, supportingText = if (err != null) { { Text(err) } } else null,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = {
        when (val r = ShiftReviewFlow.row(d, close, ShiftReviewFlow.roleOf(role), me, note, ir.vil3ntec.tohid.todayIso(), now, ::newId)) {
          is PayFlow.Outcome.Ok -> save(r.value)
          is PayFlow.Outcome.Bad -> error = reviewMessage(r.problem.code)
        }
      }) { Text(StrShiftReview.save) }
    },
    dismissButton = { TextButton(onClick = dismiss) { Text(StrShiftReview.cancel) } },
  )
}

private fun reviewMessage(code: String): String = when (code) {
  "NOTE_REQUIRED" -> StrShiftReview.errNoteRequired
  "NOTE_TOO_LONG" -> StrShiftReview.errNoteTooLong
  "SELF_REVIEW" -> StrShiftReview.errSelf
  "ALREADY_REVIEWED" -> StrShiftReview.errAlready
  "ROLE_DENIED", "BY_REQUIRED" -> StrShiftReview.errRole
  else -> StrShiftReview.errOther
}
