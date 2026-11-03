package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.image.ImageChanges
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.repo.ImageChangesRepository
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.screens.onlineReportApi
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3SearchField
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/*
 *  IMG-PRO-17 — «تغییرِ دسته‌ایِ عکس‌ها» در برنامهٔ مدیریت (پیش‌تر فقط API، #882):
 *    `stock/images/changes`        فهرستِ دسته‌ها و «دستهٔ تازه»
 *    `stock/images/changes/new`    کار (نسخهٔ تازهٔ مرکزی / برگشت به مرکزی) + کالاها ⇐ بررسی ⇐ اعمال (یا همه یا هیچ)
 *    `stock/images/changes/{id}`   پیش و پسِ هر کالا و «برگرداندنِ همه» (۴۰۹ ⇒ نامِ کالاهای عوض‌شده؛ هیچ چیز برنمی‌گردد)
 *  ⛔ پرچمِ خاموشِ `shop.image_bank` ⇒ راه در «…» ِ انبار پنهان و صفحه «روشن نیست».
 *  ⛔ تطبیقِ نامطمئن فقط با تأییدِ همان یک کالا؛ هیچ تأییدِ یک‌جا.
 */

private fun sourceLabel(p: ImageChanges.Pick?): String = when {
  p == null -> StrImageChanges.previousDefault
  p.source == "central" -> StrImageChanges.sourceCentral + " · " + StrImageChanges.version(p.version)
  p.source == "shop" -> StrImageChanges.sourceShop + " · " + StrImageChanges.version(p.version)
  else -> StrImageChanges.sourceNone
}

private fun statusLabel(c: ImageChanges.Change): String = if (c.rolledBack) StrImageChanges.rolledBack else StrImageChanges.applied

private fun skipLabel(s: ImageChanges.Skip): String = when (s) {
  ImageChanges.Skip.NO_CENTRAL -> StrImageChanges.skipNoCentral
  ImageChanges.Skip.ALREADY -> StrImageChanges.skipAlready
  ImageChanges.Skip.ARCHIVED -> StrImageChanges.skipArchived
  ImageChanges.Skip.UNKNOWN -> StrImageChanges.skipUnknown
}

private fun RouteScope.nameOf(id: String): String = data.products.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: id

/** `stock/images/changes` */
@Composable
internal fun RouteScope.ImageChangesRoute() {
  val api = onlineReportApi()
  val state = remember(api) { api?.let { ImageChangesListState(ImageChangesRepository(it)) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(StrImageChanges.storefrontNote, style = V3Type.footnote, color = c.inkMuted)
    if (state != null && !state.off) {
      V3Button(StrImageChanges.newBatch, { nav.open(StockPaths.IMAGE_CHANGES_NEW) }, Modifier.padding(top = 12.dp), icon = Icons.Outlined.Add)
    }
    val list = state?.changes
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.Collections, StrImageChanges.off)
      list == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrImageChanges.failed, action = StrImageChanges.retry, onAction = { tick++ })
      list == null -> Text(StrImageChanges.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      list.isEmpty() -> V3EmptyState(Icons.Outlined.History, StrImageChanges.none)
      else -> V3Section(null) {
        list.forEachIndexed { i, ch ->
          if (i > 0) V3Divider()
          V3ListRow(
            ch.note.ifBlank { StrImageChanges.batch },
            subtitle = StrImageChanges.line(statusLabel(ch), StrImageChanges.items(ch.itemCount), displayMillis(ch.createdAt)),
            icon = if (ch.rolledBack) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Collections,
            onClick = { nav.open(StockPaths.imageChange(ch.id)) },
          )
        }
      }
    }
  }
}

