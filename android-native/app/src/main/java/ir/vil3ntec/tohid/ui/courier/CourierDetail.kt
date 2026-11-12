package ir.vil3ntec.tohid.ui.courier

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.core.courier.CourierBoardModel
import ir.vil3ntec.tohid.core.courier.CourierFlow
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.PlusCode
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.courier.TrackRow
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCourier
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3UiDark
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3StaggerState
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/*
 *  جزئیاتِ مأموریت به شکلِ مرجعِ صاحب محصول (courier-ref.jpg، صفحهٔ راست و کارتِ
 *  «From/To» ِ صفحهٔ میانی): بالا کارتِ تیرهٔ «از دکان ⇐ به مقصد» با «مسیریابی»
 *  (نقطه به برنامهٔ نقشهٔ گوشی — بی نقشهٔ ساختگی)، پایین برگهٔ تیره با دو زبانه:
 *  «جزئیاتِ پیگیری» (خطِ زمانیِ واقعی با زمانِ سرور، پلهٔ جاری روشن) و «جزئیاتِ
 *  ارسال». دکمه‌های پله ته برگه، زیرِ شست.
 */

private val SheetTop = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** عنوانِ هر ردیفِ خطِ زمانی */
private fun trackTitle(r: TrackRow): String = when {
  r.kind == "recall" -> StrCourierApp.evRecall
  r.kind == "photo" -> StrCourierApp.evPhoto
  r.kind == "created" || r.to == "requested" -> StrCourierApp.evCreated
  else -> when (r.to) {
    "assigned" -> StrCourierApp.evAssigned
    "rejected" -> StrCourierApp.evRejected
    "picked_up" -> StrCourierApp.evPickedUp
    "moving" -> StrCourierApp.hlMoving
    "arrived" -> StrCourierApp.evArrived
    "delivered" -> StrCourierApp.evDelivered
    "failed" -> StrCourierApp.hlFailed
    "returned" -> StrCourierApp.hlReturned
    "withdrawn" -> StrCourierApp.evWithdrawn
    else -> STATUS_LABEL[r.to] ?: r.to
  }
}

private fun trackSub(r: TrackRow): String {
  val who = when {
    r.pending -> StrCourierApp.byPending
    r.by == "courier" -> StrCourierApp.byCourier
    r.by == "shop" -> StrCourierApp.byShop
    else -> ""
  }
  val why = REASON_LABEL[r.reason]
  return listOfNotNull(who.ifBlank { null }, why).joinToString(" · ")
}

@Composable
internal fun CourierDetailPage(
  c: MissionCard,
  queue: QueueState,
  shopName: String,
  detailTab: String,
  onDetailTab: (String) -> Unit,
  onBack: () -> Unit,
  onAction: (String) -> Unit,
  onPhoto: () -> Unit,
  onNavigate: () -> Unit,
  seen: V3StaggerState,
  onRate: (() -> Unit)? = null,
  onCall: (() -> Unit)? = null,
) {
  val p = PhoneV3.colors
  val live = CourierFlow.isLive(c.status)
  val waiting = CourierQueue.pendingFor(queue, c.id).isNotEmpty()
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      V3IconButton(Icons.AutoMirrored.Outlined.ArrowBack, Str.goBack, onClick = onBack)
      Text(
        StrCourierApp.orderNo(c.orderNo.fa()),
        style = courierText(18.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(44.dp))
    }
    RouteCard(c, shopName, live, onNavigate, onCall)
    Spacer(Modifier.height(14.dp))
    //  برگهٔ تیرهٔ پایین (مرجع: Tracking Detail / Shipping Detail)
    Column(Modifier.weight(1f).fillMaxWidth().background(p.black, SheetTop).padding(top = 10.dp)) {
      Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(p.blackRaised, CourierPillShape))
      Spacer(Modifier.height(12.dp))
      CourierOnDark {
        V3Segmented(
          listOf(StrCourierApp.tabTrack, StrCourierApp.tabShipping),
          selected = if (detailTab == "track") 0 else 1,
          onSelect = { onDetailTab(if (it == 0) "track" else "ship") },
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      Spacer(Modifier.height(14.dp))
      AnimatedContent(
        targetState = detailTab,
        transitionSpec = {
          if (Motion.enabled) fadeIn(tween(220)) togetherWith fadeOut(tween(140))
          else EnterTransition.None togetherWith ExitTransition.None
        },
        modifier = Modifier.weight(1f),
        label = "detailTab",
      ) { t ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
          if (t == "track") TrackTab(c, queue, waiting, live, seen) else ShipTab(c, live)
        }
      }
      val actions = if (live) CourierFlow.actions(c.status) else emptyList()
      if (actions.isNotEmpty()) ActionArea(c, actions, onAction, onPhoto)
      //  DLV-18: امتیازِ پیک به تحویلِ انجام‌شده — فقط با پرچمِ دکان و یک بار
      if (onRate != null) {
        CourierOnDark { CourierSecondary(StrCourier.rate, onRate, Modifier.fillMaxWidth().padding(16.dp), icon = Icons.Outlined.Star) }
      }
    }
  }
}

