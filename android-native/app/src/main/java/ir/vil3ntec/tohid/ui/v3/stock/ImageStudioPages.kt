package ir.vil3ntec.tohid.ui.v3.stock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrImageStudio
import ir.vil3ntec.tohid.core.image.ImageStudio
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ImageStudioRepository
import ir.vil3ntec.tohid.data.repo.ImageStudioSource
import ir.vil3ntec.tohid.displayMillis
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.screens.onlineReportApi
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.products.decodeVersion
import kotlinx.coroutines.launch

/*
 *  ۴-۲۳ پرسشِ ۱ «الف» (AI-02، IMG-PRO-01..04) — «استودیوی عکس» در برنامهٔ دکان (پیش‌تر فقط API ِ سرور):
 *    `stock/images/studio`        فهرستِ دسته‌ها و «دستهٔ تازه»
 *    `stock/images/studio/new`    عکس‌های خودِ دکان ⇐ گزینش و preset ⇐ آغاز (کلیدِ یکتایی؛ تکرار همان دسته)
 *    `stock/images/studio/{id}`   پیشرفت، ایستادن/ادامه/لغو/دوباره، و «اصل / نسخهٔ استودیو» ِ هر عکس با تأیید یا رد
 *  ⛔ پرچمِ خاموشِ `shop.image_studio` (یا `shop.image_bank`) ⇒ صفحه «روشن نیست»؛ اصل هرگز عوض نمی‌شود.
 *  ⛔ خروجیِ پرخطر فقط با تأییدِ جدای همان نسخه («با وجودِ خطر») — هرگز یک‌جا.
 */

internal fun presetLabel(p: String): String = when (p) {
  "enhance" -> StrImageStudio.presetEnhance
  "ad" -> StrImageStudio.presetAd
  else -> StrImageStudio.presetCatalog
}

internal fun stateLabel(state: String, finished: Boolean): String = when {
  state == "cancelled" -> StrImageStudio.stateCancelled
  finished -> StrImageStudio.stateDone
  state == "paused" -> StrImageStudio.statePaused
  else -> StrImageStudio.stateActive
}

internal fun actionLabel(a: String): String = when (a) {
  "pause" -> StrImageStudio.pause
  "resume" -> StrImageStudio.resume
  "cancel" -> StrImageStudio.cancel
  else -> StrImageStudio.retryFailed
}

/** پیش‌نمایشِ یک فایلِ بانک (نشانیِ خودِ سرور) با نشستِ کاربر */
@Composable
private fun BankPicture(src: ImageStudioSource, url: String?, side: Int) {
  val bmp by produceState<ImageBitmap?>(null, url) {
    value = url?.let { (src.bytes(it) as? ApiResult.Success)?.value?.let(::decodeVersion) }
  }
  Box(Modifier.size(side.dp)) {
    bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
  }
}

/** `stock/images/studio` */
@Composable
internal fun RouteScope.ImageStudioRoute() {
  val api = onlineReportApi()
  val state = remember(api) { api?.let { ImageStudioListState(ImageStudioRepository(it)) } }
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(StrImageStudio.note, style = V3Type.footnote, color = c.inkMuted)
    if (state != null && !state.off) {
      V3Button(StrImageStudio.newBatch, { nav.open(StockPaths.IMAGE_STUDIO_NEW) }, Modifier.padding(top = 12.dp), icon = Icons.Outlined.Add)
    }
    val list = state?.batches
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.AutoFixHigh, StrImageStudio.off)
      list == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrImageStudio.failed, action = StrImageStudio.retry, onAction = { tick++ })
      list == null -> Text(StrImageStudio.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      list.isEmpty() -> V3EmptyState(Icons.Outlined.PhotoLibrary, StrImageStudio.none)
      else -> V3Section(null) {
        list.forEachIndexed { i, b ->
          if (i > 0) V3Divider()
          V3ListRow(
            presetLabel(b.preset),
            subtitle = StrImageStudio.line(stateLabel(b.state, b.finished >= b.total), StrImageStudio.progress(b.finished, b.total, b.percent)) +
              " · " + displayMillis(b.createdAt),
            icon = Icons.Outlined.AutoFixHigh,
            onClick = { nav.open(StockPaths.imageStudio(b.id)) },
          )
        }
      }
    }
  }
}

