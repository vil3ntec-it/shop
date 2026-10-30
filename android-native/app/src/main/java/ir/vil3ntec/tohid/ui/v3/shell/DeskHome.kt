package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** پهنای بیشترِ محتوای خانهٔ کامپیوتر — روی نمایشگرِ پهن وسط می‌نشیند، کش نمی‌آید */
private val DESK_MAX = 1080.dp

/** فاصلهٔ میانِ بلوک‌ها (HOME-V2 §۳) */
private val DESK_BLOCK = 24.dp

/**
 *  خانهٔ کامپیوتر (`home` با `LocalDeskLayout`) — همان روحِ HOME-V2: ساده و هم‌خانوادهٔ
 *  `V3HeroCard`. جای داشبوردِ پنلیِ قدیم (پنج کارتِ عدد، نمودارِ بزرگ، کارهای سریع، چهار
 *  پنلِ هشدار). سرخط و جست‌وجو و زنگ را پوستهٔ کامپیوتر دارد؛ این‌جا فقط:
 *
 *  1. دو کادرِ بزرگ کنارِ هم: «فروشِ امروز» (مقایسه با همین ساعتِ دیروز و خطِ ۷ روز، زدن ⇐
 *     گزارش‌ها) و «صندوق / گاوصندوق» (روی کامپیوتر جا هست، پنهان نیست؛ همان ورق‌خور).
 *  2. چهار میان‌بر با رنگِ همراهِ هر کدام.
 *  3. «امروز» (۵ ردیف) و کنارش «کارهای فوری» فقط وقتی کاری فوری هست — وگرنه «امروز» تمام‌پهنا.
 */
@Composable
fun DeskHomeScreen(d: ShopData, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  val view = rememberHomeView(d)
  val today = ir.vil3ntec.tohid.todayIso()
  val trend = remember(d, today) { HomeTrend.of(d.sales, today, System.currentTimeMillis()) }
  val drawer = rememberPagerState { DrawerPages.COUNT }
  val stagger = rememberV3Stagger()
  val pressing = HomeAlerts.pressing(view.alerts)
  Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.TopCenter) {
    Column(
      Modifier
        .widthIn(max = DESK_MAX)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeSalesCard(
          view.todaySales, trend,
          onStats = if (view.canSeeMoney) ({ onOpen(AnalyticsPaths.REPORTS) }) else null,
          modifier = Modifier.weight(1f).v3Stagger(stagger, "sales", 0),
          inset = 0.dp,
        )
        HomeDrawerPager(view, drawer, onOpen, Modifier.weight(1f).v3Stagger(stagger, "drawer", 1), inset = 0.dp)
      }
      Spacer(Modifier.height(DESK_BLOCK))
      HomeShortcuts(onOpen, Modifier.widthIn(max = 640.dp).v3Stagger(stagger, "shortcuts", 2))
      Spacer(Modifier.height(DESK_BLOCK))
      if (pressing.isEmpty()) {
        HomeToday(d, purchases = view.canSeeMoney, onOpen = onOpen, modifier = Modifier.v3Stagger(stagger, "today", 3), inset = 0.dp)
      } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        HomeToday(d, purchases = view.canSeeMoney, onOpen = onOpen, modifier = Modifier.weight(1.4f).v3Stagger(stagger, "today", 3), inset = 0.dp)
        DeskUrgent(pressing, onOpen, Modifier.weight(1f).v3Stagger(stagger, "urgent", 4))
      }
    }
  }
}

/** «کارهای فوری» ِ کامپیوتر: هر کارِ فوری یک کارتِ یک‌خطی (مقصدِ خودش) و «همه» ⇐ مرکزِ هشدارها */
@Composable
private fun DeskUrgent(alerts: List<HomeAlerts.Alert>, onOpen: (String) -> Unit, modifier: Modifier) {
  val c = PhoneV3.colors
  Column(modifier) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      HomeTitle(StrHome.urgentNow, Modifier.weight(1f))
      Text(
        Str.all,
        color = PhoneV3Roles.brandText(c),
        style = v3Text(15, FontWeight.Bold, 22),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).v3Pressable(label = StrHome.urgentNow) { onOpen(ShellTargets.notifications()) }
          .padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      alerts.take(DESK_URGENT_MAX).forEach { HomeUrgent(listOf(it), onOpen, inset = 0.dp) }
    }
  }
}

/** بیش از این، «همه» */
private const val DESK_URGENT_MAX = 4