/** «از دکان ⇐ به مقصد» روی سطحِ سرمه‌ای (مرجع: کارتِ From/To) */
@Composable
private fun RouteCard(c: MissionCard, shopName: String, live: Boolean, onNavigate: () -> Unit, onCall: (() -> Unit)?) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().courierDark(p).padding(16.dp)) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(12.dp).border(2.dp, dark.primaryOnDark, CircleShape))
        Box(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp).background(p.blackRaised))
        Icon(Icons.Outlined.Place, contentDescription = null, tint = dark.primaryOnDark, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(18.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(StrCourierApp.from, style = courierText(12.sp), color = p.onBlackMuted)
        Text(shopName.ifBlank { Str.shop }, style = courierText(15.sp, FontWeight.Bold), color = p.onBlack)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.blackRaised))
        Spacer(Modifier.height(10.dp))
        Text(StrCourierApp.to, style = courierText(12.sp), color = p.onBlackMuted)
        if (live) {
          //  DLV-02: «اول نشانه» — نشانه، «ناحیه · دروازهٔ رنگ»، بعد نشانیِ متنی
          val lines = landmarkLines(c).ifEmpty { listOf("—") }
          lines.forEachIndexed { i, line ->
            Text(line, style = if (i == 0) courierText(16.sp, FontWeight.Bold) else courierText(13.sp), color = if (i == 0) p.onBlack else p.onBlackMuted)
          }
          //  S33: Plus Code از پینِ مقصد (نه ژئوکدر، نه شبکه) — برای نقشهٔ آفلاین یا گفتن با تلفن
          missionPlusCode(c)?.let { Text(StrCourier.plusCode(it), style = courierText(13.sp, FontWeight.Medium), color = p.onBlack) }
        } else {
          Text(StrCourierApp.addressHidden, style = courierText(13.sp), color = p.onBlackMuted)
        }
      }
    }
    if (live && c.lat != null && c.lng != null) {
      Spacer(Modifier.height(14.dp))
      CourierOnDark { CourierSecondary(StrCourierApp.navigate, onNavigate, Modifier.fillMaxWidth(), icon = Icons.Outlined.Directions) }
    }
    //  DLV-02: «تماس با مشتری» — فقط با پرچمِ دکان و پس از «قبول» (`CourierExtras.canCall`)؛ شماره همان لحظه از سرور
    if (onCall != null) {
      Spacer(Modifier.height(10.dp))
      CourierOnDark { CourierSecondary(StrCourierApp.callCustomer, onCall, Modifier.fillMaxWidth(), icon = Icons.Outlined.Call) }
    }
  }
}