/** `stock/images/changes/{id}` */
@Composable
internal fun RouteScope.ImageChangeRoute(id: String) {
  val api = onlineReportApi()
  val state = remember(api, id) { api?.let { ImageChangeDetailState(ImageChangesRepository(it), id) } }
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  var reason by remember { mutableStateOf("") }
  var asking by remember { mutableStateOf(false) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  state?.outcome?.let { o ->
    LaunchedEffect(o) {
      when (o) {
        ImageChangeDetailState.Outcome.Done -> toasts.show(StrImageChanges.rollbackDone, V3Tone.SUCCESS)
        ImageChangeDetailState.Outcome.Already -> toasts.show(StrImageChanges.alreadyRolledBack, V3Tone.INFO)
        is ImageChangeDetailState.Outcome.Failed -> toasts.show(o.message, V3Tone.ERROR)
        is ImageChangeDetailState.Outcome.ChangedSince -> Unit
      }
    }
  }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    val ch = state?.change
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.Collections, StrImageChanges.off)
      ch == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrImageChanges.failed, action = StrImageChanges.retry, onAction = { tick++ })
      ch == null -> Text(StrImageChanges.loading, style = V3Type.callout, color = c.inkMuted)
      else -> {
        Text(ch.note.ifBlank { StrImageChanges.batch }, style = V3Type.title, color = c.ink)
        Text(
          StrImageChanges.line(statusLabel(ch), StrImageChanges.items(ch.itemCount), displayMillis(ch.createdAt)),
          style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp),
        )
        if (ch.rolledBack) {
          Text(
            StrImageChanges.rolledBackLine(displayMillis(ch.rolledBackAt ?: 0L), ch.rollbackReason),
            style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp),
          )
        }
        V3Section(StrImageChanges.pick) {
          ch.items.forEachIndexed { i, it ->
            if (i > 0) V3Divider()
            V3ListRow(nameOf(it.productId), subtitle = StrImageChanges.beforeAfter(sourceLabel(it.before), sourceLabel(it.after)), chevron = false, subtitleLines = 3)
          }
        }
        (state.outcome as? ImageChangeDetailState.Outcome.ChangedSince)?.let { o ->
          Text(
            StrImageChanges.changedSince(o.products.joinToString("، ") { nameOf(it) }),
            style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 12.dp),
          )
        }
        if (!ch.rolledBack) {
          if (asking) {
            Text(StrImageChanges.rollbackMessage, style = V3Type.callout, color = c.ink, modifier = Modifier.padding(top = 16.dp))
            V3TextField(reason, { reason = it.take(ImageChanges.REASON_MAX) }, StrImageChanges.reason, Modifier.padding(top = 8.dp))
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              V3Button(
                StrImageChanges.rollbackAll, { scope.launch { state.rollback(reason); asking = false } },
                Modifier.weight(1f), kind = V3ButtonKind.DANGER, loading = state.busy, icon = Icons.AutoMirrored.Outlined.Undo,
              )
              V3Button(ir.vil3ntec.tohid.core.i18n.Str.cancel, { asking = false }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
            }
          } else {
            V3Button(
              StrImageChanges.rollbackAll, { state.clearOutcome(); asking = true },
              Modifier.fillMaxWidth().padding(top = 16.dp), kind = V3ButtonKind.SECONDARY, icon = Icons.AutoMirrored.Outlined.Undo,
            )
          }
        }
      }
    }
  }
}

