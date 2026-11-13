package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReturnRequests
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.orders.OrderReturnRequests
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ReturnRequestPrefill
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.data.repo.ReturnRequestsRepository
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.ReturnRequestsController
import ir.vil3ntec.tohid.ui.screens.feedbackImage
import ir.vil3ntec.tohid.ui.screens.feedbackStamp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnFlowSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 *  RET-11 — «درخواست‌های برگشت» ِ ویترین سرِ صفِ سفارش‌ها (همان جای «گزارش‌ها و نظرها»)؛ اندروید و کامپیوتر همین کد.
 *  پشتِ پرچمِ خاموشِ `shop.storefront.return_requests`: تا سرور فهرست نداده، نه دکمه‌ای هست و نه برگه‌ای.
 *  «پذیرفتن» ⇒ وضعیتِ سرور، سپس **همان** برگهٔ برگشتِ موجود (`ReturnFlowSheet` ⇐ `ReturnFlow.post`) با اقلامِ پرشده و
 *  سندِ ثابتِ `o-<سفارش>-rq-<درخواست>`؛ پس از «ثبت» ِ برگه ⇒ «برگشت ثبت شد» به سرور. پول و موجودی فقط از همان برگه.
 *  مرزِ پلتفرم: `Context` (دلیل‌های دکان، `ReturnSettings`).
 */

@Composable
internal fun RouteScope.ReturnRequestsEntry(api: ApiClient, canManage: Boolean, rev: Long) {
  val scope = rememberCoroutineScope()
  val ctl = remember(api) { ReturnRequestsController(ReturnRequestsRepository(api), scope) }
  var open by remember { mutableStateOf(false) }
  var posting by remember { mutableStateOf<OrderReturnRequests.Request?>(null) }
  LaunchedEffect(ctl, rev) {
    while (true) {
      ctl.refresh()
      delay(ReturnRequestsController.POLL_MS)
    }
  }
  //  پیامِ هر نوشتن، چه برگه باز باشد چه نه
  LaunchedEffect(ctl.note) { ctl.note?.let { (ok, text) -> toasts.show(text, if (ok) V3Tone.SUCCESS else V3Tone.ERROR) } }
  val b = ctl.board
  LaunchedEffect(b.visible) { if (!b.visible) open = false }
  if (b.visible) {
    V3Button(
      if (b.waiting > 0) StrReturnRequests.entry(b.waiting) else StrReturnRequests.title, { open = true },
      kind = V3ButtonKind.SECONDARY, height = 40.dp,
    )
  }
  if (open) ReturnRequestsSheet(ctl, canManage, onClose = { open = false }, onPost = { r -> open = false; posting = r })
  posting?.let { r -> ReturnRequestPost(ctl, r) { posting = null } }
}

/** برگهٔ برگشتِ موجود برای یک درخواستِ پذیرفته — بی فروشِ همان سفارش در دفترِ این دستگاه ⇒ پیام و هیچ */
@Composable
private fun RouteScope.ReturnRequestPost(ctl: ReturnRequestsController, r: OrderReturnRequests.Request, onDone: () -> Unit) {
  val context = LocalContext.current
  val sale = ReturnRequestPrefill.saleOf(data, r)
  if (sale == null) { LaunchedEffect(r.id) { toasts.show(StrReturnRequests.noSale, V3Tone.WARNING); onDone() }; return }
  val preset = remember(r.id) { ReturnRequestPrefill.of(data, r, ReturnSettings.reasons(context)) }
  ReturnFlowSheet(
    d = data,
    sale = sale,
    save = { base, next -> store.save(base, next) },
    onPosted = { doc, note -> toasts.show(note, V3Tone.SUCCESS); ctl.complete(r, doc); onDone() },
    onClose = onDone,
    preset = preset,
  )
}

@Composable
private fun RouteScope.ReturnRequestsSheet(
  ctl: ReturnRequestsController,
  canManage: Boolean,
  onClose: () -> Unit,
  onPost: (OrderReturnRequests.Request) -> Unit,
) {
  val c = PhoneV3.colors
  val b = ctl.board
  LaunchedEffect(Unit) { ctl.refresh() }
  V3Sheet(onDismiss = onClose, title = StrReturnRequests.title) { closer ->
    V3ChipRow(contentPadding = PaddingValues(0.dp)) {
      for (f in OrderReturnRequests.FILTERS) V3Chip(StrReturnRequests.filter(f), b.filter == f, { ctl.show(f) }, count = if (f == "pending" && b.waiting > 0) b.waiting else null)
    }
    Spacer(Modifier.height(10.dp))
    ctl.listError?.let { V3Message(V3Tone.ERROR, it, action = ir.vil3ntec.tohid.core.i18n.StrKit.retry, onAction = { ctl.reload() }); Spacer(Modifier.height(8.dp)) }
    if (b.loaded && b.list.isEmpty()) Text(StrReturnRequests.empty, style = V3Type.callout, color = c.inkMuted)
    for (r in b.list) {
      ReturnRequestCard(ctl, r, canManage, posted = r.accepted && ReturnFlow.doc(data, r.docId) != null) { picked ->
        //  بی فروشِ همان سفارش در دفترِ این دستگاه، پذیرفتن هم نه (برگهٔ برگشت چیزی برای برگرداندن ندارد)
        if (ReturnRequestPrefill.saleOf(data, picked) == null) toasts.show(StrReturnRequests.noSale, V3Tone.WARNING)
        else if (picked.pending) ctl.accept(picked) { u -> closer.close { onPost(u) } }
        else closer.close { onPost(picked) }
      }
      Spacer(Modifier.height(10.dp))
    }
  }
}

