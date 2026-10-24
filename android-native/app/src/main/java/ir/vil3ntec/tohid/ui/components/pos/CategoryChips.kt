package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

/**
 *  دسته‌ها — یک ردیفِ افقیِ تراشه بالای کاتالوگ (نسخهٔ ۲).
 *
 *  نسخهٔ ۱ ستونِ عمودیِ دوم بود کنارِ منوی کناری (نقدِ صاحب محصول: «دو ستونِ
 *  عمودی، جعبه‌های نارنجی برای چیست؟»). حالا «همه» و دسته‌های واقعیِ دکان با
 *  شمارِ کالای فعال، قرصی؛ تراشهٔ فعال تهِ رنگِ اصلی و متنِ پررنگ دارد. هر تراشه
 *  دکمهٔ واقعی است: با Tab می‌رسد و با Enter یا Space انتخاب می‌شود. ردیف اگر جا
 *  نشود افقی می‌لغزد.
 */
@Composable
fun CategoryChips(
  categories: List<PosCategory>,
  selected: String?,
  onSelect: (String?) -> Unit,
  target: Dp,
  modifier: Modifier = Modifier,
) {
  LazyRow(
    modifier.selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(horizontal = 4.dp),
  ) {
    items(categories, key = { it.name ?: "\u0000all" }) { cat ->
      CategoryChip(cat, on = cat.name == selected, target = target) { onSelect(cat.name) }
    }
  }
}

@Composable
private fun CategoryChip(cat: PosCategory, on: Boolean, target: Dp, onClick: () -> Unit) {
  val c = Shop.colors
  Row(
    Modifier
      .heightIn(min = target)
      .clip(ShopShape.chip)
      .background(if (on) c.primaryTint else c.surface)
      .then(if (on) Modifier.border(1.dp, c.focus, ShopShape.chip) else Modifier.border(1.dp, c.border, ShopShape.chip))
      .selectable(selected = on, role = Role.Tab, onClick = onClick)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      cat.label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
      color = if (on) c.primaryDark else c.text,
      maxLines = 1,
    )
    Spacer(Modifier.width(6.dp))
    Text(
      cat.count.toString(),
      Modifier
        .clip(ShopShape.chip)
        .background(if (on) c.surface else c.surface2)
        .padding(horizontal = 7.dp),
      style = MaterialTheme.typography.labelMedium.tabular(),
      color = if (on) c.primaryDark else c.muted,
      maxLines = 1,
    )
  }
}
