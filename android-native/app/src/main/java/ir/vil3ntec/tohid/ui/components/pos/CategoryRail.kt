package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape
import ir.vil3ntec.tohid.core.i18n.Str

/** پهنای ستونِ دسته‌ها */
val CategoryRailWidth: Dp = 104.dp

/**
 *  ستونِ دسته‌ها (VISUAL-DIRECTION §۲، سمتِ راست در RTL): «همه» و دسته‌های
 *  واقعیِ دکان با شمارِ کالا. کاشیِ فعال حاشیه و رنگِ اصلی دارد. هر کاشی
 *  دکمهٔ واقعی است: با Tab می‌رسد و با Enter یا Space انتخاب می‌شود.
 */
@Composable
fun CategoryRail(
  categories: List<PosCategory>,
  selected: String?,
  onSelect: (String?) -> Unit,
  target: Dp,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier.width(CategoryRailWidth).fillMaxHeight(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(categories, key = { it.name ?: "\u0000all" }) { c ->
      CategoryTile(c, on = c.name == selected, target = target) { onSelect(c.name) }
    }
  }
}

@Composable
private fun CategoryTile(c: PosCategory, on: Boolean, target: Dp, onClick: () -> Unit) {
  Surface(
    selected = on,
    onClick = onClick,
    shape = ShopShape.card,
    color = if (on) Shop.colors.primaryTint else Shop.colors.surfaceSolid,
    contentColor = if (on) Shop.colors.primaryDark else Shop.colors.text,
    border = BorderStroke(if (on) 2.dp else 1.dp, if (on) Shop.colors.primary else Shop.colors.border),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = maxOf(target, 72.dp))
      .semantics {
        role = Role.Tab
        contentDescription = Str.categoryA11y(c.label, c.count)
      },
  ) {
    Column(
      Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Surface(
        shape = CircleShape,
        color = if (on) Shop.colors.surfaceSolid else Shop.colors.surface2,
        contentColor = if (on) Shop.colors.primaryDark else Shop.colors.muted,
        modifier = Modifier.size(30.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (c.name == null) Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp))
          else Text(c.label.trim().take(1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
      }
      Spacer(Modifier.height(4.dp))
      Text(
        c.label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
      )
      Text(
        qty(c.count.toDouble()),
        style = MaterialTheme.typography.labelSmall,
        color = if (on) Shop.colors.primaryDark else Shop.colors.muted,
      )
    }
  }
}
