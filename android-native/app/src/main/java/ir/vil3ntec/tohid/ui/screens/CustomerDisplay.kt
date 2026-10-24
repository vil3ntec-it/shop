package ir.vil3ntec.tohid.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.hw.CustomerFacing
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Space

/**
 *  نمایشگرِ مشتری (HW-10، POS-30) — پنجرهٔ دومِ کامپیوتر رو به مشتری.
 *
 *  فقط چیزی که مشتری باید ببیند: نام، تعداد و مبلغِ هر قلم و جمعِ درشت.
 *  پس از فروش «سپاس» و مبلغ، تا نخستین کالای سبدِ بعدی.
 */
@Composable
fun CustomerDisplayScreen(storeName: String) {
  val v by CustomerFacing.view
  val c = Shop.colors
  Column(Modifier.fillMaxSize().background(c.bg).padding(Space.xl)) {
    Text(storeName.ifBlank { "فروشگاه" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = c.text)
    Spacer(Modifier.height(Space.md))
    Box(Modifier.weight(1f).fillMaxWidth().clip(Shape.cardLarge).background(c.surfaceSolid).padding(Space.lg)) {
      when {
        v.paid != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
          Text("سپاس از خرید شما", style = MaterialTheme.typography.headlineMedium, color = c.text, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(Space.sm))
          Text("${money(v.paid ?: 0.0)} ؋", fontSize = 44.sp, color = c.primary, fontWeight = FontWeight.Bold)
        }
        v.lines.isEmpty() -> Text("خوش آمدید", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium, color = c.muted)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
          items(v.lines) { l ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${qty(l.quantity)}${if (l.unit.isNotBlank()) " ${l.unit}" else ""}", style = MaterialTheme.typography.bodyLarge, color = c.muted)
              Spacer(Modifier.padding(horizontal = Space.sm))
              Text(money(l.total), style = MaterialTheme.typography.titleMedium, color = c.text, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
    Spacer(Modifier.height(Space.md))
    Row(
      Modifier.fillMaxWidth().clip(Shape.cardLarge).background(c.primaryTint).padding(horizontal = Space.lg, vertical = Space.md),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("جمع", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = c.text)
      Text("${money(if (v.paid != null) v.paid ?: 0.0 else v.total)} ؋", fontSize = 40.sp, color = c.primary, fontWeight = FontWeight.Bold)
    }
  }
}
