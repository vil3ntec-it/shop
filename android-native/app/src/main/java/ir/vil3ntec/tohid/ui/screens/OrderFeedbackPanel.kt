package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOrderFeedback
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.orders.OrderFeedback
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay

/*
 *  «گزارش‌ها و نظرها» در صفحهٔ سفارش‌ها — NTF-07 (گزارشِ مشکل با عکس؛ «رسیدگی شد» با پاسخ) و NTF-09 (نظرِ مشتری؛
 *  «تأیید و انتشار» / «رد»). پشتِ پرچم‌های خاموشِ `shop.orders.issue_reports` و `shop.orders.reviews`: تا سرور فهرست
 *  نداده، نه دکمه‌ای هست و نه برگه‌ای. نوشتن فقط با `orders.manage` (سرور دوباره می‌سنجد). عکس با توکنِ کارمند و همان
 *  رمزگشاییِ رسانهٔ چت (`OrderFeedbackPhotoAndroid.kt`). حال در `OrderFeedbackState.kt`.
 */

@Composable
internal fun OrderFeedbackEntry(api: ApiClient, canManage: Boolean, rev: Long) {
  val ctl = rememberOrderFeedback(api)
  var open by remember { mutableStateOf(false) }
  LaunchedEffect(ctl, rev) {
    while (true) {
      ctl.refresh()
      delay(OrderFeedbackController.POLL_MS)
    }
  }
  val b = ctl.board
  //  پرچم میانِ کار خاموش شد ⇒ برگه بسته و دکمه نیست
  LaunchedEffect(b.visible) { if (!b.visible) open = false }
  if (!b.visible) return
  Spacer(Modifier.width(8.dp))
  TohidSecondaryButton(if (b.waiting > 0) StrOrderFeedback.entry(b.waiting) else StrOrderFeedback.title, onClick = { open = true })
  if (open) OrderFeedbackSheet(ctl, canManage, onClose = { open = false })
}

@Composable
private fun OrderFeedbackSheet(ctl: OrderFeedbackController, canManage: Boolean, onClose: () -> Unit) {
  val c = Shop.colors
  val b = ctl.board
  var tab by remember { mutableStateOf(if (b.issuesOn == true) "issues" else "reviews") }
  LaunchedEffect(Unit) { ctl.refresh() }
  TohidBottomSheet(title = StrOrderFeedback.title, onDismiss = onClose) {
    SheetBody {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (b.issuesOn == true) TohidFilterChip(StrOrderFeedback.tabIssues(b.openIssues), selected = tab == "issues", onClick = { tab = "issues" })
        if (b.reviewsOn == true) TohidFilterChip(StrOrderFeedback.tabReviews(b.pendingReviews), selected = tab == "reviews", onClick = { tab = "reviews" })
      }
      if (!canManage) Text(StrOrderFeedback.readOnly, style = MaterialTheme.typography.labelSmall, color = c.muted)
      ctl.note?.let { (ok, text) -> Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) c.success else c.danger) }
      when {
        tab == "issues" && b.issuesOn == true -> IssuesPane(ctl, canManage)
        b.reviewsOn == true -> ReviewsPane(ctl, canManage)
        b.issuesOn == true -> IssuesPane(ctl, canManage)
      }
    }
  }
}

@Composable
private fun ListState(loaded: Boolean, empty: Boolean, emptyText: String, ctl: OrderFeedbackController): Boolean {
  val c = Shop.colors
  if (!loaded) {
    val e = ctl.listError
    if (e == null) TohidLoadingState(rows = 3)
    else {
      ErrorLine(StrOrderFeedback.listFailed + " — " + e)
      TohidSecondaryButton(StrOrderFeedback.retry, onClick = { ctl.reload() })
    }
    return false
  }
  if (empty) {
    Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = c.muted)
    return false
  }
  return true
}

// ── گزارشِ مشکل (NTF-07) ──────────────────────────────────────────────

@Composable
private fun IssuesPane(ctl: OrderFeedbackController, canManage: Boolean) {
  val c = Shop.colors
  val b = ctl.board
  Text(StrOrderFeedback.issuesHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (f in OrderFeedback.ISSUE_FILTERS) TohidFilterChip(issueFilterText(f), selected = b.issueFilter == f, onClick = { ctl.showIssues(f) })
  }
  if (!ListState(b.issuesLoaded, b.issues.isEmpty(), StrOrderFeedback.issuesEmpty, ctl)) return
  for (i in b.issues) IssueCard(ctl, i, canManage)
}

