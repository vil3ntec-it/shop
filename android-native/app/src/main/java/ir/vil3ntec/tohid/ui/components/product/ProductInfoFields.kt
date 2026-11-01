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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.product.CustomFields
import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.product.ProductSku
import ir.vil3ntec.tohid.core.product.ProductTerms
import ir.vil3ntec.tohid.core.product.ShelfLocation
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کادرهای فرمِ کالا برای PRD-03 (کدِ کالا با ساختِ خودکار)، PRD-09 (خدماتی یا بی موجودی)،
 *  PRD-02 (نامِ پشتو و انگلیسی، مترادف، واژهٔ جست‌وجو)، PRD-26 (محلِ قفسه)،
 *  PRD-24 (کالاهای جایگزین: نام یا بارکد، با «،») و PRD-25 (فیلدهای سفارشی: هر خط «نام: مقدار»).
 *  جایگزین‌ها در دفتر شناسه‌اند؛ تا کاربر کادر را عوض نکرده ([alternativesText] تهی) همان شناسه‌ها
 *  دست‌نخورده می‌مانند، پس ساختنِ فرم نامِ کالاها را لازم ندارد.
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
  val alternativeIds: List<String> = emptyList(),
  /** متنِ کادرِ جایگزین‌ها پس از ویرایش؛ `null` ⇒ ویرایش نشده و [alternativeIds] همان است */
  val alternativesText: String? = null,
  val custom: String = "",
  val sku: String = "",
  val stockless: Boolean = false,
) {
  /** [catalog]/[selfId] فقط برای کادرِ ویرایش‌شدهٔ جایگزین‌ها: نام یا بارکد ⇐ شناسه (ناشناخته کنار) */
  fun toInfo(catalog: List<ProductAlternatives.Ref>, selfId: String?): ProductInfo = ProductInfo.of(
    namePs, nameEn, synonyms, keywords, aisle, shelf, level,
    alternatives = alternativesText?.let { ProductAlternatives.resolve(it, selfId, catalog).ids } ?: alternativeIds,
    custom = custom,
    sku = sku,
    stockless = stockless,
  )

  companion object {
    fun of(info: ProductInfo) = ProductInfoForm(
      namePs = info.namePs,
      nameEn = info.nameEn,
      synonyms = ProductTerms.format(info.synonyms),
      keywords = ProductTerms.format(info.keywords),
      aisle = info.location.aisle,
      shelf = info.location.shelf,
      level = info.location.level,
      alternativeIds = info.alternatives,
      custom = CustomFields.format(info.custom),
      sku = info.sku,
      stockless = info.stockless,
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
fun ProductInfoFields(form: ProductInfoForm, d: ShopData, selfId: String?, onChange: (ProductInfoForm) -> Unit) {
  val c = Shop.colors
  Column(Modifier.fillMaxWidth()) {
    //  PRD-03: کدِ کالا — ایرادِ شکل یا کدِ کالای دیگر همان‌جا (ذخیره هم همین را رد می‌کند)
    val skuProblem = ProductSku.problem(form.sku)
    val skuOwner = ProductSku.owner(form.sku, selfId, ProductInfoMap.skus(d))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(
        value = form.sku,
        onValueChange = { onChange(form.copy(sku = it.take(ProductSku.MAX + 8))) },
        label = { Text(StrProduct.sku) },
        singleLine = true,
        isError = skuProblem != null || skuOwner != null,
        supportingText = {
          when {
            skuProblem == ProductSku.Problem.BAD_CHAR -> Text(StrProduct.skuBadChar, color = c.danger)
            skuProblem == ProductSku.Problem.TOO_LONG -> Text(StrProduct.skuTooLong(ProductSku.MAX), color = c.danger)
            skuOwner != null -> Text(StrProduct.skuTaken(ProductSku.clean(form.sku), d.products.firstOrNull { it.id == skuOwner }?.name.orEmpty()), color = c.danger)
            else -> Text(StrProduct.skuHint)
          }
        },
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { onChange(form.copy(sku = ProductSku.next(ProductInfoMap.skus(d).values))) }) { Text(StrProduct.skuAuto) }
    }
    //  PRD-09: خدماتی یا بی موجودی
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(checked = form.stockless, onCheckedChange = { onChange(form.copy(stockless = it)) })
      Column(Modifier.weight(1f)) {
        Text(StrProduct.stockless, style = MaterialTheme.typography.bodyMedium)
        Text(StrProduct.stocklessHint, style = MaterialTheme.typography.labelSmall, color = c.muted)
      }
    }
    Spacer(Modifier.height(6.dp))
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
    Spacer(Modifier.height(6.dp))
    //  PRD-24: نام‌های جایگزین تا ویرایش از همان شناسه‌ها؛ ناشناخته‌ها همان‌جا گفته می‌شوند
    val altText = form.alternativesText ?: ProductTerms.format(
      form.alternativeIds.mapNotNull { id -> d.products.firstOrNull { it.id == id }?.name },
    )
    val unknown = form.alternativesText?.let { ProductAlternatives.resolve(it, selfId, ProductInfoMap.refs(d)).unknown }.orEmpty()
    OutlinedTextField(
      value = altText,
      onValueChange = { onChange(form.copy(alternativesText = it.take(LIST_INPUT))) },
      label = { Text(StrProduct.alternatives) },
      supportingText = {
        if (unknown.isEmpty()) Text(StrProduct.alternativesHint)
        else Text(StrProduct.notFound(ProductTerms.format(unknown)), color = c.danger)
      },
      modifier = Modifier.fillMaxWidth(),
    )
    //  PRD-25: هر خط «نام: مقدار»
    val badCustom = CustomFields.parse(form.custom).bad
    OutlinedTextField(
      value = form.custom,
      onValueChange = { onChange(form.copy(custom = it.take(CUSTOM_INPUT))) },
      label = { Text(StrProduct.customFields) },
      supportingText = {
        if (badCustom.isEmpty()) Text(StrProduct.customHint)
        else Text(StrProduct.customBad(badCustom.first()), color = c.danger)
      },
      singleLine = false,
      modifier = Modifier.fillMaxWidth(),
    )
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

/** سقفِ نویسهٔ کادرِ فیلدهای سفارشی — ۲۰ خطِ «نام: مقدار» */
private const val CUSTOM_INPUT = CustomFields.MAX_FIELDS * (CustomFields.MAX_KEY + CustomFields.MAX_VALUE + 3)
