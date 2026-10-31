package ir.vil3ntec.tohid.ui.components.product

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.product.ProductTerms
import ir.vil3ntec.tohid.core.product.ShelfLocation
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کادرهای فرمِ کالا برای PRD-02 (نامِ پشتو و انگلیسی، مترادف، واژهٔ جست‌وجو) و PRD-26 (محلِ قفسه).
 *  متنِ خامِ کادرها این‌جاست؛ تمیز کردن و سقف‌ها کارِ هسته است (`ProductInfo.of`)، تا فرمِ گوشی و
 *  پنلِ کامپیوتر یک قاعده داشته باشند. فقط وقتی `productInfoOn()` روشن است نشان داده می‌شود.
 */
data class ProductInfoForm(
  val namePs: String = "",
  val nameEn: String = "",
  val synonyms: String = "",
  val keywords: String = "",
  val aisle: String = "",
  val shelf: String = "",
  val level: String = "",
) {
  fun toInfo(): ProductInfo = ProductInfo.of(namePs, nameEn, synonyms, keywords, aisle, shelf, level)

  companion object {
    fun of(info: ProductInfo) = ProductInfoForm(
      namePs = info.namePs,
      nameEn = info.nameEn,
      synonyms = ProductTerms.format(info.synonyms),
      keywords = ProductTerms.format(info.keywords),
      aisle = info.location.aisle,
      shelf = info.location.shelf,
      level = info.location.level,
    )
  }
}

/** «راهرو ۳ · قفسه B» — فقط بخش‌های پُر، با زبانِ صفحه */
fun shelfLabel(loc: ShelfLocation): String = loc.parts().joinToString(" · ") { (part, v) ->
  when (part) {
    ShelfLocation.Part.AISLE -> StrProduct.aisleIs(v)
    ShelfLocation.Part.SHELF -> StrProduct.shelfIs(v)
    ShelfLocation.Part.LEVEL -> StrProduct.levelIs(v)
  }
}

@Composable
fun ProductInfoFields(form: ProductInfoForm, onChange: (ProductInfoForm) -> Unit) {
  val c = Shop.colors
  Column(Modifier.fillMaxWidth()) {
    Text(StrProduct.names, style = MaterialTheme.typography.labelMedium, color = c.muted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = form.namePs,
        onValueChange = { onChange(form.copy(namePs = it.take(ProductTerms.MAX_NAME))) },
        label = { Text(StrProduct.namePs) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = form.nameEn,
        onValueChange = { onChange(form.copy(nameEn = it.take(ProductTerms.MAX_NAME))) },
        label = { Text(StrProduct.nameEn) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
    }
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
      value = form.synonyms,
      onValueChange = { onChange(form.copy(synonyms = it.take(LIST_INPUT))) },
      label = { Text(StrProduct.synonyms) },
      supportingText = { Text(StrProduct.listHint) },
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = form.keywords,
      onValueChange = { onChange(form.copy(keywords = it.take(LIST_INPUT))) },
      label = { Text(StrProduct.keywords) },
      supportingText = { Text(StrProduct.listHint) },
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(StrProduct.location, style = MaterialTheme.typography.labelMedium, color = c.muted)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      OutlinedTextField(
        value = form.aisle,
        onValueChange = { onChange(form.copy(aisle = it.take(ShelfLocation.MAX))) },
        label = { Text(StrProduct.aisle) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = form.shelf,
        onValueChange = { onChange(form.copy(shelf = it.take(ShelfLocation.MAX))) },
        label = { Text(StrProduct.shelf) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = form.level,
        onValueChange = { onChange(form.copy(level = it.take(ShelfLocation.MAX))) },
        label = { Text(StrProduct.level) },
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
    }
  }
}

/** PRD-26 — محلِ قفسهٔ کالای یک قلمِ سفارش، زیرِ نامش؛ تنظیمِ خاموش، قلمِ بی کالا یا کالای بی محل ⇒ هیچ */
@Composable
fun OrderLineShelf(d: ShopData, productId: String) {
  if (productId.isEmpty() || !productInfoOn()) return
  val loc = d.products.firstOrNull { it.id == productId }?.let { ProductInfoMap.shelf(it) }?.takeIf { !it.isEmpty } ?: return
  Text(shelfLabel(loc), style = MaterialTheme.typography.labelSmall, color = Shop.colors.primary)
}

/** بخشِ تنظیمات — خودش نمی‌خواند و نمی‌نویسد: مقدار و نوشتن از صدازننده (`ProductInfoPrefs`) */
@Composable
fun ProductInfoSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.Inventory2,
    title = StrProduct.section,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrProduct.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}

/** سقفِ نویسهٔ کادرِ فهرست — ۲۰ واژه × ۶۰ نویسه با جداکننده جا می‌شود */
private const val LIST_INPUT = ProductTerms.MAX_TERMS * (ProductTerms.MAX_TERM + 2)
