package ir.vil3ntec.tohid.ui.v3.online.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField

/** برگهٔ گزارش به مدیرِ سامانه (یک پیام یا کلِ گفت‌وگو): هرزنامه، توهین یا آزار، کلاه‌برداری، دیگر + توضیح */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatReportSheet(forMessage: Boolean, busy: Boolean, onSend: (reason: String, note: String) -> Unit, onDismiss: () -> Unit) {
  var reason by remember { mutableStateOf(ChatModel.REPORT_REASONS.first()) }
  var note by remember { mutableStateOf("") }
  V3Sheet(onDismiss = onDismiss, title = if (forMessage) StrOnline.ctReportTitleMsg else StrOnline.ctReportTitleConv, detents = false) { _ ->
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      ChatModel.REPORT_REASONS.forEach { r -> V3Chip(StrOnline.ctReason(r), reason == r, { reason = r }) }
    }
    Spacer(Modifier.height(14.dp))
    V3TextField(note, { if (it.length <= NOTE_MAX) note = it }, StrOnline.ctReportNote, singleLine = false, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    V3Button(StrOnline.ctSendReport, { onSend(reason, note) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, loading = busy, haptic = true)
  }
}

private const val NOTE_MAX = 500
