package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.PagerDots
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.rememberCountUp
import ir.vil3ntec.tohid.ui.v3.v3PagerItem
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  کارتِ سیاهِ کششی — r6، آینه‌شده برای راست‌به‌چپ؛ رفتارِ کشش همان `PullCard`.
 *
 *  آواتار (⇐ «حساب من») و زنگ (⇐ مرکزِ هشدارها) در دو گوشه، نامِ دکان با ˅ (عوض کردنِ
 *  دکان، همان «حساب من»)، و دو رویِ ورق‌خور (owner-feedback-3 B): «صندوق» = نقدِ دخل همین
 *  حالا با شمارندهٔ عدد و سودِ امروز (فقط برای کسی که می‌بیند)، «گاوصندوق» = `HomeSafeFace`؛
 *  سه کار — «دریافت» ⇐ فرمِ دریافت، + ⇐ فروشِ تازه، «پرداخت» ⇐ فرمِ مصرفِ تازه — و تراشهٔ
 *  حالِ همگام‌سازی ⇐ صفحهٔ همگام‌سازی. با فنر پایین می‌آید (`progress`) و کشیدن به بالا می‌بندد.
 */
@Composable
internal fun HomePullCard(
  view: HomeView,
  progress: Float,
  initialFace: Int = 0,
  onHeight: (Float) -> Unit,
  onDismiss: () -> Unit,
  onOpen: (String) -> Unit,
) {
  val p = PhoneV3.colors
  val shape = RoundedCornerShape(PhoneV3.sheetRadius)
  Column(
    Modifier
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .fillMaxWidth()
      .onSizeChanged { onHeight(it.height.toFloat()) }
      .graphicsLayer {
        translationY = -(1f - progress) * (size.height + 16.dp.toPx())
        val s = 0.96f + 0.04f * progress
        scaleX = s; scaleY = s
        alpha = (progress * 1.6f).coerceAtMost(1f)
      }
      .clip(shape)
      .background(Brush.verticalGradient(listOf(p.blackRaised, p.black)))
      //  لمس روی خودِ کارت به «بیرون» نمی‌رسد؛ کشیدن به بالا = بستن
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          var dy = 0f
          while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            dy += c.position.y - c.previousPosition.y
            if (!c.pressed) break
            if (dy < -32f) { c.consume(); onDismiss(); break }
          }
        }
      }
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 22.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      ShopLetter(view.shopName, 44.dp, ring = p.onBlack.copy(alpha = 0.9f)) { onOpen(ShellTargets.PROFILE) }
      Spacer(Modifier.weight(1f))
      Box(
        Modifier.v3Pressable(label = StrKit.notifications, scale = 0.92f) { onOpen(ShellTargets.notifications()) }
          .size(40.dp).clip(CircleShape).border(1.dp, p.onBlack.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Notifications, contentDescription = StrKit.notifications, tint = p.onBlack, modifier = Modifier.size(20.dp))
        if (view.bellDot) Box(Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(7.dp).clip(CircleShape).background(p.negative))
      }
    }
    Spacer(Modifier.height(30.dp))
    Row(
      Modifier.clip(RoundedCornerShape(10.dp)).v3Pressable(label = view.shopName) { onOpen(ShellTargets.PROFILE) }.padding(horizontal = 8.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(view.shopName, color = p.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false), style = v3Text(17, FontWeight.Medium, 24))
      Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = p.onBlack, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.height(4.dp))
    //  owner-feedback-3 B: کارت ورق می‌خورد — ۱ «صندوق» (نقدِ دخل همین حالا)، ۲ «گاوصندوق»
    val faces = rememberPagerState(initialPage = initialFace) { 2 }
    HorizontalPager(
      state = faces,
      modifier = Modifier.fillMaxWidth(),
      flingBehavior = PagerDefaults.flingBehavior(faces, snapAnimationSpec = MotionSpec.sheet()),
    ) { page ->
      //  روی کناری کمی کوچک و کم‌رنگ؛ با کشیدن پا‌به‌پای انگشت بزرگ و پررنگ می‌شود
      Box(Modifier.v3PagerItem(faces, page)) {
        if (page == 0) {
          val profit = view.profit
          PullFace(
            label = StrHome.drawer + " · " + if (view.shiftOpen) StrHome.drawerCash else StrHome.todayCashClosed,
            amount = view.cash,
            line = when {
              profit == null -> StrHome.invoicesToday(view.todayCount)
              profit < 0 -> StrHome.todayLoss + " " + "${money(kotlin.math.abs(profit))} ؋"
              else -> StrHome.todayProfit + " " + "${money(profit)} ؋"
            },
          )
        } else HomeSafeFace()
      }
    }
    Spacer(Modifier.height(10.dp))
    PagerDots(2, faces.currentPage, p.onBlack, p.onBlack.copy(alpha = 0.3f))
    Spacer(Modifier.height(30.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      DarkPill(StrHome.receive, Icons.Outlined.SouthWest) { onOpen(ShellTargets.RECEIVE) }
      Box(
        Modifier.v3Pressable(label = StrHome.newSale, scale = 0.9f, haptic = true) { onOpen(ShellTargets.NEW_SALE) }
          .size(58.dp).clip(CircleShape).background(p.blackRaised).border(1.dp, p.onBlack.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
      ) { Icon(Icons.Rounded.Add, contentDescription = StrHome.newSale, tint = p.onBlack, modifier = Modifier.size(32.dp)) }
      DarkPill(StrHome.pay, Icons.Outlined.NorthEast) { onOpen(ShellTargets.PAY) }
    }
    Spacer(Modifier.height(40.dp))
    SyncChip(view) { onOpen(ShellTargets.SYNC) }
  }
}

