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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ir.vil3ntec.tohid.ui.desk.deskClickable
import ir.vil3ntec.tohid.core.sync.SyncBadge
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncModel
import kotlinx.coroutines.delay
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.Str

/**
 *  نوارِ وضعیتِ پایینِ پنجره — فقط برنامهٔ کامپیوتر.
 *
 *  همان نواری که هر نرم‌افزارِ رومیزی دارد: یک نگاه و همه‌چیز معلوم —
 *  همگام‌سازی، صندوق، فروشِ امروز، شمارِ کالا و قرض‌دار، تاریخِ شمسی،
 *  ساعت و نسخه. هر خانه که کاری دارد، با کلیک صفحه‌اش را باز می‌کند.
 *  ساعت هر ۳۰ ثانیه تازه می‌شود؛ بقیه فقط با عوض شدنِ دفتر.
 *  e-desk-d1: همان خانه‌ها، خواناتر — ۱۳، جداکنندهٔ روشن، نقطهٔ رنگیِ همگام‌سازی (سبز/کهربایی/سرخ/خاکستری) و صندوق.
 */
@Composable
fun DeskStatusBar(d: ShopData) {
  val context = LocalContext.current
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  //  PERF-LEDGER: موتور بیرون از رشتهٔ صفحه ساخته می‌شود (`rememberSyncV1Status`)
  val sync = ir.vil3ntec.tohid.sync.v1.rememberSyncV1Status(context) ?: ir.vil3ntec.tohid.sync.v1.SYNC_STARTING
  val now = ir.vil3ntec.tohid.ui.v3.rememberV3LiveClock(30_000) { System.currentTimeMillis() } // PERF-SCREEN
  val today = ir.vil3ntec.tohid.todayIso()
  val todaySales = remember(d, today) { d.sales.filter { it.status != "cancelled" && it.date == today } }
  val shift = remember(d) { CashShift.current(d) }

  val colors = Shop.colors
  val c = ir.vil3ntec.tohid.ui.desk.Desk.colors
  Row(
    //  e-desk-d1: ۲۸dp، سطحِ پوسته با خطِ مویی بالا، نوشتهٔ ۱۳ (`DeskType.secondary`) و جداکنندهٔ روشن میانِ خانه‌ها
    Modifier.fillMaxWidth().height(ir.vil3ntec.tohid.ui.desk.DeskSize.statusBar).background(c.chrome).topLine(c.hairline)
      .padding(horizontal = ir.vil3ntec.tohid.ui.desk.DeskSpace.x2)
      .semantics(mergeDescendants = false) { contentDescription = ir.vil3ntec.tohid.core.i18n.StrDesk.statusBar },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val (dot, head) = when {
      !signedIn -> c.inkMuted to StrNav.statusNoAccount
      sync.dot == SyncDot.GREEN -> c.positive to (if (sync.live) StrNav.syncLive else StrNav.syncedWithServer)
      //  SYN-12: قطعیِ بی هشدار (حکمِ موتور) برچسبِ خودش را دارد، نه «در صف» — همان `dotHead` ِ وب
      sync.dot == SyncDot.YELLOW -> colors.message.warning.icon to (SyncModel.quietHead(sync.outage) ?: StrNav.syncQueued)
      sync.dot == SyncDot.RED -> c.negative to StrNav.syncError
      else -> c.inkMuted to StrNav.offlineHeld
    }
    //  SYN-08: شمارِ منتظر، آخرین همگام‌سازیِ موفق و خطای آخر همیشه پیداست — خطا فقط به شکلِ ساده (`SyncModel.reason`)
    val text = if (!signedIn) head else SyncBadge.line(head, sync.queued, sync.lastOkAt, SyncModel.reason(sync.lastError), now)
    Cell(onClick = { AppNav.go("devices") }) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
      Spacer(Modifier.width(6.dp))
      Label(text, if (signedIn && sync.dot == SyncDot.RED) c.negative else c.ink)
    }
    Sep()
    Cell(onClick = { AppNav.go("sales") }) {
      Box(Modifier.size(6.dp).clip(CircleShape).background(if (shift != null) c.positive else c.inkMuted))
      Spacer(Modifier.width(6.dp))
      Label(if (shift != null) StrNav.registerOpen else StrNav.registerClosed, if (shift != null) c.positive else c.inkSecondary)
    }
    Sep()
    Cell(onClick = { AppNav.go("sales") }) {
      Label(StrNav.todaySummary(plain(todaySales.size), money(todaySales.sumOf { it.finalTotal })))
    }
    Sep()
    Cell(onClick = { AppNav.go("products") }) { Label(Str.itemsCount(plain(d.products.size))) }
    Sep()
    Cell(onClick = { AppNav.go("debtors") }) { Label(Str.debtorsCount(plain(d.debtors.size))) }
    Spacer(Modifier.weight(1f))
    //  «جست‌وجو Ctrl+K» این‌جا برداشته شد — جست‌وجو فقط یک جا (سربرگ یا کادرِ صندوق)
    Label(ir.vil3ntec.tohid.formatDate(today) + " — " + clock(now), modifier = Modifier.padding(horizontal = 6.dp))
    Sep()
    Label(Str.version(ir.vil3ntec.tohid.BuildConfig.VERSION_NAME), c.inkSecondary, Modifier.padding(horizontal = 6.dp))
  }
}

private fun clock(at: Long): String {
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  return "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
}

@Composable
private fun Cell(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.height(22.dp).deskClickable(onClick = onClick).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun Label(text: String, color: Color = ir.vil3ntec.tohid.ui.desk.Desk.colors.inkSecondary, modifier: Modifier = Modifier) {
  Text(text, modifier, style = ir.vil3ntec.tohid.ui.desk.DeskType.secondary, color = color, maxLines = 1)
}

@Composable
private fun Sep() {
  Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(14.dp).background(ir.vil3ntec.tohid.ui.desk.Desk.colors.hairline))
}

/** خطِ مویی بالای نوار */
private fun Modifier.topLine(color: Color): Modifier = drawBehind {
  drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0.5f), androidx.compose.ui.geometry.Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
}
