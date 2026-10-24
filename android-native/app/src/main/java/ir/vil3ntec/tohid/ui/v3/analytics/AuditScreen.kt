package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 *  «سابقهٔ عملیات» (`audit?kind=`): «همه» + پنج نوعِ پرتکرار و «نوعِ رویداد» ⇐ برگهٔ همهٔ نوع‌ها؛
 *  ردیف‌ها روز به روز، پاک‌کردن‌ها با قرصِ قرمزِ «حذف». زدن ⇐ همان مورد اگر هنوز هست، وگرنه
 *  جزئیاتِ فقط‌خواندنی. دفتر فقط خوانده می‌شود (`d.auditLog`).
 */
@Composable
fun AuditScreen(
  d: ShopData,
  kindArg: String?,
  onBack: (() -> Unit)?,
  onOpen: (String) -> Unit,
  bottomReserve: Dp = 0.dp,
  today: String = todayIso(),
) {
  var kind by rememberSaveable(kindArg) { mutableStateOf(kindArg?.takeIf { it.isNotBlank() }) }
  var sheet by remember { mutableStateOf(false) }
  var detail by remember { mutableStateOf<AuditEntry?>(null) }
  val log = d.auditLog
  val counts = remember(log) { AuditModel.counts(log) }
  val quick = remember(log) { AuditModel.quickKinds(log) }
  val rows = remember(log, kind) { AuditModel.byDay(AuditModel.filter(log, kind)) }
  val stagger = rememberV3Stagger()
  val chips: (@Composable () -> Unit)? = if (log.isEmpty()) null else ({
    V3ChipRow {
      V3Chip(Str.all, kind == null, { kind = null })
      (quick + listOfNotNull(kind?.takeIf { it !in quick })).forEach { k ->
        V3Chip(StrReports.kind(k), kind == k, { kind = if (kind == k) null else k }, count = counts[k] ?: 0)
      }
      V3Chip(StrReports.eventKind, false, { sheet = true }, icon = Icons.Outlined.Tune)
    }
  })

  V3Scaffold(
    title = StrReports.auditTitle,
    subtitle = StrReports.auditCount(log.size),
    onBack = onBack,
    bottomReserve = bottomReserve,
    chips = chips,
  ) { pad ->
    when {
      log.isEmpty() -> Column(Modifier.fillMaxSize()) {
        V3EmptyState(Icons.Outlined.History, StrReports.auditEmpty, body = StrReports.auditEmptyBody, action = onBack?.let { StrKit.back }, onAction = onBack)
      }
      rows.isEmpty() -> Column(Modifier.fillMaxSize()) {
        V3EmptyState(Icons.Outlined.History, StrReports.auditFilteredEmpty, action = StrReports.showAll, onAction = { kind = null })
      }
      else -> LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
      ) {
        itemsIndexed(rows, key = { _, g -> g.first }) { i, (date, entries) ->
          V3Section(dayTitle(date, today), Modifier.animateItem().v3Stagger(stagger, "$kind-$date", i)) {
            entries.forEachIndexed { i, e ->
              if (i > 0) V3Divider(start = 64.dp)
              AuditRow(e) { AuditModel.target(e, d)?.let(onOpen) ?: run { detail = e } }
            }
          }
        }
      }
    }
  }

  if (sheet) KindSheet(AuditModel.sheetKinds(log), kind, onDismiss = { sheet = false }) { kind = it }
  detail?.let { e -> DetailSheet(e, gone = !e.refId.isNullOrBlank(), onDismiss = { detail = null }) }
}

@Composable
private fun AuditRow(e: AuditEntry, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val deletion = AuditModel.isDeletion(e.type)
  V3ListRow(
    StrReports.kind(e.type),
    subtitle = e.notes.ifBlank { null },
    icon = kindIcon(e.type), hue = kindHue(e.type), danger = deletion,
    trailing = {
      Column(horizontalAlignment = Alignment.End) {
        timeOf(e.createdAt)?.let { Text(it, style = V3Type.footnote, color = c.inkMuted) }
        if (e.amount != 0.0) Text(afn(e.amount), style = V3Type.subhead, color = c.ink)
        if (deletion) { Spacer(Modifier.height(4.dp)); V3StatusPill(V3Status.CANCELLED, StrReports.deletion) }
      }
    },
    onClick = onClick,
  )
}