/** `stock/images/changes/new` */
@Composable
internal fun RouteScope.ImageBatchRoute() {
  val api = onlineReportApi()
  val state = remember(api) { api?.let { ImageBatchState(ImageChangesRepository(it)) { "imgch-" + Ulid.next() } } }
  val scope = rememberCoroutineScope()
  var q by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val c = PhoneV3.colors
  if (state == null) { V3EmptyState(Icons.Outlined.Collections, StrImageChanges.off); return }
  state.done?.let { ch ->
    LaunchedEffect(ch.id) {
      toasts.show(StrImageChanges.appliedToast, V3Tone.SUCCESS)
      nav.replace(StockPaths.imageChange(ch.id))
    }
  }
  val products = remember(data.products) { data.products.filter { it.active } }
  val byId = remember(products) { products.associate { it.id to ImageChanges.Product(it.id, it.name, it.barcodes.firstOrNull().orEmpty()) } }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(StrImageChanges.storefrontNote, style = V3Type.footnote, color = c.inkMuted)
    V3ChipRow(Modifier.padding(top = 12.dp), contentPadding = PaddingValues(0.dp)) {
      V3Chip(StrImageChanges.actionLatest, state.action == ImageChanges.Action.LATEST_CENTRAL, { state.choose(ImageChanges.Action.LATEST_CENTRAL) })
      V3Chip(StrImageChanges.actionRevert, state.action == ImageChanges.Action.REVERT_CENTRAL, { state.choose(ImageChanges.Action.REVERT_CENTRAL) })
    }
    Text(
      if (state.action == ImageChanges.Action.LATEST_CENTRAL) StrImageChanges.actionLatestNote else StrImageChanges.actionRevertNote,
      style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp),
    )
    val planned = state.planned
    if (planned == null) {
      V3SearchField(q, { q = it }, StrImageChanges.search, Modifier.padding(top = 12.dp))
      Text(StrImageChanges.picked(state.selected.size, ImageChanges.MAX_ITEMS), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp))
      V3Section(StrImageChanges.pick) {
        pickList(products, q, state.selected).forEachIndexed { i, p ->
          if (i > 0) V3Divider()
          V3ListRow(
            p.name.ifBlank { p.id }, subtitle = p.barcodes.firstOrNull(), chevron = false, selected = p.id in state.selected,
            onClick = { state.toggle(p.id) },
          )
        }
      }
      V3Button(
        if (state.checking) StrImageChanges.checking else StrImageChanges.check, { scope.launch { state.check(byId) } },
        Modifier.fillMaxWidth().padding(top = 16.dp), enabled = state.selected.isNotEmpty(), loading = state.checking,
      )
      state.message?.let { Text(it, style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 8.dp)) }
      return@Column
    }
    val send = planned.filterIsInstance<ImageChanges.Planned.Send>()
    val skipped = planned.filterIsInstance<ImageChanges.Planned.Skipped>()
    V3Section(StrImageChanges.willChange(send.size)) {
      if (send.isEmpty()) V3ListRow(StrImageChanges.nothingToSend, chevron = false)
      send.forEachIndexed { i, s ->
        if (i > 0) V3Divider()
        val v = s.line.version
        V3ListRow(
          s.line.name.ifBlank { s.line.productId },
          subtitle = StrImageChanges.sourceCentral + (if (v != null) " · " + StrImageChanges.version(v) else ""),
          chevron = false,
        )
      }
    }
    if (skipped.isNotEmpty()) {
      V3Section(StrImageChanges.skippedCount(skipped.size)) {
        skipped.forEachIndexed { i, s ->
          if (i > 0) V3Divider()
          V3ListRow(byId[s.productId]?.name?.ifBlank { null } ?: s.productId, value = skipLabel(s.why), chevron = false)
        }
      }
    }
    val problem = state.problem
    val pid = problem?.productId
    when {
      problem != null && problem.confirmable && pid != null -> {
        Text(StrImageChanges.needsConfirm(nameOf(pid)), style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          V3Button(StrImageChanges.confirmThis, { state.confirm(pid); scope.launch { state.send(note) } }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
          V3Button(StrImageChanges.removeThis(nameOf(pid)), { state.remove(pid) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
        }
      }
      problem != null && problem.code == "key_reused" ->
        Text(StrImageChanges.keyReused, style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 12.dp))
      problem != null && pid != null -> {
        Text(StrImageChanges.itemFailed(nameOf(pid), state.message.orEmpty()), style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 12.dp))
        V3Button(StrImageChanges.removeThis(nameOf(pid)), { state.remove(pid) }, Modifier.fillMaxWidth().padding(top = 8.dp), kind = V3ButtonKind.SECONDARY)
      }
      state.message != null -> Text(state.message.orEmpty(), style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 12.dp))
    }
    if (send.isNotEmpty()) {
      V3TextField(note, { note = it.take(ImageChanges.NOTE_MAX) }, StrImageChanges.note, Modifier.padding(top = 12.dp))
      V3Button(
        StrImageChanges.apply, { scope.launch { state.send(note) } }, Modifier.fillMaxWidth().padding(top = 12.dp),
        loading = state.sending, enabled = problem?.confirmable != true,
      )
    }
  }
}

/** کالاهای فهرستِ انتخاب: برگزیده‌ها اول، بعد جورِ جست‌وجو (نام یا بارکد)؛ حداکثر ۲۰۰ ردیف تا صفحه سنگین نشود */
internal fun pickList(products: List<Product>, q: String, selected: List<String>): List<Product> {
  val key = q.trim()
  val chosen = selected.mapNotNull { id -> products.firstOrNull { it.id == id } }
  val rest = products.filter { p ->
    p.id !in selected && (key.isEmpty() || p.name.contains(key, ignoreCase = true) || p.barcodes.any { it.contains(key) })
  }
  return (chosen + rest).take(200)
}