/**
 *  یک رویِ کارت: برچسب، عددِ درشت با شمارنده (یا «—» وقتی عددی نیست) و خطِ خاکستریِ زیرش.
 *  «صندوق» و «گاوصندوق» هر دو همین شکل را دارند تا ورق خوردن بی‌پرش باشد.
 */
@Composable
internal fun PullFace(label: String, amount: Double?, line: String, live: Boolean = true) {
  val p = PhoneV3.colors
  Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = p.onBlackMuted, style = v3Text(12, FontWeight.Medium, 17), maxLines = 1, overflow = TextOverflow.Ellipsis)
    val shown = rememberCountUp(amount ?: 0.0)
    val final = amount?.let { "${money(it)} ؋" } ?: "—"
    Text(
      amount?.let { "${money(shown)} ؋" } ?: final,
      color = p.onBlack,
      maxLines = 1,
      textAlign = TextAlign.Center,
      style = v3Text(if (final.length > 12) 36 else 46, FontWeight.Bold, 56).copy(brush = Brush.horizontalGradient(listOf(p.onBlack, p.onBlackMuted))),
    )
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(line, color = p.onBlackMuted, style = v3Text(15, FontWeight.Medium, 22), maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
      //  عددِ زنده (از دفتر، با هر تغییر تازه) — جای‌نگهدار نشانش را ندارد
      if (live) {
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = p.onBlackMuted, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun DarkPill(label: String, icon: ImageVector, onClick: () -> Unit) {
  val p = PhoneV3.colors
  val pill = RoundedCornerShape(percent = 50)
  Row(
    Modifier.v3Pressable(label = label, scale = 0.94f, onClick = onClick)
      .height(42.dp).clip(pill).background(p.blackRaised).border(1.dp, p.onBlack.copy(alpha = 0.14f), pill).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = p.onBlack, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, color = p.onBlack, style = v3Text(13, FontWeight.Medium, 18))
  }
}

/** «● همگام‌شده» / «● در انتظارِ همگام‌سازی» — حالِ واقعیِ همگام‌سازی؛ زدن ⇐ صفحهٔ همگام‌سازی */
@Composable
private fun SyncChip(view: HomeView, onClick: () -> Unit) {
  val p = PhoneV3.colors
  //  رنگِ نقطه از جفت‌های پیامِ §۹ روی سطحِ وارونه (رنگِ روشنِ شب تا روی تیره دیده شود)
  val inv = ir.vil3ntec.tohid.ui.theme.PhoneV3Dark
  val (dot, text) = when (view.sync) {
    HomeAlerts.Sync.OK -> PhoneV3Roles.tone(inv, V3Tone.SUCCESS).fg to StrHome.synced
    HomeAlerts.Sync.QUEUED -> PhoneV3Roles.tone(inv, V3Tone.WARNING).fg to StrHome.syncWaiting + if (view.queued > 0) " (${view.queued})" else ""
    HomeAlerts.Sync.OFFLINE -> p.onBlackMuted to StrHome.offline + if (view.queued > 0) " · " + StrHome.inQueue(view.queued) else ""
    HomeAlerts.Sync.ERROR -> PhoneV3Roles.tone(inv, V3Tone.ERROR).fg to StrHome.syncFailed
    HomeAlerts.Sync.LOCAL_ONLY -> p.onBlackMuted to StrHome.localOnly
  }
  val pill = RoundedCornerShape(percent = 50)
  Row(
    Modifier.v3Pressable(label = text, onClick = onClick)
      .height(30.dp).clip(pill).background(p.blackRaised).border(1.dp, p.onBlack.copy(alpha = 0.12f), pill).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(7.dp))
    Text(text, color = p.onBlack, style = v3Text(12, FontWeight.Medium, 17))
  }
}