@Composable
private fun TrackTab(c: MissionCard, queue: QueueState, waiting: Boolean, live: Boolean, seen: V3StaggerState) {
  val p = PhoneV3.colors
  Row(verticalAlignment = Alignment.Bottom) {
    Column(Modifier.weight(1f)) {
      Text(StrCourierApp.orderNumber, style = courierText(12.sp), color = p.onBlackMuted)
      Text("#${c.orderNo.fa()}", style = courierText(22.sp, FontWeight.Bold), color = p.onBlack)
    }
    CourierOnDark {
      if (waiting) { QueuedPill(); Spacer(Modifier.width(6.dp)) }
      MissionStatus(c.status)
    }
  }
  if (live && reached(c.status) >= 0) {
    Spacer(Modifier.height(14.dp))
    StepTrack(c.status, onDark = true)
  }
  if (c.recallRequested && live) {
    Spacer(Modifier.height(12.dp))
    CourierOnDark { V3Message(V3Tone.ERROR, RECALL_NOTE) }
  }
  Spacer(Modifier.height(18.dp))
  val rows = CourierBoardModel.track(c, queue)
  if (rows.isEmpty()) {
    Text(StrCourierApp.timelineLater, style = courierText(13.sp), color = p.onBlackMuted)
  }
  rows.forEachIndexed { i, r ->
    TrackRowView(r, last = i == rows.lastIndex, modifier = Modifier.v3Stagger(seen, "t-${c.id}-${r.at}-$i", i))
  }
}

@Composable
private fun TrackRowView(r: TrackRow, last: Boolean, modifier: Modifier) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
      Spacer(Modifier.height(4.dp))
      when {
        r.current -> {
          //  پلهٔ جاری: حلقهٔ سبزآبی که آرام می‌تپد (بخشِ G)
          val pulse = if (Motion.enabled) {
            rememberInfiniteTransition(label = "pulse").animateFloat(
              initialValue = 0.85f, targetValue = 1.15f,
              animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseScale",
            ).value
          } else 1f
          Box(
            Modifier.size(20.dp).graphicsLayer { scaleX = pulse; scaleY = pulse }
              .background(dark.primaryOnDark.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
          ) { Box(Modifier.size(10.dp).background(dark.primaryOnDark, CircleShape)) }
        }
        r.pending -> Box(Modifier.size(12.dp).background(dark.awaiting.fg, CircleShape))
        else -> Box(Modifier.size(10.dp).border(2.dp, dark.primaryOnDark.copy(alpha = 0.7f), CircleShape))
      }
      if (!last) Box(Modifier.width(2.dp).weight(1f).padding(vertical = 4.dp).background(p.blackRaised))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f).padding(bottom = 18.dp)) {
      Text(
        trackTitle(r),
        style = courierText(15.sp, if (r.current) FontWeight.Bold else FontWeight.Medium),
        color = if (r.current) p.onBlack else p.onBlack.copy(alpha = 0.82f),
      )
      val sub = trackSub(r)
      if (sub.isNotBlank()) Text(sub, style = courierText(12.sp), color = p.onBlackMuted)
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(clock(r.at), style = courierText(13.sp, FontWeight.Medium), color = p.onBlack)
      Text(formatMillis(r.at), style = courierText(11.sp), color = p.onBlackMuted)
    }
  }
}

