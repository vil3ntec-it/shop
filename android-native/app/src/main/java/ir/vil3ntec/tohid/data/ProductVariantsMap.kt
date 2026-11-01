package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductVariants
import ir.vil3ntec.tohid.core.product.SimilarNames
import ir.vil3ntec.tohid.core.product.VariantCombo


/**
 *  PRD-11 — تنوع با ماتریس در دفتر: کالای «مدل» ویژگی‌ها را دارد (`Product.variantAxes`)، هر تنوع کالای
 *  جداست با بارکد، قیمت و موجودیِ خودش و به مدل اشاره می‌کند (`variantOf` + `variant`). منطقِ خالص در
 *  `core/product/ProductVariants`.
 *
 *  ⛔ ساختنِ تنوع فقط با ذخیرهٔ فرمِ مدل (`tohid-ui:product_info`): ترکیبِ بی تنوع یک کالای تازه می‌شود با دسته، واحد،
 *  قیمت‌ها، تأمین‌کننده، برند و برچسبِ مدل و بی بارکد؛ تنوعِ موجود و تنوعی که مقدارش از فهرست رفته دست نمی‌خورد.
 *  ترکیبی که نامِ ساخته‌اش مالِ کالای دیگری است ساخته نمی‌شود (نامِ کالا یکتاست). کالای بی این فیلدها همان JSON.
 */
object ProductVariantsMap {

  fun axesOf(p: Product): List<ProductVariants.Axis> = p.variantAxes.orEmpty().map { ProductVariants.Axis(it.name, it.values) }

  fun axesModel(a: List<ProductVariants.Axis>): List<VariantAxis>? = a.map { VariantAxis(it.name, it.values) }.ifEmpty { null }

  /** ترکیبِ یک تنوع به ترتیبِ ثبت */
  fun comboOf(p: Product): VariantCombo = p.variant.orEmpty().toList()

  /** تنوع‌های این مدل، با ترکیبِ هر کدام */
  fun children(products: List<Product>, parentId: String): List<Product> = products.filter { it.variantOf == parentId }

  /** ترکیب‌هایی که با ذخیرهٔ [parent] ساخته می‌شوند (نامشان آزاد است) */
  fun toCreate(products: List<Product>, parent: Product): List<VariantCombo> {
    val axes = axesOf(parent)
    if (axes.isEmpty()) return emptyList()
    val names = products.mapTo(HashSet()) { SimilarNames.exactKey(it.name) }
    return ProductVariants.missing(axes, children(products, parent.id).map(::comboOf))
      .filter { SimilarNames.exactKey(ProductVariants.name(parent.name, it)) !in names }
  }

  /** همان فهرستِ کالاها به‌اضافهٔ تنوع‌های تازهٔ [parent]؛ مدلِ بی ویژگی ⇒ همان فهرست */
  fun withChildren(products: List<Product>, parent: Product, newId: () -> String, now: Long): List<Product> {
    val make = toCreate(products, parent)
    if (make.isEmpty()) return products
    return products + make.map { combo ->
      Product(
        id = newId(), name = ProductVariants.name(parent.name, combo), category = parent.category, subcategory = parent.subcategory,
        unit = parent.unit, purchasePrice = parent.purchasePrice, salePrice = parent.salePrice, wholesalePrice = parent.wholesalePrice,
        supplierId = parent.supplierId, brand = parent.brand, tags = parent.tags, createdAt = now,
        variantOf = parent.id, variant = LinkedHashMap<String, String>().apply { combo.forEach { (k, v) -> put(k, v) } },
      )
    }
  }

  /** ماتریسِ تنوع‌های مدل برای فرم: سطر × ستون ⇒ کالای تنوع */
  fun matrix(products: List<Product>, axes: List<ProductVariants.Axis>, parentId: String): Pair<ProductVariants.Matrix, Map<String, Product>> {
    val kids = children(products, parentId)
    return ProductVariants.matrix(axes, kids.map { it.id to comboOf(it) }) to kids.associateBy { it.id }
  }
}
