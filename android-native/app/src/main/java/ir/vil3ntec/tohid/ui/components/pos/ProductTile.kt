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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.ProductPhoto
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.softCard
import ir.vil3ntec.tohid.ui.theme.avatarColors
import ir.vil3ntec.tohid.ui.theme.tabular
import ir.vil3ntec.tohid.ui.theme.Shape as ShopShape

/*
 *  کاتالوگِ صندوق — نسخهٔ ۲ (نقدِ صاحب محصول، ۱۴۰۵/۰۷/۲۷: «− / + هم در کاتالوگ
 *  هست هم در سبد؛ همه‌چیز دو جا»).
 *
 *  • کارت: عکس یا حرفِ اول، نام، نقطهٔ موجودی و قیمت. **زدنِ کارت = افزودن به
 *    سبد** (با «۳*» پیشوندِ کادر، سه تا). اگر در سبد است، نشانِ کوچکِ تعداد روی
 *    کارت. شمارندهٔ − / + فقط در سبد است (`OrderLines`).
 *  • نمای جدولی (اختیاری، §۱٫۵) همان قاعده: ردیف را بزنید ⇐ افزودن؛ ستونِ «در سبد».
 *  هر دو نما همان فهرست و همان برجسته (پیشنهادِ Enter) را دارند.
 */

/** آن‌چه کارت و ردیف برای یک کالا لازم دارند — از صفحه، بی محاسبهٔ دوباره */
class PosProductCallbacks(
  val stockOf: (String) -> Double,
  val qtyOf: (String) -> Double,
  val onAdd: (Product) -> Unit,
)

