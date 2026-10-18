package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.ProductPhoto
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

/*
 *  شبکه و جدولِ کالای صندوق (VISUAL-DIRECTION §۲ ستونِ وسط و §۱٫۵).
 *  هر دو نما همان فهرست، همان برجسته (پیشنهادِ Enter) و همان − / تعداد / + را
 *  دارند؛ فقط شکل فرق می‌کند. کالای غیرفعال پیش از این‌جا کنار رفته است.
 */

/** هر چیزی که کاشی و ردیف برای یک کالا لازم دارند — از صفحه، بی محاسبهٔ دوباره */
class PosProductCallbacks(
  val stockOf: (String) -> Double,
  val qtyOf: (String) -> Double,
  val onAdd: (Product) -> Unit,
  val onMinus: (Product) -> Unit,
  val onPlus: (Product) -> Unit,
)

/** − / تعداد / + — هدفِ لمس از `PosLayout.minTarget` */
@Composable
fun QtyStepper(
  quantity: Double,
  itemName: String,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  target: Dp,
  unit: String = "",
  plusEnabled: Boolean = true,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    StepButton(Icons.Filled.Remove, "یکی کمتر از $itemName", enabled = quantity > 0, target = target, onClick = onMinus)
    Text(
      if (unit.isNotBlank() && quantity > 0) "${qty(quantity)} $unit" else qty(quantity),
      Modifier.widthIn(min = 28.dp).padding(horizontal = 4.dp),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = if (quantity > 0) FontWeight.SemiBold else FontWeight.Normal,
      color = if (quantity > 0) Shop.colors.text else Shop.colors.muted,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
    StepButton(Icons.Filled.Add, "یکی بیشتر از $itemName", enabled = plusEnabled, target = target, onClick = onPlus)
  }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, target: Dp, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    color = Shop.colors.surfaceSolid,
    contentColor = if (enabled) Shop.colors.primaryDark else Shop.colors.muted2,
    border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) Shop.colors.fieldBorder else Shop.colors.border),
    modifier = Modifier.size(target).semantics { contentDescription = label },
  ) {
    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
  }
}

/** وضعیتِ موجودی: همیشه نقطه + نوشته + رنگ (۱۹.۱۱)، نه رنگِ تنها */
@Composable
fun StockLine(stock: Double, minStock: Double, unit: String, modifier: Modifier = Modifier) {
  val (color, text) = when {
    stock <= 0 -> Shop.colors.danger to "ناموجود"
    minStock > 0 && stock <= minStock -> Shop.colors.warning to "کم · ${qty(stock)}${if (unit.isNotBlank()) " $unit" else ""}"
    else -> Shop.colors.success to "موجودی ${qty(stock)}${if (unit.isNotBlank()) " $unit" else ""}"
  }
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = if (stock <= 0) Shop.colors.danger else Shop.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** کاشیِ کالا: عکسِ کوچک (اگر نمایشِ عکس روشن است)، دسته، نام، موجودی، قیمت و − / تعداد / + */
@Composable
fun ProductTile(
  product: Product,
  stock: Double,
  inCart: Double,
  highlighted: Boolean,
  showPhoto: Boolean,
  target: Dp,
  onAdd: () -> Unit,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val edge = when {
    highlighted -> Shop.colors.primary
    inCart > 0 -> Shop.colors.primary.copy(alpha = 0.45f)
    else -> Shop.colors.border
  }
  Surface(
    color = if (highlighted) Shop.colors.primaryTint else Shop.colors.surfaceSolid,
    shape = ShopShape.card,
    modifier = modifier
      .border(if (highlighted) 2.dp else 1.dp, edge, ShopShape.card)
      .semantics { selected = highlighted },
  ) {
    Column {
      Column(
        Modifier
          .fillMaxWidth()
          .clickable(onClickLabel = "افزودن به سبد", onClick = onAdd)
          .padding(start = 10.dp, end = 10.dp, top = 10.dp),
      ) {
        if (showPhoto) {
          Box(
            Modifier.fillMaxWidth().height(84.dp).clip(ShopShape.card).background(Shop.colors.surface2),
            contentAlignment = Alignment.Center,
          ) { ProductPhoto(productId = product.id, size = 72.dp) }
          Spacer(Modifier.height(8.dp))
        }
        Text(
          product.category.ifBlank { "بی‌دسته" },
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
          product.name,
          style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text,
          minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        StockLine(stock, product.minStock, product.unit)
        Spacer(Modifier.height(6.dp))
      }
      HorizontalDivider(color = Shop.colors.border)
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "${money(product.salePrice)} ؋",
          Modifier.weight(1f),
          style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text, maxLines = 1,
        )
        QtyStepper(inCart, product.name, onMinus = onMinus, onPlus = onPlus, target = target)
      }
    }
  }
}