@Composable
private fun IssueCard(ctl: OrderFeedbackController, i: OrderFeedback.Issue, canManage: Boolean) {
  val c = Shop.colors
  var reply by remember(i.id) { mutableStateOf("") }
  var big by remember(i.id) { mutableStateOf<String?>(null) }
  TohidCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrOrderFeedback.issueHead(i.orderNo, feedbackKind(i.kind)), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
      StatusPill(if (i.open) StrOrderFeedback.stOpen else StrOrderFeedback.stResolved, if (i.open) PillTone.WARNING else PillTone.SUCCESS)
    }
    Text(feedbackStamp(i.createdAt), style = MaterialTheme.typography.labelSmall, color = c.muted)
    Text(i.text, style = MaterialTheme.typography.bodyMedium, color = c.text)
    if (i.photos.isNotEmpty()) {
      Text(StrOrderFeedback.photos(i.photos.size), style = MaterialTheme.typography.labelSmall, color = c.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (p in i.photos) FeedbackThumb(ctl, i.id, p.id) { big = p.id }
      }
    }
    i.reply?.let { Text(StrOrderFeedback.replied(it), style = MaterialTheme.typography.bodySmall, color = c.muted) }
    if (i.open && canManage) {
      TohidTextField(reply, { reply = it.take(OrderFeedback.REPLY_MAX) }, label = StrOrderFeedback.replyLabel, supporting = StrOrderFeedback.replyHint, singleLine = false)
      TohidButton(
        StrOrderFeedback.resolve, onClick = { ctl.resolve(i, reply) },
        enabled = ctl.busy == null && OrderFeedback.replyOk(reply), busy = ctl.busy == i.id,
      )
    }
  }
  big?.let { id -> ctl.images[id]?.let { img -> PhotoSheet(img) { big = null } } }
}

@Composable
private fun FeedbackThumb(ctl: OrderFeedbackController, issueId: String, photoId: String, onOpen: () -> Unit) {
  val c = Shop.colors
  LaunchedEffect(photoId) { ctl.photo(issueId, photoId, ::feedbackImage) }
  val img = ctl.images[photoId]
  val bad = ctl.failed[photoId] == true
  Box(
    Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(c.surface)
      .clickable { if (img != null) onOpen() else if (bad) ctl.retryPhoto(issueId, photoId, ::feedbackImage) },
    contentAlignment = Alignment.Center,
  ) {
    when {
      img != null -> Image(img, contentDescription = StrOrderFeedback.photo, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      bad -> Text(StrOrderFeedback.photoFailed, Modifier.padding(6.dp), style = MaterialTheme.typography.labelSmall, color = c.danger)
      else -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    }
  }
}

@Composable
private fun PhotoSheet(img: androidx.compose.ui.graphics.ImageBitmap, onClose: () -> Unit) {
  TohidBottomSheet(title = StrOrderFeedback.photo, onDismiss = onClose) {
    Image(img, contentDescription = StrOrderFeedback.photo, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp))
  }
}

// ── نظرها (NTF-09) ───────────────────────────────────────────────────

@Composable
private fun ReviewsPane(ctl: OrderFeedbackController, canManage: Boolean) {
  val c = Shop.colors
  val b = ctl.board
  Text(StrOrderFeedback.reviewsHint, style = MaterialTheme.typography.bodySmall, color = c.muted)
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (f in OrderFeedback.REVIEW_FILTERS) TohidFilterChip(reviewFilterText(f), selected = b.reviewFilter == f, onClick = { ctl.showReviews(f) })
  }
  if (!ListState(b.reviewsLoaded, b.reviews.isEmpty(), StrOrderFeedback.reviewsEmpty, ctl)) return
  for (r in b.reviews) ReviewCard(ctl, r, canManage)
}

@Composable
private fun ReviewCard(ctl: OrderFeedbackController, r: OrderFeedback.Review, canManage: Boolean) {
  val c = Shop.colors
  val (label, tone) = when (r.status) {
    "approved" -> StrOrderFeedback.stApproved to PillTone.SUCCESS
    "rejected" -> StrOrderFeedback.stRejected to PillTone.NEUTRAL
    else -> StrOrderFeedback.stPending to PillTone.WARNING
  }
  TohidCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrOrderFeedback.reviewHead(r.orderNo), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = c.text)
      StatusPill(label, tone)
    }
    val stars = StrOrderFeedback.stars(r.stars)
    Text(OrderFeedback.starLine(r.stars), Modifier.semantics { contentDescription = stars }, style = MaterialTheme.typography.titleMedium, color = c.warning)
    Text(r.comment.ifBlank { StrOrderFeedback.noComment }, style = MaterialTheme.typography.bodyMedium, color = if (r.comment.isBlank()) c.muted else c.text)
    Text(feedbackStamp(r.createdAt), style = MaterialTheme.typography.labelSmall, color = c.muted)
    if (canManage) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (r.status != "approved") TohidButton(
        StrOrderFeedback.approve, onClick = { ctl.moderate(r, OrderFeedback.APPROVE) },
        enabled = ctl.busy == null, busy = ctl.busy == r.id,
      )
      if (r.status != "rejected") TohidDangerButton(StrOrderFeedback.reject, onClick = { ctl.moderate(r, OrderFeedback.REJECT) }, enabled = ctl.busy == null)
    }
  }
}
