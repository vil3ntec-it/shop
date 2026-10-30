package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3ChipRow
import ir.vil3ntec.tohid.ui.v3.rememberV3Grow
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** رنگِ هر دسته = سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2 «نمودارها») */
@Composable
internal fun categoryColor(index: Int): Color = PhoneV3Palette.chartSeries(PhoneV3.colors, index)

/**
 *  تفکیکِ مصارفِ ماه: یک نوارِ افقیِ چندرنگ (سهمِ هر دسته، با رشد از آغاز) و زیرش تراشهٔ هر دسته
 *  با نقطهٔ همان رنگ و مبلغ — رنگ هرگز تنها نیست. زدنِ تراشه = فیلترِ همان دسته؛ دوباره = همه.
 */
@Composable
internal fun CategoryBreakdown(rows: List<Pair<String, Double>>, selected: String?, onPick: (String) -> Unit) {
  val shares = ExpensesModel.shares(rows)
  val grow = rememberV3Grow(rows)
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  V3Card(padding = PaddingValues(vertical = 14.dp)) {
    Row(
      Modifier
        .padding(horizontal = 14.dp)
        .fillMaxWidth()
        .height(12.dp)
        .graphicsLayer { scaleX = grow; transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f) }
        .clip(RoundedCornerShape(50))
        .background(PhoneV3.colors.tile),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      rows.forEachIndexed { i, _ ->
        val w = shares[i]
        if (w > 0f) {
          Box(
            Modifier
              .weight(w)
              .fillMaxHeight()
              .background(categoryColor(i).copy(alpha = if (selected == null || selected == rows[i].first) 1f else 0.35f)),
          )
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    V3ChipRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
      rows.forEachIndexed { i, (name, amount) ->
        LegendChip(name, money(amount), categoryColor(i), selected == name) { onPick(name) }
      }
    }
  }
}

/** تراشهٔ راهنمای نمودار: نقطهٔ رنگِ سری + نام + مبلغ؛ انتخاب‌شده با مرز و زمینهٔ برند (پیشنهادِ کیت: `Draft`) */
@Composable
private fun LegendChip(name: String, amount: String, dot: Color, selected: Boolean, onClick: () -> Unit) {
  val c = PhoneV3.colors
  val shape = RoundedCornerShape(50)
  Row(
    Modifier
      .v3Pressable(label = name, role = Role.Tab, onClick = onClick)
      .height(36.dp)
      .clip(shape)
      .background(if (selected) PhoneV3Palette.selected(c) else c.card)
      .border(1.dp, if (selected) PhoneV3Roles.focus(c) else PhoneV3Roles.inputBorder(c), shape)
      .semantics { this.selected = selected; contentDescription = "$name: $amount" }
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
    Spacer(Modifier.width(6.dp))
    Text(name, style = v3Text(13, FontWeight.Medium, 18), color = if (selected) PhoneV3Roles.brandText(c) else c.ink, maxLines = 1)
    Spacer(Modifier.width(6.dp))
    Text(amount, style = v3Text(12, FontWeight.SemiBold, 16), color = c.inkMuted, maxLines = 1)
  }
}

/** یک ردیفِ برگهٔ «به تفکیک دسته»: نام، مبلغ و نوارِ نازکِ سهم */
@Composable
internal fun BreakdownRow(name: String, amount: Double, share: Float, color: Color, selected: Boolean, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(PhoneV3.tileRadius))
      .background(if (selected) PhoneV3Palette.selected(c) else c.card)
      .v3Pressable(label = name, onClick = onClick)
      .semantics { this.selected = selected }
      .padding(horizontal = 12.dp, vertical = 9.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(9.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(8.dp))
      Text(name, style = v3Text(14, FontWeight.Medium, 20), color = c.ink, modifier = Modifier.weight(1f))
      Text(MoneyText.afn(amount), style = v3Text(13, FontWeight.SemiBold, 18), color = c.ink)
    }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(c.tile)) {
      Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(50)).background(color))
    }
  }
}
