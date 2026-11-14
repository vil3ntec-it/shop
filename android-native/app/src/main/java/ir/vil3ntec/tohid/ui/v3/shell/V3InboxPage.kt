package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrV3Inbox
import ir.vil3ntec.tohid.core.sync.V3Inbox
import ir.vil3ntec.tohid.core.sync.V3InboxOutbox
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement

/**
 *  SYN-07 / S28 — صفحهٔ «تعارض‌های همگام‌سازی» (`sync-conflicts`؛ گوشیِ نسخهٔ ۳ و کامپیوتر، همان پوسته): باز و بسته‌ها،
 *  هر ردیف با نامِ همان کالا/قرض‌دار از دفترِ همین دستگاه، گونهٔ تعارض و هر دو مقدار؛ زدن ⇒ [V3InboxSheet].
 *  حال و فرستادن در [V3InboxModel]؛ مرزِ سکو `V3InboxAndroid.kt`.
 */
@Composable
internal fun V3InboxPage(model: V3InboxModel, data: ShopData, onBack: (() -> Unit)?, refreshMs: Long = 30_000L) {
  val c = PhoneV3.colors
  var tab by remember { mutableStateOf(0) }
  var picked by remember { mutableStateOf<V3Inbox.Conflict?>(null) }
  LaunchedEffect(model) {
    var first = true
    while (true) {
      //  نخستین بار همان دم (حتی در پنجرهٔ «خاموش»)؛ بعد با فاصلهٔ خودِ مدل
      runCatching { model.refresh(force = first) }
      first = false
      delay(refreshMs)
    }
  }
  val open by model.open
  val closed by model.closed
  val on by model.on
  val failed by model.failed
  val queued by model.queued
  LaunchedEffect(tab, closed == null) { if (tab == 1 && closed == null) runCatching { model.loadClosed() } }

  V3Scaffold(title = StrV3Inbox.title, onBack = onBack) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(StrV3Inbox.intro, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(4.dp))
        V3Segmented(listOf(StrV3Inbox.tabOpen, StrV3Inbox.tabClosed), tab, { tab = it })
        if (failed && tab == 0) V3Message(V3Tone.WARNING, StrV3Inbox.loadFailed)
        if (queued.isNotEmpty() && tab == 0) V3Message(V3Tone.INFO, StrV3Inbox.queuedCount(queued.size))
        val list = if (tab == 0) open else closed.orEmpty()
        when {
          tab == 0 && (!on || list.isEmpty()) -> V3EmptyState(Icons.Outlined.TaskAlt, StrV3Inbox.empty)
          tab == 1 && closed != null && list.isEmpty() -> V3EmptyState(Icons.Outlined.TaskAlt, StrV3Inbox.emptyClosed)
          else -> list.forEach { cf -> V3InboxCard(cf, model, data) { picked = cf } }
        }
      }
    }
  }
  picked?.let { cf -> V3InboxSheet(cf, model, data, onClose = { picked = null }) }
}

/** یک ردیف: چه چیزی، چرا، دو مقدار (تک‌فیلدی) یا شمارِ فیلدها، زمان، و «در صف»/پیامِ پس از تصمیم یا حالِ بسته */
@Composable
private fun V3InboxCard(cf: V3Inbox.Conflict, model: V3InboxModel, data: ShopData, onOpen: () -> Unit) {
  val c = PhoneV3.colors
  val warn = PhoneV3Roles.tone(c, V3Tone.WARNING).fg
  val queued = model.pending(cf.id)
  val note = model.notes.value[cf.id]
  V3Card(Modifier.clickable(onClick = onOpen), padding = PaddingValues(14.dp)) {
    Text(v3RowTitle(cf, data), style = V3Type.headline, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text(StrV3Inbox.kind(cf.kind), style = V3Type.subhead, color = if (cf.open) warn else c.inkMuted)
    val (cur, other) = V3Inbox.sides(cf, model.me)
    if (cf.field != V3Inbox.WHOLE && cf.field != V3Inbox.DELETE && cf.field.isNotEmpty()) {
      Text(
        StrV3Inbox.field(V3Inbox.base(cf.field)) + ": " + v3Value(cf.field, cur.values[cf.field]) + "  ⇄  " + v3Value(cf.field, other.values[cf.field]),
        style = V3Type.subhead, color = c.ink,
      )
    } else if (other.values.isNotEmpty()) {
      Text(StrV3Inbox.fields(other.values.size), style = V3Type.subhead, color = c.inkMuted)
    }
    Text(SaleTime.stamp(cf.at), style = V3Type.footnote, color = c.inkMuted)
    when {
      queued != null -> Text(StrV3Inbox.queued, style = V3Type.footnote, color = warn)
      note != null -> Text(v3NoteText(note), style = V3Type.footnote, color = c.inkMuted)
      !cf.open -> Text(StrV3Inbox.closedAs(v3Status(cf), cf.resolvedBy.takeLast(8).ifBlank { "—" }), style = V3Type.footnote, color = c.inkMuted)
    }
  }
}

/** «کالا: چای سبز» — نام از دفترِ همین دستگاه؛ نبود ⇒ پایانِ شناسه */
internal fun v3RowTitle(cf: V3Inbox.Conflict, data: ShopData): String {
  val name = when (cf.table) {
    "products" -> data.products.firstOrNull { it.id == cf.rowId }?.name
    "debtors" -> data.debtors.firstOrNull { it.id == cf.rowId }?.name
    "suppliers" -> data.suppliers.firstOrNull { it.id == cf.rowId }?.name
    else -> null
  }?.takeIf { it.isNotBlank() } ?: cf.rowId.takeLast(8).ifBlank { "—" }
  return StrV3Inbox.row(StrV3Inbox.table(cf.table), name)
}

/** یک مقدار به واحدِ دفتر: مبلغ افغانی، مقدار، متن، بله/نه، فهرست */
internal fun v3Value(field: String, v: JsonElement?): String {
  val s = V3Inbox.shown(field, v)
  return when (s.kind) {
    V3Inbox.Kind.MONEY -> ir.vil3ntec.tohid.money((s.number ?: 0L) / 100.0)
    V3Inbox.Kind.QTY -> ir.vil3ntec.tohid.qty((s.number ?: 0L) / 1000.0)
    V3Inbox.Kind.BOOL -> if (s.number == 1L) StrV3Inbox.yes else StrV3Inbox.no
    V3Inbox.Kind.EMPTY -> StrV3Inbox.emptyValue
    V3Inbox.Kind.OTHER -> StrV3Inbox.fields(s.size)
    else -> s.text
  }
}

internal fun v3Status(cf: V3Inbox.Conflict): String = when (cf.status) {
  V3Inbox.KEPT -> StrV3Inbox.statusKept
  V3Inbox.REVERTED -> if (cf.merged.isNotEmpty()) StrV3Inbox.statusMerged else StrV3Inbox.statusReverted
  V3Inbox.DISMISSED -> StrV3Inbox.statusDismissed
  else -> ""
}

internal fun v3NoteText(n: V3InboxModel.Note): String = when (n.fate) {
  V3InboxOutbox.Fate.DONE -> if (n.duplicate) StrV3Inbox.doneAlready else StrV3Inbox.done
  V3InboxOutbox.Fate.CLOSED -> StrV3Inbox.closedElsewhere
  V3InboxOutbox.Fate.REFUSED -> StrV3Inbox.refused(n.code.ifBlank { "?" })
  V3InboxOutbox.Fate.RETRY -> StrV3Inbox.queued
}