@Composable
private fun ShipTab(c: MissionCard, live: Boolean) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  val rows = buildList {
    add(Triple(StrCourierApp.itemsCount, if (c.itemsCount > 0) c.itemsCount.fa() else "—", false))
    if (c.expectedCollectMinor > 0) add(Triple(if (live) StrCourierApp.cashToCollect else StrCourierApp.orderCashLabel, afn(c.expectedCollectMinor), true))
    else add(Triple(StrCourierApp.cash, StrCourierApp.noCashFromCustomer, false))
    c.cashCollectedMinor?.let { add(Triple(StrCourierApp.collected, afn(it), true)) }
    if (c.fareMinor > 0) add(Triple(StrCourierApp.yourFare, afn(c.fareMinor), false))
    add(Triple(StrCourierApp.deliveryCode, if (c.requiresCode) StrCourierApp.askCustomer else StrCourierApp.notNeeded, false))
    add(Triple(StrCourierApp.parcelPhoto, if (c.photoAllowed) StrCourierApp.customerConsented else StrCourierApp.noPhoto, false))
    if (live) {
      if (c.customerName.isNotBlank()) add(Triple(StrCourierApp.customer, c.customerName, false))
      val note = listOfNotNull(
        c.leaveWith.ifBlank { null }?.let { StrCourierApp.leaveWith(it) },
        if (c.callBeforeArrival) StrCourierApp.callBefore else null,
      ).joinToString(" · ")
      add(Triple(StrCourierApp.deliveryNote, note.ifBlank { "—" }, false))
    }
  }
  rows.forEach { (label, value, strong) -> DarkKV(label, value, strong) }
  if (live && c.phoneMasked.isNotBlank()) DarkKV(StrCourierApp.maskedPhone, c.phoneMasked, false, ltr = true)
  //  CRM-09/CHK-08: عکسِ دروازه و یادداشتِ صوتیِ مشتری (پرچمِ `shop.storefront.address_media`؛ فقط تا مأموریت زنده است)
  if (live) CourierAddressMediaV3(c.id)
  if (!live) {
    Spacer(Modifier.height(10.dp))
    Text(StrCourierApp.customerErased, style = courierText(13.sp), color = p.onBlackMuted)
  }
  if (c.status == "failed") {
    Spacer(Modifier.height(10.dp))
    Text(
      StrCourierApp.failedReturnNote,
      style = courierText(13.sp), color = dark.awaiting.fg,
    )
  }
}

@Composable
private fun DarkKV(label: String, value: String, strong: Boolean, ltr: Boolean = false) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = courierText(14.sp), color = p.onBlackMuted)
    //  مقدار ته ردیف (مثلِ «Shipping Detail» ِ مرجع)؛ متنِ بلند دوخطی می‌شود
    Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
    Text(
      value,
      style = courierText(if (strong) 16.sp else 14.sp, if (strong) FontWeight.Bold else FontWeight.Medium)
        .copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
      color = p.onBlack, textAlign = TextAlign.End,
      modifier = Modifier.widthIn(max = 220.dp),
    )
  }
  Box(Modifier.fillMaxWidth().height(1.dp).background(p.blackRaised))
}

/** دکمه‌های پله ته برگهٔ تیره (کیتِ شب): اصلی بزرگ، بقیه کنارِ هم؛ کارِ منفی گونهٔ «خطر» */
@Composable
private fun ActionArea(c: MissionCard, actions: List<String>, onAction: (String) -> Unit, onPhoto: () -> Unit) {
  val positive = actions.filterNot { isNegative(it) }
  val negative = actions.filter { isNegative(it) }
  val photo = c.photoAllowed && c.status == "arrived"
  CourierOnDark {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      positive.firstOrNull()?.let { to ->
        StepButton(actionLabel(c.status, to), { onAction(to) }, Modifier.fillMaxWidth())
      }
      val rest = positive.drop(1)
      if (rest.isNotEmpty() || photo || negative.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          rest.forEach { to -> CourierSecondary(actionLabel(c.status, to), { onAction(to) }, Modifier.weight(1f)) }
          if (photo) CourierSecondary(StrCourierApp.parcelPhoto, onPhoto, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera)
          negative.forEach { to -> CourierSecondary(actionLabel(c.status, to), { onAction(to) }, Modifier.weight(1f), negative = true) }
        }
      }
    }
  }
}

/** S33 — Plus Code ِ ده‌نویسه‌ای از پینِ مأموریت؛ بی پین ⇒ `null` (همان کارتِ پیشین) */
internal fun missionPlusCode(c: MissionCard): String? {
  val lat = c.lat ?: return null
  val lng = c.lng ?: return null
  return PlusCode.encode(lat, lng)
}
