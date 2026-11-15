package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.v3.V3Bar
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/** رنگِ هر دسته = سری‌های نمودار به ترتیبِ رنگ‌های همراه (THEME-V2 «نمودارها») */
@Composable
internal fun categoryColor(index: Int): Color = PhoneV3Palette.chartSeries(PhoneV3.colors, index)

/** ستون‌های نمودارِ دسته‌ها در کارتِ خلاصهٔ مصارف (۶۵s) — رنگِ هر دسته همان [categoryColor] ِ برگهٔ «به تفکیک دسته» */
@Composable
internal fun categoryBars(rows: List<Pair<String, Double>>): List<V3Bar> =
  rows.mapIndexed { i, (name, amount) -> V3Bar(name, name, money(amount), amount, categoryColor(i)) }

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