/** − / تعداد / + — فقط در سبد. هدفِ لمس از `PosLayout.minTarget` */
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
  //  نسخهٔ ۲: یک قرصِ نرم با دو دکمهٔ گرد، نه سه جعبهٔ خط‌دار
  Row(
    Modifier.clip(ShopShape.chip).background(Shop.colors.surface2).padding(2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StepButton(Icons.Filled.Remove, "یکی کمتر از $itemName", enabled = quantity > 0, target = target, onClick = onMinus)
    Text(
      if (unit.isNotBlank() && quantity > 0) "${qty(quantity)} $unit" else qty(quantity),
      Modifier.widthIn(min = 26.dp).padding(horizontal = 4.dp),
      style = MaterialTheme.typography.bodyMedium.tabular(),
      fontWeight = FontWeight.SemiBold,
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
    color = Shop.colors.surface,
    contentColor = if (enabled) Shop.colors.primaryDark else Shop.colors.muted2,
    shadowElevation = if (enabled) 1.dp else 0.dp,
    modifier = Modifier.size(target - 6.dp).semantics { contentDescription = label },
  ) {
    Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
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

/**
 *  حرفِ اولِ نام در دایرهٔ کوچکِ تهِ رنگِ دسته (`avatarColors`) — جای عکسِ کالای بی‌عکس (و وقتی
 *  نمایشِ عکس خاموش است). کالاهای یک دسته یک رنگ‌اند، پس با یک نگاه کنارِ هم شناخته می‌شوند.
 */
@Composable
fun ProductInitial(name: String, size: Dp, modifier: Modifier = Modifier, tintKey: String = name) {
  val (bg, fg) = avatarColors(Shop.colors, tintKey)
  Box(
    modifier.size(size).clip(CircleShape).background(bg),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      name.trim().take(1).ifEmpty { "؟" },
      fontSize = (size.value * 0.42f).sp,
      fontWeight = FontWeight.Bold,
      color = fg,
      maxLines = 1,
    )
  }
}

/** عکسِ کالا یا حرفِ اول — یک جا برای ردیفِ جدول، ردیفِ سبد و فهرستِ مدیریت */
@Composable
fun ProductThumb(productId: String, name: String, size: Dp, showPhoto: Boolean, modifier: Modifier = Modifier, tintKey: String = name) {
  if (showPhoto) ProductPhoto(productId = productId, size = size, modifier = modifier, fallback = { ProductInitial(name, size, tintKey = tintKey) })
  else ProductInitial(name, size, modifier, tintKey)
}

/**
 *  کارتِ کالا: عکس یا حرفِ اول (دایرهٔ کوچکِ رنگِ دسته — همان `ProductThumb`ِ فهرست‌ها)،
 *  نام، موجودی و قیمت؛ زدن ⇐ افزودن. اگر در سبد است نشانِ «×n» کنارِ قیمت.
 *
 *  بازبینی (۱۴۰۵/۰۷/۲۷): جای عکسِ بزرگ نیمی از کارت را می‌گرفت و برای کالای بی‌عکس
 *  فقط یک جعبهٔ خاکستری بود؛ حالا جای کارت مالِ نام، قیمت و موجودی است.
 */
@Composable
fun ProductCard(
  product: Product,
  stock: Double,
  inCart: Double,
  highlighted: Boolean,
  showPhoto: Boolean,
  onAdd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Shop.colors
  val label = buildString {
    append(product.name); append("، "); append(money(product.salePrice)); append(" افغانی")
    if (inCart > 0) { append("، در سبد: "); append(qty(inCart)) }
  }
  Column(
    modifier
      .fillMaxWidth()
      .softCard(c, ShopShape.card, fill = if (highlighted) c.primaryTint.compositeOver(c.surface) else c.surface)
      .then(
        when {
          highlighted -> Modifier.border(2.dp, c.primary, ShopShape.card)
          inCart > 0 -> Modifier.border(1.5.dp, c.primary.copy(alpha = 0.45f), ShopShape.card)
          else -> Modifier
        },
      )
      .semantics { selected = highlighted; contentDescription = label }
      .clickable(onClickLabel = "افزودن به سبد", onClick = onAdd)
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      ProductThumb(product.id, product.name, 44.dp, showPhoto, tintKey = product.category)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          product.name,
          style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text,
          minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        StockLine(stock, product.minStock, product.unit)
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "${money(product.salePrice)} ؋",
        Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium.tabular(), fontWeight = FontWeight.Bold, color = c.text, maxLines = 1,
      )
      if (inCart > 0) {
        Text(
          "×${qty(inCart)}",
          Modifier.clip(ShopShape.chip).background(c.primary).padding(horizontal = 8.dp, vertical = 2.dp),
          style = MaterialTheme.typography.labelMedium.tabular(),
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
          maxLines = 1,
        )
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
  cb: PosProductCallbacks,
  state: LazyGridState,
  minCell: Dp,
  modifier: Modifier = Modifier,
) {
  //  برجسته (پیشنهادِ Enter) دیده می‌ماند؛ فهرستِ تازه بی برجسته از سر
  LaunchedEffect(highlight, items) {
    runCatching { if (highlight in items.indices) state.animateScrollToItem(highlight) else state.scrollToItem(0) }
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = minCell),
    state = state,
    modifier = modifier,
    //  جا برای سایهٔ نرمِ کارت‌ها (بی‌اش سایهٔ ردیفِ آخر بریده می‌شود)
    contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
      ProductCard(
        product = p,
        stock = cb.stockOf(p.id),
        inCart = cb.qtyOf(p.id),
        highlighted = i == highlight,
        showPhoto = showPhotos,
        onAdd = { cb.onAdd(p) },
      )
    }
  }
}

/** نمای جدولی: ردیفِ نرم با زمینهٔ یک‌درمیان — نام، کد، قیمت، موجودی، «در سبد»؛ زدن ⇐ افزودن */
@Composable
fun ProductTable(
  items: List<Product>,
  highlight: Int,
  target: Dp,
  showPhotos: Boolean,
  cb: PosProductCallbacks,
  state: LazyListState,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(highlight, items) {
    runCatching { if (highlight in items.indices) state.animateScrollToItem(highlight) else state.scrollToItem(0) }
  }
  val c = Shop.colors
  val head = MaterialTheme.typography.labelMedium
  Column(modifier.padding(4.dp).softCard(c, ShopShape.card)) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("کالا", Modifier.weight(1f), style = head, color = c.muted)
      Text("کد", Modifier.width(130.dp), style = head, color = c.muted)
      Text("قیمت", Modifier.width(110.dp), style = head, color = c.muted, textAlign = TextAlign.End)
      Spacer(Modifier.width(16.dp))
      Text("موجودی", Modifier.width(130.dp), style = head, color = c.muted)
      Text("در سبد", Modifier.width(64.dp), style = head, color = c.muted, textAlign = TextAlign.Center)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
      itemsIndexed(items, key = { _, p -> p.id }) { i, p ->
        val on = i == highlight
        val inCart = cb.qtyOf(p.id)
        Row(
          Modifier
            .fillMaxWidth()
            .heightIn(min = target + 12.dp)
            .padding(horizontal = 6.dp)
            .clip(ShopShape.button)
            .background(if (on) c.primaryTint else if (i % 2 == 1) c.surface2 else Color.Transparent)
            .semantics { selected = on }
            .clickable(onClickLabel = "افزودن به سبد") { cb.onAdd(p) }
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ProductThumb(p.id, p.name, 32.dp, showPhotos, tintKey = p.category)
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (p.category.isNotBlank()) Text(p.category, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
          }
          Text(p.barcodes.firstOrNull().orEmpty(), Modifier.width(130.dp), style = MaterialTheme.typography.bodySmall.tabular(), color = c.muted, maxLines = 1)
          Text("${money(p.salePrice)} ؋", Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium.tabular(), fontWeight = FontWeight.SemiBold, color = c.text, textAlign = TextAlign.End, maxLines = 1)
          Spacer(Modifier.width(16.dp))
          StockLine(cb.stockOf(p.id), p.minStock, p.unit, Modifier.width(130.dp))
          Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            if (inCart > 0) Text(
              "×${qty(inCart)}",
              Modifier.clip(ShopShape.chip).background(c.primaryTint).padding(horizontal = 8.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelMedium.tabular(), fontWeight = FontWeight.Bold, color = c.primaryDark,
            )
          }
        }
      }
    }
  }
}

/** حالتِ خالیِ شبکه/جدول، با راهنمای کارِ بعدی */
@Composable
fun PosEmpty(title: String, hint: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(Shop.colors.primaryTint), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.Inventory, contentDescription = null, tint = Shop.colors.primaryDark, modifier = Modifier.size(30.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text, textAlign = TextAlign.Center)
    Spacer(Modifier.height(4.dp))
    Text(hint, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, textAlign = TextAlign.Center)
  }
}

/** پهنای کمینهٔ کارت: پنجرهٔ پهن ⇒ کارتِ درشت‌تر تا کاتالوگ پر شود نه خالی */
fun posCardMin(catalogWidth: Dp, touch: Boolean): Dp = when {
  catalogWidth >= 1200.dp -> 200.dp
  touch -> 180.dp
  else -> 156.dp
}
