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
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Place
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
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.courier.TrackRow
import ir.vil3ntec.tohid.fa
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3UiDark

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
  r.kind == "recall" -> "دکان سفارش را پس خواست"
  r.kind == "photo" -> "عکسِ بسته ثبت شد"
  r.kind == "created" || r.to == "requested" -> "کارِ تازه سپرده شد"
  else -> when (r.to) {
    "assigned" -> "قبول کردید"
    "rejected" -> "رد کردید"
    "picked_up" -> "بسته را از دکان گرفتید"
    "moving" -> "در راهِ مشتری"
    "arrived" -> "به مقصد رسیدید"
    "delivered" -> "به مشتری تحویل شد"
    "failed" -> "تحویل نشد"
    "returned" -> "بسته به دکان برگشت"
    "withdrawn" -> "دکان پس گرفت"
    else -> STATUS_LABEL[r.to] ?: r.to
  }
}

private fun trackSub(r: TrackRow): String {
  val who = when {
    r.pending -> "در صفِ گوشی — هنوز به دکان نرسیده"
    r.by == "courier" -> "توسطِ شما"
    r.by == "shop" -> "توسطِ دکان"
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
  seen: MutableSet<String>,
) {
  val p = PhoneV3.colors
  val live = CourierFlow.isLive(c.status)
  val waiting = CourierQueue.pendingFor(queue, c.id).isNotEmpty()
  Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "بازگشت", onBack)
      Text(
        "سفارشِ #${c.orderNo.fa()}",
        style = v3Style(18.sp, FontWeight.Bold), color = p.ink, textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(44.dp))
    }
    RouteCard(c, shopName, live, onNavigate)
    Spacer(Modifier.height(14.dp))
    //  برگهٔ تیرهٔ پایین (مرجع: Tracking Detail / Shipping Detail)
    Column(Modifier.weight(1f).fillMaxWidth().background(p.black, SheetTop).padding(top = 10.dp)) {
      Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).background(p.blackRaised, PillShape))
      Spacer(Modifier.height(12.dp))
      TwoTabs(
        "جزئیاتِ پیگیری" to null, "جزئیاتِ ارسال" to null,
        firstSelected = detailTab == "track",
        onSelect = { onDetailTab(if (it) "track" else "ship") },
        modifier = Modifier.padding(horizontal = 16.dp),
        onDark = true,
      )
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
    }
  }
}

/** «از دکان ⇐ به مقصد» روی سطحِ سرمه‌ای (مرجع: کارتِ From/To) */
@Composable
private fun RouteCard(c: MissionCard, shopName: String, live: Boolean, onNavigate: () -> Unit) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().v3Dark(p).padding(16.dp)) {
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
        Text("از", style = v3Style(12.sp), color = p.onBlackMuted)
        Text(shopName.ifBlank { "دکان" }, style = v3Style(15.sp, FontWeight.Bold), color = p.onBlack)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.blackRaised))
        Spacer(Modifier.height(10.dp))
        Text("به", style = v3Style(12.sp), color = p.onBlackMuted)
        if (live) {
          Text(c.landmark.ifBlank { "—" }, style = v3Style(16.sp, FontWeight.Bold), color = p.onBlack)
          if (c.addressText.isNotBlank()) Text(c.addressText, style = v3Style(13.sp), color = p.onBlackMuted)
        } else {
          Text("نشانی پس از پایانِ کار دیگر نشان داده نمی‌شود", style = v3Style(13.sp), color = p.onBlackMuted)
        }
      }
    }
    if (live && c.lat != null && c.lng != null) {
      Spacer(Modifier.height(14.dp))
      V3SecondaryButton("مسیریابی", onNavigate, Modifier.fillMaxWidth(), icon = Icons.Outlined.Directions, onDark = true)
    }
  }
}

@Composable
private fun TrackTab(c: MissionCard, queue: QueueState, waiting: Boolean, live: Boolean, seen: MutableSet<String>) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  Row(verticalAlignment = Alignment.Bottom) {
    Column(Modifier.weight(1f)) {
      Text("شمارهٔ سفارش", style = v3Style(12.sp), color = p.onBlackMuted)
      Text("#${c.orderNo.fa()}", style = v3Style(22.sp, FontWeight.Bold), color = p.onBlack)
    }
    if (waiting) { TonePill("در صف", dark.awaiting); Spacer(Modifier.width(6.dp)) }
    TonePill(STATUS_LABEL[c.status] ?: c.status, statusTone(c.status, dark))
  }
  if (live && reached(c.status) >= 0) {
    Spacer(Modifier.height(14.dp))
    StepTrack(c.status, onDark = true)
  }
  if (c.recallRequested && live) {
    Spacer(Modifier.height(12.dp))
    WarnNote("دکان این سفارش را پس خواسته است — بسته را برگردانید.")
  }
  Spacer(Modifier.height(18.dp))
  val rows = CourierBoardModel.track(c, queue)
  if (rows.isEmpty()) {
    Text("خطِ زمانی پس از همگام‌سازی با دکان می‌آید.", style = v3Style(13.sp), color = p.onBlackMuted)
  }
  rows.forEachIndexed { i, r ->
    val appear = rememberAppear("t-${c.id}-${r.at}-$i", i, seen)
    TrackRowView(r, last = i == rows.lastIndex, modifier = Modifier.graphicsLayer {
      alpha = appear.value
      translationY = (1f - appear.value) * 16.dp.toPx()
    })
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
        style = v3Style(15.sp, if (r.current) FontWeight.Bold else FontWeight.Medium),
        color = if (r.current) p.onBlack else p.onBlack.copy(alpha = 0.82f),
      )
      val sub = trackSub(r)
      if (sub.isNotBlank()) Text(sub, style = v3Style(12.sp), color = p.onBlackMuted)
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(clock(r.at), style = v3Style(13.sp, FontWeight.Medium), color = p.onBlack)
      Text(formatMillis(r.at), style = v3Style(11.sp), color = p.onBlackMuted)
    }
  }
}

