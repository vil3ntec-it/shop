package ir.vil3ntec.tohid.ui.v3.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3HeroCard
import ir.vil3ntec.tohid.ui.v3.V3HeroChip
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.rememberV3Grow
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Status

/** میان‌برهای نمونه: شناسهٔ همان بخش‌ها، با آیکن و رنگِ همراهی که ثبتِ ماژول‌ها می‌دهد */
private val SHORTCUTS = listOf(
  "sale" to "فروش", "receipts" to "دریافت", "warehouse" to "ورودِ کالا", "history" to "تاریخچه",
  "debtors" to "مشتری‌ها", "expenses" to "مصارف", "reports" to "گزارش", "purchasing" to "خرید",
)

/**
 *  نمونهٔ زندهٔ تم (THEME-V2): کادرِ بزرگ، کاشیِ بخش‌ها با آیکن و رنگِ ثبت‌شده، دکمه‌ها، وضعیت‌ها و یک نمودار
 *  که از خطِ پایه رشد می‌کند. تمِ جاری همان `ThemeStore.theme` است؛ عکس‌ها هر سه تم را می‌گیرند.
 */
@Composable
fun ThemeGallery() {
  val c = PhoneV3.colors
  val s = Shop.colors
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text("تمِ «${themeName()}»", style = V3Type.footnote, color = c.inkMuted)
    V3HeroCard(
      title = "دکانِ کریم",
      label = "فروشِ امروز",
      value = "۱۲٬۴۵۰ افغانی",
      caption = "۲۳ فاکتور، ۴ قرض‌دار",
      chips = listOf(
        V3HeroChip("فروشِ تازه", Icons.Outlined.Add) {},
        V3HeroChip("دریافت", Icons.Outlined.Payments) {},
        V3HeroChip("اسکن", Icons.Outlined.QrCodeScanner) {},
      ),
      pages = 3,
      page = 0,
      onBell = {},
      bellDot = true,
    )
    V3Section("میان‌برها") {
      SHORTCUTS.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
          row.forEach { (id, label) ->
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
              V3IconTile(iconOf(id), hue = V3Modules.hueOf(id), size = 46.dp)
              Spacer(Modifier.height(6.dp))
              Text(label, style = V3Type.caption, color = c.ink)
            }
          }
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button("ثبتِ فروش", onClick = {}, modifier = Modifier.weight(1f))
      V3Button("پیش‌نویس", onClick = {}, kind = V3ButtonKind.SECONDARY, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      V3StatusPill(V3Status.DELIVERED, "تحویل شد")
      V3StatusPill(V3Status.AWAITING, "در انتظارِ تأیید")
      V3StatusPill(V3Status.CANCELLED, "لغو")
    }
    V3Card {
      Text("فروشِ هفته", style = V3Type.headline, color = c.ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      val values = listOf(0.45f, 0.62f, 0.38f, 0.85f, 0.55f, 0.7f, 0.5f)
      val grow = rememberV3Grow(ThemeStore.theme)
      Canvas(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp, vertical = 8.dp)) {
        val step = size.width / values.size
        val bar = step * 0.56f
        for (g in 0..2) {
          val y = size.height - g * size.height / 3f
          drawLine(s.chart.grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        values.forEachIndexed { i, v ->
          val h = size.height * v * grow
          val x = size.width - (i + 1) * step + (step - bar) / 2
          val top = Offset(x, size.height - h)
          val sz = Size(bar, h)
          if (i == 3) drawRoundRect(Brush.linearGradient(s.heroGradient, top, Offset(x + bar, size.height)), top, sz, CornerRadius(8.dp.toPx()))
          else drawRoundRect(s.chart.series[i % s.chart.series.size], top, sz, CornerRadius(8.dp.toPx()))
        }
      }
    }
    Box(Modifier.height(24.dp))
  }
}

private fun themeName(): String = when (ThemeStore.theme) {
  ir.vil3ntec.tohid.ui.theme.ShopTheme.SKY -> "آسمان"
  ir.vil3ntec.tohid.ui.theme.ShopTheme.OCEAN -> "اقیانوس"
  ir.vil3ntec.tohid.ui.theme.ShopTheme.DUSK -> "غروب"
}

/** آیکنِ ثبت‌شدهٔ همان بخش در ثبتِ ماژول‌ها (نوار، «بیشتر» یا خانه) */
private fun iconOf(id: String) =
  V3Modules.ALL.asSequence().flatMap { it.entries.asSequence() }.firstOrNull { it.id == id }?.icon ?: Icons.Outlined.Inventory2
