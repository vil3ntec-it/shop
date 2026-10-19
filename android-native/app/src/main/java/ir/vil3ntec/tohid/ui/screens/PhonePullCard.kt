package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3

/**
 *  کارتِ سیاهِ کششی — r6، آینه‌شده برای راست‌به‌چپ.
 *
 *  آواتار و زنگ در دو گوشهٔ بالا، نامِ دکان با ˅، عددِ درشتِ نقدِ دخل، خطِ خاکستریِ
 *  سودِ امروز، سه کار (قرصِ «دریافت»، دایرهٔ + برای فروشِ تازه، قرصِ «پرداخت») و
 *  تراشهٔ حالِ همگام‌سازی. با فنر و کمی بزرگ‌شدن از ۰٫۹۶ پایین می‌آید و با لغزش به
 *  بالا و محو شدن می‌رود (`progress`)؛ کشیدنش به بالا هم می‌بندد.
 */
@Composable
internal fun PullCardView(
  view: PhoneHomeView,
  progress: Float,
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
      ShopAvatar(view.shopName, 44.dp, ring = p.onBlack.copy(alpha = 0.9f), modifier = Modifier.clickable(role = Role.Button) { onOpen("profile") })
      Spacer(Modifier.weight(1f))
      Box(
        Modifier.size(40.dp).clip(CircleShape).border(1.dp, p.onBlack.copy(alpha = 0.22f), CircleShape)
          .clickable(role = Role.Button, onClickLabel = "هشدارها") { onDismiss() },
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Notifications, contentDescription = "هشدارها", tint = p.onBlack, modifier = Modifier.size(20.dp))
        if (view.alerts.isNotEmpty()) Box(
          Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 10.dp).size(7.dp).clip(CircleShape).background(p.negative),
        )
      }
    }
    Spacer(Modifier.height(30.dp))
    //  نامِ دکان با ˅ — حساب و دکان از صفحهٔ «حساب من» عوض می‌شود
    Row(
      Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onOpen("profile") }.padding(horizontal = 8.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        view.shopName, color = p.onBlack, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      )
      Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = p.onBlack, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.height(4.dp))
    Text(
      if (view.shiftOpen) "نقدِ دخل" else "نقدِ امروز · صندوق بسته",
      color = p.onBlackMuted,
      style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
    )
    val amount = "${money(view.cash)} ؋"
    Text(
      amount,
      color = p.onBlack,
      maxLines = 1,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.displaySmall.copy(
        fontSize = if (amount.length > 12) 36.sp else 46.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 56.sp,
        brush = Brush.horizontalGradient(listOf(p.onBlack, p.onBlackMuted)),
      ),
    )
    Spacer(Modifier.height(2.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        view.profit?.let { (if (it < 0) "ضررِ امروز " else "سودِ امروز ") + "${money(kotlin.math.abs(it))} ؋" }
          ?: "${view.todayCount} فاکتورِ امروز",
        color = p.onBlackMuted,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
      )
      Spacer(Modifier.width(6.dp))
      Icon(Icons.Outlined.Autorenew, contentDescription = null, tint = p.onBlackMuted, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.height(44.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
      DarkPill("دریافت", Icons.Outlined.SouthWest) { onOpen("debtors") }
      PressBox(Modifier.size(58.dp), scaleTo = 0.9f, label = "فروشِ تازه", onClick = { onOpen("sale") }) {
        Box(
          Modifier.size(58.dp).clip(CircleShape).background(p.blackRaised).border(1.dp, p.onBlack.copy(alpha = 0.18f), CircleShape),
          contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = p.onBlack, modifier = Modifier.size(32.dp)) }
      }
      DarkPill("پرداخت", Icons.Outlined.NorthEast) { onOpen("expenses") }
    }
    Spacer(Modifier.height(40.dp))
    SyncChip(view)
  }
}

@Composable
private fun DarkPill(label: String, icon: ImageVector, onClick: () -> Unit) {
  val p = PhoneV3.colors
  PressBox(scaleTo = 0.94f, label = label, onClick = onClick) {
    Row(
      Modifier.height(42.dp).clip(RoundedCornerShape(percent = 50)).background(p.blackRaised)
        .border(1.dp, p.onBlack.copy(alpha = 0.14f), RoundedCornerShape(percent = 50)).padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = p.onBlack, modifier = Modifier.size(17.dp))
      Spacer(Modifier.width(8.dp))
      Text(label, color = p.onBlack, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
  }
}

/** «● همگام‌شده» / «● در انتظارِ همگام‌سازی» — حالِ واقعیِ همگام‌سازی */
@Composable
private fun SyncChip(view: PhoneHomeView) {
  val p = PhoneV3.colors
  val (dot, text) = when (view.sync) {
    HomeAlerts.Sync.OK -> p.toggleOn to "همگام‌شده"
    HomeAlerts.Sync.QUEUED -> p.cart to "در انتظارِ همگام‌سازی" + if (view.queued > 0) " (${view.queued})" else ""
    HomeAlerts.Sync.OFFLINE -> p.onBlackMuted to "بی اینترنت" + if (view.queued > 0) " · ${view.queued} در صف" else ""
    HomeAlerts.Sync.ERROR -> p.negative to "خطای همگام‌سازی"
    HomeAlerts.Sync.LOCAL_ONLY -> p.onBlackMuted to "فقط همین دستگاه · بی حساب"
  }
  Row(
    Modifier.height(30.dp).clip(RoundedCornerShape(percent = 50)).background(p.blackRaised)
      .border(1.dp, p.onBlack.copy(alpha = 0.12f), RoundedCornerShape(percent = 50)).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(7.dp))
    Text(text, color = p.onBlack, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
  }
}