/** یک درخواست با کارهایش؛ `posted` = سندِ همین درخواست پیش‌تر در دفترِ همین دستگاه نشسته (فقط خبرِ «ثبت شد» مانده) */
@Composable
internal fun ReturnRequestCard(
  ctl: ReturnRequestsController,
  r: OrderReturnRequests.Request,
  canManage: Boolean,
  posted: Boolean,
  onPost: (OrderReturnRequests.Request) -> Unit,
) {
  val c = PhoneV3.colors
  var rejecting by remember(r.id) { mutableStateOf(false) }
  var why by remember(r.id) { mutableStateOf("") }
  var big by remember(r.id) { mutableStateOf<String?>(null) }
  V3Card(radius = 16.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrReturnRequests.order(r.orderNo), Modifier.weight(1f), style = V3Type.headline, color = c.ink)
      V3StatusPill(
        when (r.status) { "pending" -> V3Status.AWAITING; "accepted" -> V3Status.REVIEW; "completed" -> V3Status.DELIVERED; else -> V3Status.CANCELLED },
        StrReturnRequests.status(r.status),
      )
    }
    Text(feedbackStamp(r.createdAt), style = V3Type.footnote, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    Text(StrReturnRequests.reason(r.reason), style = V3Type.body, color = c.ink)
    if (r.text.isNotBlank()) Text(r.text, style = V3Type.callout, color = c.ink)
    for (l in r.lines) {
      Text(StrReturnRequests.line(l.name, qty(l.qtyMilli / 1000.0) + unitOf(l.unit), qty(l.boughtMilli / 1000.0) + unitOf(l.unit)), style = V3Type.callout, color = c.inkMuted)
    }
    Text(StrReturnRequests.method(r.method), style = V3Type.footnote, color = c.inkMuted)
    if (r.photos.isNotEmpty()) {
      Text(StrReturnRequests.photos(r.photos.size), style = V3Type.footnote, color = c.inkMuted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in r.photos) Thumb(ctl, r.id, p.id) { big = p.id }
      }
    }
    r.decisionNote?.let { Text(StrReturnRequests.note(it), style = V3Type.footnote, color = c.inkMuted) }
    if (canManage && r.open) {
      Spacer(Modifier.height(8.dp))
      when {
        r.pending -> V3Button(StrReturnRequests.accept, { onPost(r) }, Modifier.fillMaxWidth(), enabled = ctl.busy == null, loading = ctl.busy == r.id)
        //  سند پیش‌تر در همین دفتر نشسته (خبرِ «ثبت شد» نرسیده بود) ⇒ فقط همان خبر، نه برگهٔ دوم
        posted -> V3Button(StrReturnRequests.report, { ctl.complete(r, r.docId) }, Modifier.fillMaxWidth(), enabled = ctl.busy == null, loading = ctl.busy == r.id)
        else -> V3Button(StrReturnRequests.post, { onPost(r) }, Modifier.fillMaxWidth(), enabled = ctl.busy == null)
      }
      if (!posted) {
        Spacer(Modifier.height(6.dp))
        if (!rejecting) V3Button(StrReturnRequests.reject, { rejecting = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, height = 40.dp)
        else Column {
          Text(StrReturnRequests.rejectHint, style = V3Type.footnote, color = c.inkMuted)
          V3TextField(why, { why = it.take(OrderReturnRequests.NOTE_MAX) }, StrReturnRequests.rejectField, Modifier.fillMaxWidth())
          Spacer(Modifier.height(6.dp))
          V3Button(
            StrReturnRequests.rejectSend, { ctl.reject(r, why) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER,
            enabled = ctl.busy == null && OrderReturnRequests.noteOk(why), loading = ctl.busy == r.id,
          )
        }
      }
    }
  }
  big?.let { id -> ctl.images[id]?.let { img -> PhotoSheet(img) { big = null } } }
}

private fun unitOf(u: String): String = if (u.isBlank()) "" else " $u"

@Composable
private fun Thumb(ctl: ReturnRequestsController, requestId: String, photoId: String, onOpen: () -> Unit) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  LaunchedEffect(photoId) { ctl.photo(requestId, photoId, ::feedbackImage) }
  val img = ctl.images[photoId]
  val bad = ctl.failed[photoId] == true
  Box(
    Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)).background(c.hairline)
      .clickable { if (img != null) onOpen() else if (bad) scope.launch { ctl.photo(requestId, photoId, ::feedbackImage) } },
    contentAlignment = Alignment.Center,
  ) {
    when {
      img != null -> Image(img, contentDescription = StrReturnRequests.photos(1), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      bad -> Icon(Icons.Outlined.Refresh, ir.vil3ntec.tohid.core.i18n.StrKit.retry, Modifier.size(22.dp), tint = c.inkMuted)
    }
  }
}

@Composable
private fun PhotoSheet(img: androidx.compose.ui.graphics.ImageBitmap, onClose: () -> Unit) {
  V3Sheet(onDismiss = onClose, title = StrReturnRequests.photos(1)) {
    Image(img, contentDescription = StrReturnRequests.photos(1), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(420.dp))
  }
}