/** نمای کارتی */
@Composable
fun ProductGrid(
  items: List<Product>,
  highlight: Int,
  showPhotos: Boolean,
  target: Dp,
  cb: PosProductCallbacks,
  state: LazyGridState,
  modifier: Modifier = Modifier,
) {
  //  برجسته (پیشنهادِ Enter) دیده می‌ماند؛ فهرستِ تازه بی برجسته از سر
  LaunchedEffect(highlight, items) {
    runCatching { if (highlight in items.indices) state.animateScrollToItem(highlight) else state.scrollToItem(0) }
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = if (target >= 48.dp) 196.dp else 176.dp),
    state = state,
    modifier = modifier,
    contentPadding = PaddingValues(bottom = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
      ProductTile(
        product = p,
        stock = cb.stockOf(p.id),
        inCart = cb.qtyOf(p.id),
        highlighted = i == highlight,
        showPhoto = showPhotos,
        target = target,
        onAdd = { cb.onAdd(p) },
        onMinus = { cb.onMinus(p) },
        onPlus = { cb.onPlus(p) },
      )
    }
  }
}

/** نمای جدولی: ردیفِ فشرده — نام، کد، قیمت، موجودی و − / تعداد / + */
@Composable
fun ProductTable(
  items: List<Product>,
  highlight: Int,
  target: Dp,
  cb: PosProductCallbacks,
  state: LazyListState,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(highlight, items) {
    runCatching { if (highlight in items.indices) state.animateScrollToItem(highlight) else state.scrollToItem(0) }
  }
  val head = MaterialTheme.typography.labelMedium
  Column(modifier.border(1.dp, Shop.colors.border, ShopShape.card).clip(ShopShape.card)) {
    Row(
      Modifier.fillMaxWidth().background(Shop.colors.surface2).padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("کالا", Modifier.weight(1f), style = head, color = Shop.colors.muted)
      Text("کد", Modifier.width(130.dp), style = head, color = Shop.colors.muted)
      Text("قیمت", Modifier.width(110.dp), style = head, color = Shop.colors.muted, textAlign = TextAlign.End)
      Spacer(Modifier.width(12.dp))
      Text("موجودی", Modifier.width(120.dp), style = head, color = Shop.colors.muted)
      Text("تعداد", Modifier.width(target * 2 + 44.dp), style = head, color = Shop.colors.muted, textAlign = TextAlign.Center)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().background(Shop.colors.surfaceSolid)) {
      itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
        val on = i == highlight
        val inCart = cb.qtyOf(p.id)
        Row(
          Modifier
            .fillMaxWidth()
            .heightIn(min = target + 4.dp)
            .background(if (on) Shop.colors.primaryTint else if (i % 2 == 1) Shop.colors.surface2.copy(alpha = 0.45f) else Color.Transparent)
            .semantics { selected = on }
            .clickable(onClickLabel = "افزودن به سبد") { cb.onAdd(p) }
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (inCart > 0) FontWeight.SemiBold else FontWeight.Normal, color = Shop.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, maxLines = 1)
          }
          Text(p.barcodes.firstOrNull().orEmpty(), Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, maxLines = 1)
          Text("${money(p.salePrice)} ؋", Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, textAlign = TextAlign.End, maxLines = 1)
          Spacer(Modifier.width(12.dp))
          StockLine(cb.stockOf(p.id), p.minStock, p.unit, Modifier.width(120.dp))
          Box(Modifier.width(target * 2 + 44.dp), contentAlignment = Alignment.Center) {
            QtyStepper(inCart, p.name, onMinus = { cb.onMinus(p) }, onPlus = { cb.onPlus(p) }, target = target)
          }
        }
        HorizontalDivider(color = Shop.colors.border.copy(alpha = 0.6f))
      }
    }
  }
}

/** حالتِ خالیِ شبکه/جدول، با راهنمای کارِ بعدی */
@Composable
fun PosEmpty(title: String, hint: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Icons.Filled.Inventory, contentDescription = null, tint = Shop.colors.muted2, modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(hint, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, textAlign = TextAlign.Center)
  }
}
