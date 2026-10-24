package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Warehouse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.HomeSections
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Stagger

/** رنگِ هر کاشی از پالتِ پشتیبانِ VILL3N — ثابت برای هر بخش، نه به ترتیبِ جا */
internal fun tileHue(id: String): PhoneV3Palette.Hue = when (id) {
  "products", "receipts", "more" -> PhoneV3Palette.Hue.BRAND
  "warehouse", "orders" -> PhoneV3Palette.Hue.ORANGE
  "debtors", "team" -> PhoneV3Palette.Hue.BLUE
  "expenses", "sales" -> PhoneV3Palette.Hue.PURPLE
  else -> PhoneV3Palette.Hue.TEAL
}

/** r7: نشانهٔ پُر و رنگی روی کاشیِ روشن — تنها جای آیکنِ پُرِ رنگی در گوشی */
internal fun tileIcon(id: String): ImageVector = when (id) {
  "products" -> Icons.Rounded.LocalOffer
  "warehouse" -> Icons.Rounded.Warehouse
  "debtors" -> Icons.Rounded.Groups
  "expenses" -> Icons.Rounded.Payments
  "reports" -> Icons.Rounded.Assessment
  "team" -> Icons.Rounded.Badge
  "orders" -> Icons.Rounded.ShoppingBag
  "purchasing" -> Icons.Rounded.LocalShipping
  "sales" -> Icons.Rounded.ReceiptLong
  "receipts" -> Icons.Rounded.Receipt
  else -> Icons.Rounded.Apps
}

/**
 *  شبکهٔ ۴×۲ِ «بخش‌ها» (r7) — کاشیِ روشنِ گرد با نشانهٔ رنگی و نام زیرش؛ ورودِ پلکانی
 *  بارِ اول و فشارِ فنری. هر کاشی مسیرِ نوع‌دارِ بخشِ خودش را باز می‌کند.
 */
@Composable
internal fun HomeTiles(tiles: List<HomeSections.Tile>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val stagger = rememberV3Stagger()
  Column(modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    tiles.chunked(4).forEachIndexed { r, row ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        row.forEachIndexed { c, t ->
          val label = t.label
          Column(Modifier.weight(1f).v3Stagger(stagger, t.id, r * 4 + c), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              //  VILL3N §۶: سطحِ درونی روی برگهٔ سفید با مرزِ دیدنی، نه سفید روی سفید
              Modifier
                .v3Pressable(label = label, scale = 0.9f) { onOpen(t.route) }
                .size(58.dp)
                .clip(RoundedCornerShape(PhoneV3.tileRadius))
                .background(p.tile)
                .border(1.dp, p.hairline, RoundedCornerShape(PhoneV3.tileRadius)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(tileIcon(t.id), contentDescription = null, tint = PhoneV3Palette.glyph(p, tileHue(t.id)), modifier = Modifier.size(28.dp))
              if (t.id == "orders" && OrdersLive.newCount > 0) Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(p.negative),
              )
            }
            Spacer(Modifier.height(8.dp))
            Text(
              label,
              color = p.ink.copy(alpha = 0.78f),
              style = v3Text(13, FontWeight.Medium, 18),
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

/** سرخطِ بخش‌های خانه (۱۷sp، پررنگ) */
@Composable
internal fun HomeTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, modifier = modifier, color = PhoneV3.colors.ink, style = v3Text(17, FontWeight.Bold, 24))
}
