package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.sync.SyncBadge
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay

/**
 *  نوارِ وضعیتِ پایینِ پنجره — فقط برنامهٔ کامپیوتر.
 *
 *  همان نواری که هر نرم‌افزارِ رومیزی دارد: یک نگاه و همه‌چیز معلوم —
 *  همگام‌سازی، صندوق، فروشِ امروز، شمارِ کالا و قرض‌دار، تاریخِ شمسی،
 *  ساعت و نسخه. هر خانه که کاری دارد، با کلیک صفحه‌اش را باز می‌کند.
 *  ساعت هر ۳۰ ثانیه تازه می‌شود؛ بقیه فقط با عوض شدنِ دفتر.
 */
@Composable
fun DeskStatusBar(d: ShopData) {
  val context = LocalContext.current
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  val sync by SyncV1Engine.of(context).status.collectAsState()
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
  val today = ir.vil3ntec.tohid.todayIso()
  val todaySales = remember(d, today) { d.sales.filter { it.status != "cancelled" && it.date == today } }
  val shift = remember(d) { CashShift.current(d) }

  val colors = Shop.colors
  Row(
    //  نسخهٔ ۲: نوارِ باریکِ کم‌کنتراست، هم‌رنگِ ناحیهٔ منو و بی خطِ جداکننده
    Modifier.fillMaxWidth().height(26.dp).background(colors.zoneNav)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val (dot, head) = when {
      !signedIn -> colors.muted2 to "بی حساب — فقط همین کامپیوتر"
      sync.dot == SyncDot.GREEN -> colors.success to (if (sync.live) "همگام، زنده" else "همگام با سرور")
      sync.dot == SyncDot.YELLOW -> colors.warning to "در صفِ ارسال"
      sync.dot == SyncDot.RED -> colors.danger to "همگام‌سازی خطا دارد"
      else -> colors.muted2 to "بی اینترنت — کارها نگه داشته می‌شوند"
    }
    //  SYN-08: شمارِ منتظر، آخرین همگام‌سازیِ موفق و خطای آخر همیشه پیداست
    val text = if (!signedIn) head else SyncBadge.line(head, sync.queued, sync.lastOkAt, sync.lastError, now)
    Cell(onClick = { AppNav.go("devices") }) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
      Spacer(Modifier.width(6.dp))
      Label(text, if (signedIn && sync.dot == SyncDot.RED) colors.danger else colors.muted)
    }
    Sep()
    Cell(onClick = { AppNav.go("sales") }) {
      Label(if (shift != null) "صندوق باز" else "صندوق بسته", if (shift != null) colors.success else colors.muted)
    }
    Sep()
    Cell(onClick = { AppNav.go("sales") }) {
      Label("امروز: ${plain(todaySales.size)} فاکتور · ${money(todaySales.sumOf { it.finalTotal })} ؋")
    }
    Sep()
    Cell(onClick = { AppNav.go("products") }) { Label("${plain(d.products.size)} کالا") }
    Sep()
    Cell(onClick = { AppNav.go("debtors") }) { Label("${plain(d.debtors.size)} قرض‌دار") }
    Spacer(Modifier.weight(1f))
    //  «جست‌وجو Ctrl+K» این‌جا برداشته شد — جست‌وجو فقط یک جا (سربرگ یا کادرِ صندوق)
    Label(ir.vil3ntec.tohid.formatDate(today) + " — " + clock(now))
    Sep()
    Label("نسخهٔ " + ir.vil3ntec.tohid.BuildConfig.VERSION_NAME, colors.muted2)
  }
}

private fun clock(at: Long): String {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@Composable
private fun Cell(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.clip(MaterialTheme.shapes.extraSmall).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun Label(text: String, color: Color = Shop.colors.muted) {
  Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
}

@Composable
private fun Sep() {
  Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(14.dp).background(Shop.colors.border))
}