/** `stock/images/studio/new` */
@Composable
internal fun RouteScope.ImageStudioNewRoute() {
  val api = onlineReportApi()
  val repo = remember(api) { api?.let { ImageStudioRepository(it) } }
  val state = remember(repo) { repo?.let { ImageStudioNewState(it) { "studio-" + Ulid.next() } } }
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  state?.error?.let { e -> LaunchedEffect(e) { toasts.show(e, V3Tone.ERROR) } }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    val photos = state?.photos
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.AutoFixHigh, StrImageStudio.off)
      photos == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrImageStudio.failed, action = StrImageStudio.retry, onAction = { tick++ })
      photos == null -> Text(StrImageStudio.loading, style = V3Type.callout, color = c.inkMuted)
      photos.isEmpty() -> V3EmptyState(Icons.Outlined.PhotoLibrary, StrImageStudio.noPhotos)
      else -> {
        V3ChipRow {
          (state.config?.presets ?: ImageStudio.PRESETS).forEach { p -> V3Chip(presetLabel(p), state.preset == p, { state.preset = p }) }
        }
        Text(StrImageStudio.pickPhotos, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 12.dp))
        Text(StrImageStudio.selected(state.picked.size, state.max), style = V3Type.footnote, color = c.inkMuted)
        when (val p = state.problem) {
          ImageStudio.Check.Empty -> Text(StrImageStudio.emptyPick, style = V3Type.footnote, color = Shop.colors.danger)
          is ImageStudio.Check.TooMany -> Text(StrImageStudio.tooMany(p.max), style = V3Type.footnote, color = Shop.colors.danger)
          else -> Unit
        }
        V3Section(null) {
          photos.forEachIndexed { i, m ->
            if (i > 0) V3Divider()
            V3ListRow(
              data.products.firstOrNull { it.id == m.productId }?.name?.ifBlank { null } ?: m.productId.ifBlank { m.id },
              leading = { BankPicture(repo!!, m.thumb, 44) },
              selected = m.id in state.picked,
              chevron = false,
              onClick = { state.toggle(m.id) },
            )
          }
        }
        if (state.next != null) V3Button(StrImageStudio.more, { scope.launch { state.more() } }, kind = V3ButtonKind.SECONDARY)
        V3Button(
          StrImageStudio.start,
          { scope.launch { state.start()?.let { nav.replace(StockPaths.imageStudio(it)) } } },
          Modifier.fillMaxWidth().padding(top = 12.dp), loading = state.busy, enabled = !state.busy,
        )
      }
    }
  }
}

/** `stock/images/studio/{id}` */
@Composable
internal fun RouteScope.ImageStudioBatchRoute(id: String) {
  val api = onlineReportApi()
  val repo = remember(api) { api?.let { ImageStudioRepository(it) } }
  val state = remember(repo, id) { repo?.let { ImageStudioBatchState(it, id) } }
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  if (state != null) LaunchedEffect(state, tick) { state.load() }
  state?.outcome?.let { o ->
    LaunchedEffect(o) {
      when (o) {
        is ImageStudioBatchState.Outcome.Reviewed -> toasts.show(if (o.approved) StrImageStudio.approved else StrImageStudio.rejected, V3Tone.SUCCESS)
        is ImageStudioBatchState.Outcome.Failed -> toasts.show(o.message, V3Tone.ERROR)
      }
    }
  }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    val b = state?.batch
    when {
      state == null || state.off -> V3EmptyState(Icons.Outlined.AutoFixHigh, StrImageStudio.off)
      b == null && state.failed -> V3EmptyState(Icons.Outlined.CloudOff, StrImageStudio.failed, action = StrImageStudio.retry, onAction = { tick++ })
      b == null -> Text(StrImageStudio.loading, style = V3Type.callout, color = c.inkMuted)
      else -> {
        Text(presetLabel(b.preset), style = V3Type.headline, color = c.ink)
        Text(
          StrImageStudio.line(stateLabel(b.state, b.finished), StrImageStudio.progress(b.progress.finished, b.total, b.progress.percent)),
          style = V3Type.callout, color = c.inkMuted,
        )
        if (b.needsReview > 0) Text(StrImageStudio.toReview(b.needsReview), style = V3Type.callout, color = c.ink)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          b.actions.forEach { a ->
            V3Button(actionLabel(a), { scope.launch { state.act(a) } }, kind = V3ButtonKind.SECONDARY, enabled = !state.busy)
          }
          V3Button(StrImageStudio.retry, { tick++ }, kind = V3ButtonKind.SECONDARY)
        }
        b.items.forEach { it0 ->
          V3Section(null, Modifier.padding(top = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Column { Text(StrImageStudio.before, style = V3Type.footnote, color = c.inkMuted); BankPicture(repo!!, it0.before, 140) }
              Column {
                Text(StrImageStudio.after, style = V3Type.footnote, color = c.inkMuted)
                if (it0.after != null) BankPicture(repo!!, it0.after, 140)
                else Text(if (it0.status == "failed") StrImageStudio.failedItem else StrImageStudio.waiting, style = V3Type.callout, color = c.inkMuted)
              }
            }
            when {
              it0.reviewable -> {
                if (state.riskAsk == it0.imageId) {
                  Text(StrImageStudio.riskTitle, style = V3Type.callout, color = Shop.colors.danger)
                  V3Button(StrImageStudio.approveRisk, { scope.launch { state.review(it0, approve = true, confirmRisk = true) } },
                    kind = V3ButtonKind.DANGER, enabled = !state.busy)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  V3Button(StrImageStudio.approve, { scope.launch { state.review(it0, approve = true) } }, enabled = !state.busy)
                  V3Button(StrImageStudio.reject, { scope.launch { state.review(it0, approve = false) } }, kind = V3ButtonKind.SECONDARY, enabled = !state.busy)
                }
              }
              it0.review == "approved" -> Text(StrImageStudio.approved, style = V3Type.callout, color = c.inkMuted)
              it0.review == "rejected" -> Text(StrImageStudio.rejected, style = V3Type.callout, color = c.inkMuted)
              else -> Unit
            }
          }
        }
        if (b.next != null) V3Button(StrImageStudio.more, { scope.launch { state.more() } }, kind = V3ButtonKind.SECONDARY)
      }
    }
  }
}
