package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.audit.DocChainAudit
import ir.vil3ntec.tohid.core.i18n.StrDocChain
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3Type

/** حالِ سنجش: هنوز می‌سنجد، زنجیره نیست/خاموش، یا گزارش */
sealed interface DocChainCheckState {
  data object Loading : DocChainCheckState
  data object Off : DocChainCheckState
  data class Done(val report: DocChainAudit.Report) : DocChainCheckState
}

/**
 *  SEC-02 — «بررسیِ یکپارچگیِ اسناد» (`audit/doc-chain`، گوشیِ نسخهٔ ۳ و کامپیوتر): زنجیرهٔ هشِ همین دستگاه از دیسک، سنجیده
 *  با دفترِ امروز (`DocChainAudit`، از راهِ `rememberDocChainCheck`). فقط می‌خواند. «دوباره بسنج» سنجش را از نو می‌زند.
 */
@Composable
fun DocChainCheckScreen(state: DocChainCheckState, time: (Long) -> String, onRecheck: () -> Unit, onBack: (() -> Unit)?, bottomReserve: Dp = 0.dp) {
  val c = PhoneV3.colors
  V3Scaffold(title = StrDocChain.title, onBack = onBack, bottomReserve = bottomReserve) { pad ->
    when (state) {
      DocChainCheckState.Loading -> Box(Modifier.fillMaxSize().padding(16.dp)) { V3Skeleton(rows = 4) }
      DocChainCheckState.Off -> Box(Modifier.fillMaxSize()) { V3EmptyState(Icons.Outlined.LinkOff, StrDocChain.off) }
      is DocChainCheckState.Done -> {
        val v = DocChainCheckModel.view(state.report, time)
        LazyColumn(
          Modifier.fillMaxSize(),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
        ) {
          item(key = "verdict") {
            Box(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
              V3Message(if (v.ok) V3Tone.SUCCESS else V3Tone.ERROR, v.title, body = v.body, action = StrDocChain.recheck, onAction = onRecheck)
            }
          }
          if (v.notes.isNotEmpty()) item(key = "notes") {
            Box(Modifier.padding(vertical = 4.dp)) { V3Message(V3Tone.INFO, v.notes.first(), body = v.notes.drop(1).joinToString("\n").ifBlank { null }) }
          }
          for ((key, title, rows) in listOf(Triple("changed", StrDocChain.changedTitle, v.changed), Triple("missing", StrDocChain.missingTitle, v.missing))) {
            if (rows.isNotEmpty()) item(key = key) {
              V3Section(title) {
                rows.forEachIndexed { i, r ->
                  if (i > 0) V3Divider(start = 64.dp)
                  V3ListRow(r.label, subtitle = r.ref, icon = Icons.Outlined.LinkOff, danger = true)
                }
              }
            }
          }
          item(key = "facts") {
            V3Section(StrDocChain.title) {
              v.facts.forEachIndexed { i, f ->
                if (i > 0) V3Divider(start = 16.dp)
                Text(f, style = V3Type.callout, color = c.ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
              }
            }
          }
        }
      }
    }
  }
}

/** درِ صفحه در سرِ «سابقهٔ عملیات» — فقط وقتی زنجیرهٔ اسناد روشن است */
@Composable
internal fun DocChainCheckEntry(onOpen: () -> Unit) {
  Box(Modifier.padding(top = 8.dp)) {
    V3Section(null) { V3ListRow(StrDocChain.title, subtitle = StrDocChain.entryBody, icon = Icons.Outlined.Link, onClick = onOpen) }
  }
}