@Composable
private fun ShipTab(c: MissionCard, live: Boolean) {
  val p = PhoneV3.colors
  val dark = PhoneV3UiDark
  val rows = buildList {
    add(Triple("تعدادِ قلم", if (c.itemsCount > 0) c.itemsCount.fa() else "—", false))
    if (c.expectedCollectMinor > 0) add(Triple(if (live) "نقدِ گرفتنی" else "نقدِ سفارش", afn(c.expectedCollectMinor), true))
    else add(Triple("نقد", "از مشتری گرفته نمی‌شود", false))
    c.cashCollectedMinor?.let { add(Triple("گرفته شد", afn(it), true)) }
    if (c.fareMinor > 0) add(Triple("کرایهٔ شما", afn(c.fareMinor), false))
    add(Triple("کدِ تحویل", if (c.requiresCode) "از مشتری بپرسید" else "لازم نیست", false))
    add(Triple("عکسِ بسته", if (c.photoAllowed) "مشتری رضایت داده" else "بی عکس", false))
    if (live) {
      if (c.customerName.isNotBlank()) add(Triple("مشتری", c.customerName, false))
      val note = listOfNotNull(
        c.leaveWith.ifBlank { null }?.let { "سپردن به: $it" },
        if (c.callBeforeArrival) "پیش از رسیدن خبر دهید" else null,
      ).joinToString(" · ")
      add(Triple("یادداشتِ تحویل", note.ifBlank { "—" }, false))
    }
  }
  rows.forEach { (label, value, strong) -> DarkKV(label, value, strong) }
  if (live && c.phoneMasked.isNotBlank()) DarkKV("شماره (پوشیده)", c.phoneMasked, false, ltr = true)
  if (!live) {
    Spacer(Modifier.height(10.dp))
    Text("مشتری و نشانی پس از پایانِ کار از این گوشی پاک شد.", style = v3Style(13.sp), color = p.onBlackMuted)
  }
  if (c.status == "failed") {
    Spacer(Modifier.height(10.dp))
    Text(
      "اگر دوباره نمی‌روید، بسته را به دکان برگردانید؛ برگشت را دکان هنگامِ تحویل گرفتن ثبت می‌کند.",
      style = v3Style(13.sp), color = dark.awaiting.fg,
    )
  }
}

@Composable
private fun DarkKV(label: String, value: String, strong: Boolean, ltr: Boolean = false) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = v3Style(14.sp), color = p.onBlackMuted)
    //  مقدار ته ردیف (مثلِ «Shipping Detail» ِ مرجع)؛ متنِ بلند دوخطی می‌شود
    Spacer(Modifier.weight(1f).widthIn(min = 12.dp))
    Text(
      value,
      style = v3Style(if (strong) 16.sp else 14.sp, if (strong) FontWeight.Bold else FontWeight.Medium)
        .copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
      color = p.onBlack, textAlign = TextAlign.End,
      modifier = Modifier.widthIn(max = 220.dp),
    )
  }
  Box(Modifier.fillMaxWidth().height(1.dp).background(p.blackRaised))
}

/** دکمه‌های پله ته برگهٔ تیره — اصلی سبزآبی، بقیه روی سطحِ برآمده */
@Composable
private fun ActionArea(c: MissionCard, actions: List<String>, onAction: (String) -> Unit, onPhoto: () -> Unit) {
  val positive = actions.filterNot { isNegative(it) }
  val negative = actions.filter { isNegative(it) }
  val photo = c.photoAllowed && c.status == "arrived"
  Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    positive.firstOrNull()?.let { to ->
      V3PrimaryButton(actionLabel(c.status, to), { onAction(to) }, Modifier.fillMaxWidth(), onDark = true)
    }
    val rest = positive.drop(1)
    if (rest.isNotEmpty() || photo || negative.isNotEmpty()) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rest.forEach { to -> V3SecondaryButton(actionLabel(c.status, to), { onAction(to) }, Modifier.weight(1f), onDark = true) }
        if (photo) V3SecondaryButton("عکسِ بسته", onPhoto, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera, onDark = true)
        negative.forEach { to ->
          V3SecondaryButton(actionLabel(c.status, to), { onAction(to) }, Modifier.weight(1f), tone = ButtonTone.NEGATIVE, onDark = true)
        }
      }
    }
  }
}
