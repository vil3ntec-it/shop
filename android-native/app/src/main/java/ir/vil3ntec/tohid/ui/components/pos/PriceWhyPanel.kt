package ir.vil3ntec.tohid.ui.components.pos

// i18n: سه‌زبانه

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrWhy
import ir.vil3ntec.tohid.core.pos.PriceWhy
import ir.vil3ntec.tohid.ui.theme.Shop

/** برچسبِ هر گام با زبانِ صفحه */
fun priceWhyLabel(row: PriceWhy.Row): String = when (row.kind) {
  PriceWhy.Kind.LIST -> StrWhy.list
  PriceWhy.Kind.MANUAL -> StrWhy.manual
  PriceWhy.Kind.BASE -> StrWhy.base
  PriceWhy.Kind.PROMO -> StrWhy.promo(row.name)
  PriceWhy.Kind.ROUNDING -> StrWhy.rounding
  PriceWhy.Kind.TOTAL -> StrWhy.total
}

/**
 *  POS-22 — پنلِ «چرا این قیمت؟» بالای کادرِ ویرایشِ ردیف: هر گام یک سطر، مبلغ با [money] ِ صدازننده
 *  (همان قالبِ پولِ صندوق). فقط نمایش است.
 */
@Composable
fun PriceWhyPanel(rows: List<PriceWhy.Row>, money: (Double) -> String) {
  val c = Shop.colors
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.surface).padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(StrWhy.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = c.text)
    Spacer(Modifier.height(2.dp))
    rows.forEach { r ->
      val strong = r.kind == PriceWhy.Kind.TOTAL
      Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
          Text(priceWhyLabel(r), style = MaterialTheme.typography.bodySmall, color = if (strong) c.text else c.muted)
          r.deltaMinor?.takeIf { it != 0L }?.let { delta ->
            Text(
              if (delta < 0) StrWhy.cheaper(money(-delta / 100.0)) else StrWhy.dearer(money(delta / 100.0)),
              style = MaterialTheme.typography.labelSmall,
              color = if (delta < 0) c.warning else c.muted,
            )
          }
        }
        Text(
          (if (r.amountMinor < 0) "−" else "") + money(kotlin.math.abs(r.amountMinor) / 100.0) + " ؋",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
          color = if (r.kind == PriceWhy.Kind.PROMO) c.success else c.text,
        )
      }
    }
  }
}