/** برگهٔ «نوعِ رویداد»: «همه» و هر نوع با شمارش؛ تیکِ برند کنارِ انتخابِ فعلی */
@Composable
private fun KindSheet(kinds: List<Pair<String, Int>>, selected: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrReports.eventKind) { closer ->
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
        items(listOf<Pair<String?, Int>>(null to kinds.sumOf { it.second }) + kinds, key = { it.first ?: "" }) { (k, n) ->
          if (k != null) V3Divider(start = 64.dp)
          V3ListRow(
            k?.let { StrReports.kind(it) } ?: Str.all,
            icon = k?.let { kindIcon(it) } ?: Icons.Outlined.History, hue = k?.let { kindHue(it) },
            value = n.toString(), minHeight = 50.dp,
            trailing = if (k == selected) ({ Icon(Icons.Outlined.Check, contentDescription = StrKit.selected, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
            onClick = { closer.close { onPick(k) } },
          )
        }
      }
    }
  }
}

/** جزئیاتِ فقط‌خواندنیِ رویدادی که موردش دیگر نیست */
@Composable
private fun DetailSheet(e: AuditEntry, gone: Boolean, onDismiss: () -> Unit) {
  V3Sheet(onDismiss = onDismiss, title = StrReports.eventDetail) {
    V3Card(padding = PaddingValues(vertical = 4.dp)) {
      V3ListRow(StrReports.kind(e.type), icon = kindIcon(e.type), hue = kindHue(e.type), danger = AuditModel.isDeletion(e.type), chevron = false)
      V3Divider(start = 16.dp)
      V3ListRow(StrReports.when_, tile = false, chevron = false, value = uiDateLong(e.date) + (timeOf(e.createdAt)?.let { " · $it" } ?: ""))
      if (e.amount != 0.0) { V3Divider(start = 16.dp); V3ListRow(StrReports.amount, tile = false, chevron = false, value = afn(e.amount)) }
      if (e.notes.isNotBlank()) { V3Divider(start = 16.dp); V3ListRow(StrReports.note, subtitle = e.notes, subtitleLines = 6, tile = false, chevron = false) }
    }
    if (gone) Box(Modifier.padding(top = 12.dp)) { V3Message(V3Tone.INFO, StrReports.recordGone) }
    Spacer(Modifier.height(8.dp))
  }
}

private fun dayTitle(date: String, today: String): String = when (date) {
  today -> Str.today
  CivilDays.addDays(today, -1) -> StrKit.yesterday
  else -> uiDateLong(date)
}

private fun timeOf(createdAt: Long): String? =
  if (createdAt <= 0) null else runCatching { SimpleDateFormat("HH:mm", Locale.US).format(Date(createdAt)) }.getOrNull()

private fun kindIcon(type: String): ImageVector = when (type) {
  "sale", "shift_open", "shift_close" -> Icons.Outlined.PointOfSale
  "cancel_sale", "return" -> Icons.Outlined.Restore
  "purchase", "supplier_return" -> Icons.Outlined.LocalShipping
  "customer_payment", "supplier_payment" -> Icons.Outlined.Payments
  "stock_adjustment" -> Icons.Outlined.Tune
  "price_change" -> Icons.Outlined.Sell
  "product_edit" -> Icons.Outlined.Edit
  "data_export" -> Icons.Outlined.CloudDownload
  "debt_due_set" -> Icons.Outlined.Schedule
  "credit_over_limit", "credit_limit_set" -> Icons.Outlined.AccountBalanceWallet
  else -> if (AuditModel.isDeletion(type)) Icons.Outlined.DeleteOutline else Icons.Outlined.History
}

private fun kindHue(type: String): V3Hue? = when (type) {
  "sale", "customer_payment", "shift_open", "shift_close" -> V3Hue.BRAND
  "purchase", "supplier_payment", "supplier_return" -> V3Hue.PURPLE
  "stock_adjustment" -> V3Hue.TEAL
  "price_change", "product_edit", "data_export" -> V3Hue.BLUE
  "cancel_sale", "return", "credit_over_limit", "credit_limit_set", "debt_due_set" -> V3Hue.ORANGE
  else -> null
}
