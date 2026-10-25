package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.PermCore

/**
 *  سدِ نقش پیش از نوشتنِ دفتر — STF-01، بستهٔ A16.
 *
 *  صندوق‌دار خرید نمی‌نویسد، انباردار فروش نمی‌زند و حسابدار هیچ نمی‌نویسد؛
 *  سرور opِ چنین کاری را در هر سه راهِ همگام‌سازی رد می‌کند (`canWrite`ِ
 *  هستهٔ مشترک). اگر برنامه آن را روی دستگاه می‌نشاند، دفترِ این دستگاه با
 *  دفترِ دکان برای همیشه فرق می‌کرد. پس `ShopStore.save` همین‌جا می‌پرسد و
 *  نوشتنِ بیرون از نقش را پیش از دیسک و پیش از ساختنِ op نگه می‌دارد.
 *
 *  ⛔ صاحب، مدیر و شاگرد «*»اند: برایشان حتی تفاضل حساب نمی‌شود — رفتار و
 *  هزینه همان امروز. نقشی که این نسخه نمی‌شناسد هم بسته نمی‌شود (سرور می‌سنجد).
 */
object RoleGate {

  /** بخش‌هایی از دفتر که میانِ دو حالت عوض شده‌اند، با نامِ جدولِ همگام‌سازی */
  fun changedTables(cur: ShopData, next: ShopData): List<String> = buildList {
    if (cur.debtors != next.debtors) add("debtors")
    if (cur.transactions != next.transactions) add("transactions")
    if (cur.expenses != next.expenses) add("expenses")
    if (cur.expenseCategories != next.expenseCategories) add("expenseCategories")
    if (cur.products != next.products) add("products")
    if (cur.productCategories != next.productCategories) add("productCategories")
    if (cur.productUnits != next.productUnits) add("productUnits")
    if (cur.warehouseEntries != next.warehouseEntries) add("warehouseEntries")
    if (cur.sales != next.sales) add("sales")
    if (cur.saleItems != next.saleItems) add("saleItems")
    if (cur.saleReturns != next.saleReturns) add("returns")
    if (cur.suppliers != next.suppliers) add("suppliers")
    if (cur.purchases != next.purchases) add("purchases")
    if (cur.supplierPayments != next.supplierPayments) add("supplierPayments")
    if (cur.stockMovements != next.stockMovements) add("stockMovements")
    if (cur.priceHistory != next.priceHistory) add("priceHistory")
    if (cur.auditLog != next.auditLog) add("auditLog")
  }

  /** نخستین جدولی که این نقش نمی‌نویسد و در این نوشتن عوض شده — یا null */
  fun blocked(role: String, cur: ShopData, next: ShopData): String? {
    if (role !in PermCore.ROLES || PermCore.writable(role) == PermCore.ALL_TABLES) return null
    return changedTables(cur, next).firstOrNull { table ->
      !PermCore.canWrite(role, table) || (PermCore.rowRule(role, table) != null && !rowsAllowed(role, table, cur, next))
    }
  }

  /**
   *  قاعدهٔ ردیف (همان `PermCore.checkRows`ِ سرور): فقط ردیفِ تازه، و همهٔ ردیف‌های تازه قاعده
   *  را بگذرانند؛ ویرایش یا حذفِ ردیفِ موجود نه. صندوق‌دار: رسیدِ انبار فقط اصلاحِ موجودیِ قلمِ
   *  برگشتی که در همین ذخیره ساخته شده (هر قلم یک اصلاح)، حرکتِ انبار فقط حرکتِ فاکتور و برگشت.
   *  تنگ‌تر از سرور است، نه گشادتر: سرور برگشتِ ثبت‌شده با همین دستگاه را هم «خودی» می‌داند.
   */
  private fun rowsAllowed(role: String, table: String, cur: ShopData, next: ShopData): Boolean {
    val rows = when (table) {
      "warehouseEntries" -> added(cur.warehouseEntries, next.warehouseEntries) { it.id }
        ?.map { PermCore.RowIn(table, entryView(it)) }
      "stockMovements" -> added(cur.stockMovements, next.stockMovements) { it.id }
        ?.map { PermCore.RowIn(table, moveView(it)) }
      else -> null
    } ?: return false
    return rows.isEmpty() || PermCore.checkRows(role, rows, refsOf(cur, next)).all { it.ok }
  }

  /** ردیف‌های تازه؛ null اگر ردیفِ موجودی عوض یا حذف شده یا شناسه‌ای دو بار آمده است */
  private fun <T> added(before: List<T>, after: List<T>, id: (T) -> String): List<T>? {
    val old = before.associateBy(id)
    val now = after.associateBy(id)
    if (now.size != after.size || old.any { (k, v) -> now[k] != v }) return null
    return after.filter { id(it) !in old }
  }

  private fun entryView(e: WarehouseEntry): Map<String, Any?> = mapOf(
    "isAdjustment" to e.isAdjustment, "price" to e.price, "cartons" to e.cartons, "perCarton" to e.perCarton,
    "adjKind" to e.adjKind, "refId" to e.refId, "productId" to e.productId, "units" to e.units,
  )

  private fun moveView(m: StockMovement): Map<String, Any?> =
    mapOf("type" to m.type, "qty" to m.qty, "refId" to m.refId, "productId" to m.productId)

  /** قلم‌های برگشت (خودی = ساختهٔ همین ذخیره)، فاکتورها، و اصلاح‌های ثبت‌شدهٔ برگشت */
  private fun refsOf(cur: ShopData, next: ShopData): PermCore.RowRefs {
    val before = cur.saleReturns.mapTo(HashSet()) { it.id }
    return PermCore.RowRefs(
      returns = next.saleReturns.map {
        PermCore.ReturnLine(it.id, it.docId, it.productId, it.quantity, own = it.id !in before)
      },
      sales = next.sales.mapTo(HashSet()) { it.id },
      taken = cur.warehouseEntries
        .filter { it.adjKind in PermCore.RETURN_ADJ_KINDS && !it.refId.isNullOrEmpty() }
        .map { PermCore.Taken(it.refId!!, it.productId, it.units) },
    )
  }

  /** نامِ فارسیِ بخشِ دفتر برای پیام */
  fun tableTitle(table: String): String = when (table) {
    "debtors" -> "قرض‌داران"
    "transactions" -> "قرض و پرداخت"
    "expenses", "expenseCategories" -> "مصارف"
    "products", "productCategories", "productUnits" -> "کالاها"
    "priceHistory" -> "قیمتِ کالا"
    "warehouseEntries" -> "رسیدِ انبار"
    "sales", "saleItems" -> "فروش"
    "returns" -> "برگشتِ کالا"
    "suppliers" -> "تأمین‌کننده‌ها"
    "purchases" -> "خرید"
    "supplierPayments" -> "پرداخت به تأمین‌کننده"
    "stockMovements" -> "حرکتِ انبار"
    "auditLog" -> "سابقهٔ عملیات"
    else -> table
  }
}
